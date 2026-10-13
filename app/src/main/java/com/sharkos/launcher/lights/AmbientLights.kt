package com.sharkos.launcher.lights

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Aftermarket ambient LED kits of the "ELK" family (Magic CarLight / Lotus Lamp X), over Bluetooth LE.
 * The control box advertises as MELK-…, MHRS-… or HRS-MBLEM…; service FFF0, commands written to FFF3,
 * no pairing or handshake. Commands as the Magic CarLight app builds them:
 *
 *   standard (E2):  8E len cmd params… zones      zones 0x70 = console + doors + rear
 *   alternate (E1): 7E len cmd p p p p 00 EF      fixed 9 bytes, padded with FF
 *
 * Experimental and self-contained: everything lives here and in Settings → Ambient lighting, so it can
 * be removed in one go. Connects only when used and lets go after [IDLE_MS], so the phone app still
 * works (the box takes one controller at a time).
 */
@SuppressLint("MissingPermission")   // every call is behind hasPermission()
object AmbientLights {
    enum class Link { IDLE, SEARCHING, CONNECTING, CONNECTED }
    /** Command format. MHRS-QA / MELK-QA boxes drive two light sets ("Colorful" and "Symphony" in the
     *  phone app), one per format, so [BOTH] sends each command in both. */
    enum class Protocol(val label: String) { BOTH("Both"), E2("Standard"), E1("Alternate") }

    data class Found(val name: String, val address: String, val rssi: Int, val lights: Boolean)

    private val SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val WRITE = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    private val PREFIXES = listOf("MELK", "MHRS", "HRS-MBLEM", "ELK-")
    private const val ZONES_ALL = 0x70
    private const val SCAN_MS = 16_000L
    private const val IDLE_MS = 120_000L
    private const val SEND_GAP_MS = 90L

