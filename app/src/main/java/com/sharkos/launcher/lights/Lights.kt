package com.sharkos.launcher.lights

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The aftermarket ambient lighting (an "ELK" two-in-one car kit, e.g. MHRS-QA21P), as SharkOS sees it:
 * two zones, each with on/off, brightness and colour. [LightsService] holds the Bluetooth connection.
 *
 * The box drives two light sets, which the phone apps call "Colorful" and "Symphony", and each takes its
 * own command format (from the Magic CarLight / Lotus Lamp X apps):
 *
 *   Colorful (E2):  8E len cmd params… 70        70 = all of its sub-groups (console, doors, rear)
 *   Symphony (E1):  7E len cmd p p p p 00 EF     fixed 9 bytes, FF padded
 *
 * Commands: 01 brightness (1–100), 04 power (1 on / 0 off), 05 colour (03 R G B 10).
 */
object Lights {
    enum class Link { OFF, CONNECTING, CONNECTED }

    /** The car's two zones. [symphonyByDefault] is which light set each zone is unless swapped. */
    enum class Zone(val label: String, val symphonyByDefault: Boolean) {
        TRIMS("Door trims", true),
        POCKETS("Pockets & footwells", false),
    }

    /** [hue] 0–1 around the colour wheel, or [WHITE]. */
    data class ZoneState(val on: Boolean = true, val brightness: Int = 100, val hue: Float = 0.6f) {
        val white: Boolean get() = hue < 0f
    }

    const val WHITE = -1f

    private val _link = MutableStateFlow(Link.OFF)
    val link: StateFlow<Link> = _link.asStateFlow()

    private val _zones = MutableStateFlow(Zone.entries.associateWith { ZoneState() })
    /** What SharkOS last set for each zone (the box doesn't report back), restored at start. */
    val zones: StateFlow<Map<Zone, ZoneState>> = _zones.asStateFlow()

    private val _linked = MutableStateFlow(true)
    /** One set of controls for both zones. */
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    private var app: Context? = null
    private const val TAG = "SharkOS/Lights"
    private fun prefs(c: Context) = c.getSharedPreferences("sharkos_lights", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        val p = prefs(c)
        _linked.value = p.getBoolean("linked", true)
        _zones.value = Zone.entries.associateWith { z ->
            ZoneState(p.getBoolean("${z.name}_on", true), p.getInt("${z.name}_bright", 100), p.getFloat("${z.name}_hue", 0.6f))
        }
    }

    // ---- Setup ----

    fun address(ctx: Context): String? = runCatching { prefs(ctx).getString("address", null) }.getOrNull() ?: previewAddress

    /** Screenshot tests only: the layout renderer has no working preferences. */
    internal var previewAddress: String? = null
    internal fun preview(linked: Boolean) { _linked.value = linked; _link.value = Link.CONNECTED }

    fun setAddress(ctx: Context, raw: String): Boolean {
        val addr = raw.trim().uppercase().replace('-', ':')
        if (!BluetoothAdapter.checkBluetoothAddress(addr)) return false
        prefs(ctx).edit().putString("address", addr).apply()
        connect(ctx)
        return true
    }

