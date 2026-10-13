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
    enum class Protocol(val label: String) { E2("Standard"), E1("Alternate") }

    data class Found(val name: String, val address: String, val rssi: Int, val lights: Boolean)

    private val SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    private val WRITE = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
    private val PREFIXES = listOf("MELK", "MHRS", "HRS-MBLEM", "ELK-")
    private const val ZONES_ALL = 0x70
    private const val SCAN_MS = 12_000L
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
    private val _protocol = MutableStateFlow(Protocol.E2)
    val protocol: StateFlow<Protocol> = _protocol.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var pending: ByteArray? = null
    private var writing = false
    private var scanning = false
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("sharkos_lights", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        _protocol.value = prefs(ctx).getString("protocol", null)?.let { p -> Protocol.entries.firstOrNull { it.name == p } } ?: Protocol.E2
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

    fun bluetoothPresent(ctx: Context) = adapter(ctx) != null
    fun bluetoothOn(ctx: Context) = runCatching { adapter(ctx)?.isEnabled == true }.getOrDefault(false)
    fun locationOn(ctx: Context) = runCatching { ctx.getSystemService(LocationManager::class.java)?.isLocationEnabled == true }.getOrDefault(false)

    // ---- Finding and connecting ----

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.scanRecord?.deviceName ?: runCatching { result.device.name }.getOrNull() ?: return
            val f = Found(name, result.device.address, result.rssi, PREFIXES.any { name.startsWith(it, ignoreCase = true) })
            val list = _found.value.filterNot { it.address == f.address } + f
            _found.value = list.sortedWith(compareByDescending<Found> { it.lights }.thenByDescending { it.rssi }).take(16)
        }

        override fun onScanFailed(errorCode: Int) { log("Search failed (error $errorCode)"); stopScan() }
    }

    /** Look for nearby lights for [SCAN_MS]; with [connectWhenFound] connect to the first one. */
    fun search(ctx: Context, connectWhenFound: Boolean = true) {
        init(ctx)
        if (!ready(ctx)) return
        if (scanning) return
        val scanner = adapter(ctx)?.bluetoothLeScanner ?: return log("No Bluetooth LE scanner on this unit")
        _found.value = emptyList()
        scanning = true
        _link.value = Link.SEARCHING
        log("Searching for lights…")
        val ok = runCatching {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        }
        if (ok.isFailure) { log("Search refused: ${ok.exceptionOrNull()?.message}"); scanning = false; _link.value = Link.IDLE; return }
        val started = System.currentTimeMillis()
        fun check() {
            if (!scanning) return
            val hit = _found.value.firstOrNull { it.lights }
            when {
                connectWhenFound && hit != null -> { stopScan(); log("Found ${hit.name}"); connect(ctx, hit.address, hit.name) }
                System.currentTimeMillis() - started > SCAN_MS -> {
                    stopScan()
                    log(if (_found.value.any { it.lights }) "Search finished" else "No lights found: ${_found.value.size} other devices nearby")
                }
                else -> main.postDelayed(::check, 500)
            }
        }
        main.postDelayed(::check, 500)
    }

    fun stopScan() {
        val c = app ?: return
        if (scanning) runCatching { adapter(c)?.bluetoothLeScanner?.stopScan(scanCb) }
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
        prefs(ctx).edit().putString("address", address).putString("name", name).apply()
        gatt = runCatching { device.connectGatt(ctx.applicationContext, false, gattCb, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { log("Connect refused: ${it.message}"); _link.value = Link.IDLE }.getOrNull()
        main.postDelayed({ if (_link.value == Link.CONNECTING) { log("No answer from the lights (is the phone app connected?)"); disconnect(quiet = true) } }, 15_000)
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

    fun power(on: Boolean) = send(if (_protocol.value == Protocol.E2) e2(0x04, if (on) 1 else 0, 0) else e1(0x04, if (on) 1 else 0, 0))

    /** 1–100. */
    fun brightness(percent: Int) {
        val v = percent.coerceIn(1, 100)
        send(if (_protocol.value == Protocol.E2) e2(0x01, v) else e1(0x01, v))
    }

    fun color(r: Int, g: Int, b: Int) =
        send(if (_protocol.value == Protocol.E2) e2(0x05, 3, r, g, b, 0x10) else e1(0x05, 3, r, g, b, 0x10))

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
    private fun send(bytes: ByteArray) {
        val c = app ?: return
        pending = bytes
        if (_link.value != Link.CONNECTED) { connectSaved(c); return }
        flush()
        bumpIdle()
    }

    private fun flush() {
        val g = gatt ?: return
        val ch = writer ?: return
        if (writing) return
        val bytes = pending ?: return
        pending = null
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