    private val _link = MutableStateFlow(Link.IDLE)
    val link: StateFlow<Link> = _link.asStateFlow()
    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found.asStateFlow()
    private val _connectedName = MutableStateFlow<String?>(null)
    val connectedName: StateFlow<String?> = _connectedName.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    /** Newest first; for the diagnostics panel. */
    val log: StateFlow<List<String>> = _log.asStateFlow()
    private val _protocol = MutableStateFlow(Protocol.BOTH)
    val protocol: StateFlow<Protocol> = _protocol.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    /** The newest command, as one frame per format; older unsent commands are dropped. */
    private var pending: ArrayDeque<ByteArray>? = null
    private var rawSeen = 0
    private var legacy = false
    private var writing = false
    private var scanning = false
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sharkos_lights", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        _protocol.value = prefs(ctx).getString("protocol", null)?.let { p -> Protocol.entries.firstOrNull { it.name == p } } ?: Protocol.BOTH
    }

    fun savedName(ctx: Context): String? = prefs(ctx).getString("name", null)

    // ---- What's needed, for the Settings rows ----

    fun permissionsNeeded(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermission(ctx: Context): Boolean =
        permissionsNeeded().all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun adapter(ctx: Context): BluetoothAdapter? =
        runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()

    /** What Android's app-ops say about location for SharkOS (a permission can be granted yet the op denied). */
    private fun locationOp(ctx: Context): String = runCatching {
        val ops = ctx.getSystemService(android.app.AppOpsManager::class.java)
        fun op(name: String) = when (ops.unsafeCheckOpNoThrow(name, android.os.Process.myUid(), ctx.packageName)) {
            android.app.AppOpsManager.MODE_ALLOWED -> "allow"
            android.app.AppOpsManager.MODE_IGNORED -> "IGNORED"
            android.app.AppOpsManager.MODE_ERRORED -> "DENIED"
            android.app.AppOpsManager.MODE_FOREGROUND -> "foreground"
            else -> "default"
        }
        "fine ${op(android.app.AppOpsManager.OPSTR_FINE_LOCATION)}/coarse ${op(android.app.AppOpsManager.OPSTR_COARSE_LOCATION)}"
    }.getOrDefault("?")

    fun bluetoothPresent(ctx: Context) = adapter(ctx) != null
    fun bluetoothOn(ctx: Context) = runCatching { adapter(ctx)?.isEnabled == true }.getOrDefault(false)
    fun locationOn(ctx: Context) = runCatching { ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }.getOrDefault(false)

    // ---- Finding and connecting ----

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) =
            seen(result.device, result.rssi, result.scanRecord?.deviceName, hasLightsService(result))

        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }

        override fun onScanFailed(errorCode: Int) {
            log("Search failed (error $errorCode), trying the older search")
            main.post { startLegacy() }
        }
    }

    /** A second, filtered search alongside the open one: some car stacks only deliver results that match a filter. */
    private val filteredCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) =
            seen(result.device, result.rssi, result.scanRecord?.deviceName, hasLightsService(result))
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(errorCode: Int) { log("Filtered search failed (error $errorCode)") }
    }
    private var filtered = false

    private fun filters(): List<android.bluetooth.le.ScanFilter> {
        val names = listOf("MHRS-QA21P", "MHRS-QA21P A6", "MELK-QA21P", "ELK-BLEDOM") + listOfNotNull(app?.let { savedName(it) })
        return names.distinct().map { android.bluetooth.le.ScanFilter.Builder().setDeviceName(it).build() } +
            android.bluetooth.le.ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(SERVICE)).build()
    }

    /** The pre-Android-5 search: some head units return nothing to the modern one (Sharkware does the same). */
    private val legacyCb = BluetoothAdapter.LeScanCallback { device, rssi, _ -> main.post { seen(device, rssi, null) } }

    private fun hasLightsService(r: ScanResult) = r.scanRecord?.serviceUuids?.any { it.uuid == SERVICE } == true

    private fun seen(device: BluetoothDevice, rssi: Int, advertisedName: String?, lightsService: Boolean = false) {
        rawSeen++
        // A box that advertises the lights' service counts even without a name in its advert
        val name = advertisedName ?: runCatching { device.name }.getOrNull() ?: if (lightsService) "Unnamed lights (FFF0)" else return
        val f = Found(name, device.address, rssi, lightsService || PREFIXES.any { name.startsWith(it, ignoreCase = true) })
        val list = _found.value.filterNot { it.address == f.address } + f
        _found.value = list.sortedWith(compareByDescending<Found> { it.lights }.thenByDescending { it.rssi }).take(16)
    }

    /** The third way: Bluetooth "discovery", which the head unit's own Settings search uses. */
    private var discovering = false
    private val discoveryRx = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context, i: android.content.Intent) {
            if (i.action != BluetoothDevice.ACTION_FOUND) return
            val d: BluetoothDevice = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            seen(d, i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt(), i.getStringExtra(BluetoothDevice.EXTRA_NAME))
        }
    }

    private fun startDiscovery() {
        val c = app ?: return
        if (!scanning || discovering) return
        runCatching { c.registerReceiver(discoveryRx, android.content.IntentFilter(BluetoothDevice.ACTION_FOUND)) }
        discovering = runCatching { adapter(c)?.startDiscovery() == true }.getOrDefault(false)
        log(if (discovering) "Still nothing: trying Bluetooth discovery as well" else "Bluetooth discovery couldn't start")
    }

    private fun stopDiscovery() {
        val c = app ?: return
        if (!discovering) return
        runCatching { adapter(c)?.cancelDiscovery() }
        runCatching { c.unregisterReceiver(discoveryRx) }
        discovering = false
    }

    private fun startLegacy() {
        val c = app ?: return
        if (!scanning || legacy) return
        runCatching { adapter(c)?.bluetoothLeScanner?.stopScan(scanCb) }
        legacy = runCatching { adapter(c)?.startLeScan(legacyCb) == true }.getOrDefault(false)
        log(if (legacy) "Nothing yet: switched to the older search" else "The older search couldn't start either")
    }

    /** Look for nearby lights for [SCAN_MS]; with [connectWhenFound] connect to the first one. */
    fun search(ctx: Context, connectWhenFound: Boolean = true) {
        init(ctx)
        if (!ready(ctx)) return
        if (scanning) return
        val scanner = adapter(ctx)?.bluetoothLeScanner ?: return log("No Bluetooth LE scanner on this unit")
        _found.value = emptyList()
        scanning = true
        legacy = false
        rawSeen = 0
        _link.value = Link.SEARCHING
        val a = adapter(ctx)
        log("Searching… (Bluetooth ${if (bluetoothOn(ctx)) "on" else "off"}, location ${if (locationOn(ctx)) "on" else "OFF"}, " +
            "permission ${if (hasPermission(ctx)) "yes" else "NO"}, app-op ${locationOp(ctx)}, target ${ctx.applicationInfo.targetSdkVersion})")
        val ok = runCatching {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
                .apply { if (runCatching { a?.isLeExtendedAdvertisingSupported == true }.getOrDefault(false)) setLegacy(false) }
                .build()
            scanner.startScan(null, settings, scanCb)
            filtered = runCatching { scanner.startScan(filters(), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), filteredCb); true }
                .onFailure { log("Filtered search refused: ${it.message}") }.getOrDefault(false)
        }
        if (ok.isFailure) { log("Modern search refused: ${ok.exceptionOrNull()?.message}"); startLegacy() }
        // Like Sharkware: if the modern search has heard nothing after 2.5 s, use the older one
        main.postDelayed({ if (scanning && rawSeen == 0) startLegacy() }, 2500)
        main.postDelayed({ if (scanning && rawSeen == 0) startDiscovery() }, 5500)
        val started = System.currentTimeMillis()
        fun check() {
            if (!scanning) return
            val hit = _found.value.firstOrNull { it.lights }
            when {
                connectWhenFound && hit != null -> { stopScan(); log("Found ${hit.name}"); connect(ctx, hit.address, hit.name) }
                System.currentTimeMillis() - started > SCAN_MS -> {
                    val named = _found.value.size
                    stopScan()
                    log(when {
                        _found.value.any { it.lights } -> "Search finished"
                        rawSeen == 0 -> "Heard nothing at all: the car returned no Bluetooth results${if (!locationOn(ctx)) " (location is off)" else ""}"
                        else -> "No lights found: heard $rawSeen adverts, $named named devices"
                    })
                }
                else -> main.postDelayed(::check, 500)
            }
        }
        main.postDelayed(::check, 500)
    }

    fun stopScan() {
        val c = app ?: return
        if (scanning) runCatching { adapter(c)?.bluetoothLeScanner?.stopScan(scanCb) }
        if (filtered) runCatching { adapter(c)?.bluetoothLeScanner?.stopScan(filteredCb) }
        filtered = false
        if (legacy) runCatching { adapter(c)?.stopLeScan(legacyCb) }
        stopDiscovery()
        legacy = false
        scanning = false
        if (_link.value == Link.SEARCHING) _link.value = Link.IDLE
    }

    private fun ready(ctx: Context): Boolean = when {
        !bluetoothPresent(ctx) -> { log("This unit has no Bluetooth adapter SharkOS can use"); false }
        !hasPermission(ctx) -> { log("Needs the Location permission first"); false }
        !bluetoothOn(ctx) -> { log("Bluetooth is off"); false }
        else -> true
    }

    /** Connect to the remembered lights, or search for some. */
    fun connectSaved(ctx: Context) {
        init(ctx)
        if (_link.value == Link.CONNECTED || _link.value == Link.CONNECTING) return
        val addr = prefs(ctx).getString("address", null)
        if (addr == null) search(ctx) else if (ready(ctx)) connect(ctx, addr, savedName(ctx) ?: addr)
    }

    fun connect(ctx: Context, address: String, name: String) {
        init(ctx)
        if (!ready(ctx)) return
        stopScan()
        disconnect(quiet = true)
        val device: BluetoothDevice = runCatching { adapter(ctx)!!.getRemoteDevice(address) }.getOrNull() ?: return log("Bad address $address")
        _link.value = Link.CONNECTING
        log("Connecting to $name ($address)…")
        prime(ctx)
        prefs(ctx).edit().putString("address", address).putString("name", name).apply()
        if (name.contains("-QA", ignoreCase = true) && _protocol.value != Protocol.BOTH) setProtocol(ctx, Protocol.BOTH)
        gatt = runCatching { device.connectGatt(ctx.applicationContext, false, gattCb, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { log("Connect refused: ${it.message}"); _link.value = Link.IDLE }.getOrNull()
        main.postDelayed({ if (_link.value == Link.CONNECTING) { log("No answer from the lights (is the phone app connected?)"); disconnect(quiet = true) } }, 15_000)
    }

    /** Sharkware's trick: a search running in the background lets the car connect to a box it hasn't "seen" yet. */
    private val primeCb = object : ScanCallback() {}
    private var priming = false

    private fun prime(ctx: Context) {
        if (priming) return
        priming = runCatching {
            adapter(ctx)?.bluetoothLeScanner?.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), primeCb)
            true
        }.getOrDefault(false)
        main.postDelayed({ unprime() }, 20_000)
    }

    private fun unprime() {
        if (!priming) return
        runCatching { app?.let { adapter(it)?.bluetoothLeScanner?.stopScan(primeCb) } }
        priming = false
    }

    private val gattCb = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    log("Connected, reading services…")
                    g.discoverServices()
                } else {
                    log("Disconnected (status $status)")
                    if (g == gatt) { runCatching { g.close() }; gatt = null; writer = null; _link.value = Link.IDLE; _connectedName.value = null }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                val ch = g.getService(SERVICE)?.getCharacteristic(WRITE)
                if (ch == null) {
                    val ids = g.services.joinToString { it.uuid.toString().substring(4, 8) }
                    log("Lights' control channel (FFF0/FFF3) not found. Services: $ids")
                    disconnect(quiet = true)
                    return@post
                }
                writer = ch
                unprime()
                _link.value = Link.CONNECTED
                _connectedName.value = app?.let { savedName(it) } ?: g.device.address
                log("Ready: ${_connectedName.value}")
                pending?.let { flush() }
                bumpIdle()
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.postDelayed({ writing = false; if (status != BluetoothGatt.GATT_SUCCESS) log("Write failed (status $status)"); flush() }, SEND_GAP_MS)
        }
    }

    fun disconnect(quiet: Boolean = false) {
        main.removeCallbacks(idle)
        val g = gatt ?: return
        if (!quiet) log("Released the lights")
        runCatching { g.disconnect(); g.close() }
        gatt = null; writer = null; writing = false; pending = null
        _link.value = Link.IDLE
        _connectedName.value = null
    }

    /** Connect straight to a box address typed in Diagnostics (AA:BB:CC:DD:EE:FF). */
    fun connectAddress(ctx: Context, raw: String) {
        init(ctx)
        val addr = raw.trim().uppercase().replace('-', ':')
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) return log("\"$raw\" isn't a Bluetooth address (like BE:58:12:34:56:78)")
        connect(ctx, addr, "Lights ($addr)")
    }

    fun forget(ctx: Context) {
        disconnect()
        prefs(ctx).edit().remove("address").remove("name").apply()
        _found.value = emptyList()
        log("Forgot the saved lights")
    }

    private val idle = Runnable { if (_link.value == Link.CONNECTED) { log("Idle: letting go so the phone app can connect"); disconnect(quiet = true) } }
    private fun bumpIdle() { main.removeCallbacks(idle); main.postDelayed(idle, IDLE_MS) }

    // ---- Commands ----

    fun setProtocol(ctx: Context, p: Protocol) {
        _protocol.value = p
        prefs(ctx).edit().putString("protocol", p.name).apply()
        log("Using the ${p.label.lowercase()} command format")
    }

    fun power(on: Boolean) = command(0x04, if (on) 1 else 0, 0)

    /** 1–100. */
    fun brightness(percent: Int) = command(0x01, percent.coerceIn(1, 100))

    fun color(r: Int, g: Int, b: Int) = command(0x05, 3, r, g, b, 0x10)

    private fun command(cmd: Int, vararg p: Int) = send(
        when (_protocol.value) {
            Protocol.E2 -> listOf(e2(cmd, *p))
            Protocol.E1 -> listOf(e1(cmd, *p))
            Protocol.BOTH -> listOf(e2(cmd, *p), e1(cmd, *p))
        },
    )

    /** 8E, length, command, parameters, zone byte. Length counts command + parameters + zone. */
    private fun e2(cmd: Int, vararg p: Int): ByteArray =
        (listOf(0x8E, p.size + 2, cmd) + p.toList() + ZONES_ALL).map { it.toByte() }.toByteArray()

    /** 7E, length, command, four parameter bytes (FF padded), 00, EF; colour's fifth byte lands on the 00. */
    private fun e1(cmd: Int, vararg p: Int): ByteArray {
        val out = intArrayOf(0x7E, if (cmd == 0x01) 4 else 7, cmd, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)
        p.forEachIndexed { i, v -> if (3 + i < 8) out[3 + i] = v }
        return out.map { it.toByte() }.toByteArray()
    }

    /** Only the newest command waits: dragging a slider sends a steady stream, never a backlog. */
    private fun send(frames: List<ByteArray>) {
        val c = app ?: return
        pending = ArrayDeque(frames)
        if (_link.value != Link.CONNECTED) { connectSaved(c); return }
        flush()
        bumpIdle()
    }

    private fun flush() {
        val g = gatt ?: return
        val ch = writer ?: return
        if (writing) return
        val queue = pending ?: return
        val bytes = queue.removeFirstOrNull() ?: run { pending = null; return }
        if (queue.isEmpty()) pending = null
        writing = true
        ch.writeType = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ch.value = bytes
        val ok = runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)
        log("→ " + bytes.joinToString(" ") { "%02X".format(it) } + if (ok) "" else "  (not sent)")
        if (!ok) writing = false
        // Write-without-response may not call back on every unit: don't wait for it forever
        main.postDelayed({ if (writing) { writing = false; flush() } }, 300)
    }

    private fun log(line: String) {
        _log.value = (listOf("${clock.format(Date())}  $line") + _log.value).take(40)
    }
}