    fun autoConnect(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAutoConnect(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto", on).apply()

    /** Which zone is which light set, if the default guess is the wrong way round. */
    fun swapped(ctx: Context) = prefs(ctx).getBoolean("swapped", false)
    fun setSwapped(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("swapped", on).apply()

    fun setLinked(ctx: Context, on: Boolean) {
        _linked.value = on
        prefs(ctx).edit().putBoolean("linked", on).apply()
    }

    fun permissionsNeeded(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermission(ctx: Context) = permissionsNeeded().all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun bluetoothOn(ctx: Context) = runCatching { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true }.getOrDefault(false)

    // ---- Connection ----

    /** At start-up and whenever SharkOS opens: connect if set to, and not already. */
    fun start(ctx: Context) {
        init(ctx)
        AutoLights.start(ctx)
        NightLights.start(ctx)
        if (autoConnect(ctx) && _link.value == Link.OFF) connect(ctx)
    }

    fun connect(ctx: Context) {
        init(ctx)
        val addr = address(ctx) ?: return
        service(ctx, LightsService.ACTION_CONNECT) { putExtra(LightsService.EXTRA_ADDRESS, addr) }
    }

    fun disconnect(ctx: Context) = service(ctx, LightsService.ACTION_RELEASE)

    /** From [LightsService]. */
    internal fun onState(state: String) {
        _link.value = when (state) {
            "READY" -> Link.CONNECTED
            "CONNECTING", "WAITING" -> Link.CONNECTING
            else -> Link.OFF
        }
    }

    // ---- Controls: zone null = both ----

    /**
     * The box has one power switch for both light sets, so a single zone is turned "off" by setting it
     * to black and "on" by putting its colour and brightness back. Only with every zone off does the
     * box itself power down (and it powers up again, restoring each zone, when one comes back on).
     */
    fun setOn(ctx: Context, zone: Zone?, on: Boolean) {
        init(ctx)
        val before = _zones.value
        val targets = zone?.let { listOf(it) } ?: Zone.entries
        val after = store(ctx, targets) { it.copy(on = on) }
        val frames = mutableListOf<ByteArray>()
        val wasPowered = before.values.any { it.on }
        val nowPowered = after.values.any { it.on }
        when {
            !nowPowered -> frames += power(ctx, false)
            !wasPowered -> { frames += power(ctx, true); for (z in Zone.entries) frames += look(ctx, z, after.getValue(z)) }
            else -> for (z in targets) frames += look(ctx, z, after.getValue(z))
        }
        send(ctx, frames, replaceable = false)
    }

    fun setBrightness(ctx: Context, zone: Zone?, percent: Int) {
        init(ctx)
        val v = percent.coerceIn(1, 100)
        val targets = zone?.let { listOf(it) } ?: Zone.entries
        val after = store(ctx, targets) { it.copy(brightness = v) }
        // A zone that's off stays dark: the new brightness is used when it comes back on
        send(ctx, targets.filter { after.getValue(it).on }.map { z -> Builder(format(ctx, z)).apply { bright(v) }.bytes!! }.distinct())
    }

    /** A colour on the wheel (0–1), or [WHITE]. */
    fun setHue(ctx: Context, zone: Zone?, hue: Float) {
        init(ctx)
        val targets = zone?.let { listOf(it) } ?: Zone.entries
        val after = store(ctx, targets) { it.copy(hue = hue) }
        send(ctx, targets.filter { after.getValue(it).on }.map { z -> colorFrame(ctx, z, hue) }.distinct())
    }

    /** Each zone its own colour, in one send (two separate [setHue] calls would overtake each other). */
    fun setHues(ctx: Context, trims: Float, pockets: Float) {
        init(ctx)
        val hues = mapOf(Zone.TRIMS to trims, Zone.POCKETS to pockets)
        val next = _zones.value.toMutableMap()
        val e = prefs(ctx).edit()
        for ((z, h) in hues) { next[z] = next.getValue(z).copy(hue = h); runCatching { e.putFloat("${z.name}_hue", h) } }
        runCatching { e.apply() }
        _zones.value = next
        send(ctx, hues.filter { next.getValue(it.key).on }.map { (z, h) -> colorFrame(ctx, z, h) })
    }

    /** Save the change for [targets]; returns every zone's new state. */
    private fun store(ctx: Context, targets: List<Zone>, change: (ZoneState) -> ZoneState): Map<Zone, ZoneState> {
        val next = _zones.value.toMutableMap()
        val e = prefs(ctx).edit()
        for (z in targets) {
            val s = change(next.getValue(z))
            next[z] = s
            runCatching { e.putBoolean("${z.name}_on", s.on).putInt("${z.name}_bright", s.brightness).putFloat("${z.name}_hue", s.hue) }
        }
        runCatching { e.apply() }
        _zones.value = next
        return next
    }

    private fun format(ctx: Context, z: Zone) = Format.of(z.symphonyByDefault != swapped(ctx))

    /** A zone as it should look: its brightness and colour when on, black when off. */
    private fun look(ctx: Context, z: Zone, s: ZoneState): List<ByteArray> =
        if (s.on) listOf(Builder(format(ctx, z)).apply { bright(s.brightness) }.bytes!!, colorFrame(ctx, z, s.hue))
        else listOf(Builder(format(ctx, z)).apply { color(0, 0, 0) }.bytes!!)

    private fun colorFrame(ctx: Context, z: Zone, hue: Float): ByteArray {
        val c = if (hue < 0f) android.graphics.Color.WHITE
        else android.graphics.Color.HSVToColor(floatArrayOf((hue.coerceIn(0f, 1f) * 360f) % 360f, 1f, 1f))
        return Builder(format(ctx, z)).apply { color(android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c)) }.bytes!!
    }

    /** The box's own power switch, in both formats so both light sets hear it. */
    private fun power(ctx: Context, on: Boolean): List<ByteArray> =
        Format.entries.map { f -> Builder(f).apply { power(on) }.bytes!! }

    // Sliders fire dozens of changes a second: pass on at most one every SEND_GAP_MS, always the newest
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingFrames: List<ByteArray>? = null
    private var lastSend = 0L
    private const val SEND_GAP_MS = 70L

    /** [replaceable]: a slider value, which the next one may overtake; switches always go through. */
    private fun send(ctx: Context, frames: List<ByteArray>, replaceable: Boolean = true) {
        if (frames.isEmpty()) return
        if (!replaceable) { sendNow(ctx.applicationContext, frames, replaceable = false); return }
        val wasPending = pendingFrames != null
        pendingFrames = frames
        if (wasPending) return
        val wait = (lastSend + SEND_GAP_MS - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0)
        main.postDelayed({
            val f = pendingFrames ?: return@postDelayed
            pendingFrames = null
            lastSend = android.os.SystemClock.uptimeMillis()
            sendNow(ctx.applicationContext, f)
        }, wait)
    }

    private fun sendNow(ctx: Context, frames: List<ByteArray>, replaceable: Boolean = true) {
        val addr = address(ctx) ?: run { Log.w(TAG, "no lights address set"); return }
        val hex = frames.joinToString(",") { f -> f.joinToString("") { "%02X".format(it) } }
        if (_link.value == Link.OFF) service(ctx, LightsService.ACTION_CONNECT) { putExtra(LightsService.EXTRA_ADDRESS, addr) }
        service(ctx, LightsService.ACTION_SEND) { putExtra(LightsService.EXTRA_FRAMES, hex).putExtra(LightsService.EXTRA_REPLACEABLE, replaceable) }
    }

    private fun service(ctx: Context, action: String, extras: Intent.() -> Unit = {}) {
        val i = Intent(ctx, LightsService::class.java).setAction(action).apply(extras)
        runCatching { ctx.startForegroundService(i) }.onFailure { Log.w(TAG, "service didn't start: ${it.message}") }
    }

    // ---- Command bytes ----

    private enum class Format { COLORFUL, SYMPHONY; companion object { fun of(symphony: Boolean) = if (symphony) SYMPHONY else COLORFUL } }

    private class Builder(val f: Format) {
        var bytes: ByteArray? = null
        fun power(on: Boolean) { bytes = cmd(0x04, if (on) 1 else 0, 0) }
        fun bright(v: Int) { bytes = cmd(0x01, v) }
        fun color(r: Int, g: Int, b: Int) { bytes = cmd(0x05, 3, r, g, b, 0x10) }

        private fun cmd(c: Int, vararg p: Int): ByteArray = when (f) {
            // 8E, length (command + parameters + sub-group byte), command, parameters, sub-groups
            Format.COLORFUL -> (listOf(0x8E, p.size + 2, c) + p.toList() + 0x70).map { it.toByte() }.toByteArray()
            // 7E, length, command, four parameter bytes padded with FF, 00, EF (colour's fifth byte takes the 00)
            Format.SYMPHONY -> {
                val out = intArrayOf(0x7E, if (c == 0x01) 4 else 7, c, 0xFF, 0xFF, 0xFF, 0xFF, 0x00, 0xEF)
                p.forEachIndexed { i, v -> if (3 + i < 8) out[3 + i] = v }
                out.map { it.toByte() }.toByteArray()
            }
        }
    }
}
