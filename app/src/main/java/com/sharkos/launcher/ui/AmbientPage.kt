package com.sharkos.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharkos.launcher.lights.AmbientLights
import kotlinx.coroutines.delay

/** What the Ambient lighting page asks Root for: things that need an Activity. */
class LightsActions(
    val requestPermission: () -> Unit = {},
    val enableBluetooth: () -> Unit = {},
    val openLocation: () -> Unit = {},
)

/**
 * Settings → Ambient lighting. Experimental: setup, the three test controls (on/off, brightness,
 * colour) and diagnostics, all on this one page and in [AmbientLights].
 */
@Composable
fun AmbientPage(a: LightsActions) {
    val ctx = LocalContext.current
    remember { AmbientLights.init(ctx) }
    val link by AmbientLights.link.collectAsStateWithLifecycle()
    val found by AmbientLights.found.collectAsStateWithLifecycle()
    val name by AmbientLights.connectedName.collectAsStateWithLifecycle()
    val log by AmbientLights.log.collectAsStateWithLifecycle()
    val protocol by AmbientLights.protocol.collectAsStateWithLifecycle()

    // Permission and switch states, rechecked every second while the page is open (cheap reads)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val present = remember(tick) { AmbientLights.bluetoothPresent(ctx) }
    val btOn = remember(tick) { AmbientLights.bluetoothOn(ctx) }
    val allowed = remember(tick) { AmbientLights.hasPermission(ctx) }
    val locOn = remember(tick) { AmbientLights.locationOn(ctx) }
    val saved = remember(tick) { AmbientLights.savedName(ctx) }

    var on by remember { mutableStateOf(true) }
    var bright by remember { mutableFloatStateOf(1f) }
    var hue by remember { mutableFloatStateOf(0.6f) }

    Txt("Experimental. Controls the aftermarket LED kit used with the Magic CarLight and Lotus Lamp X apps. Close the phone app first: the lights take one controller at a time.",
        Sh.text(15.sp, color = Sh.dim), maxLines = 3)

    SettingsGroup("Setup") {
        SettingsItem("Bluetooth", when { !present -> "No Bluetooth adapter found."; btOn -> "On."; else -> "Bluetooth is off." },
            status = if (btOn) "On" to Sh.green else "Off" to Sh.orange) {
            if (present && !btOn) PillButton("Turn on", a.enableBluetooth, bg = Sh.blue, fg = Color.White)
        }
        SettingsItem("Location permission", "Android 11 only lets apps search for Bluetooth devices with this. SharkOS never reads where you are.",
            status = if (allowed) "Allowed" to Sh.green else "Needed" to Sh.orange) {
            if (!allowed) PillButton("Allow", a.requestPermission, bg = Sh.blue, fg = Color.White)
        }
        if (!allowed) SettingsHint("If no prompt appears: adb shell pm grant com.sharkos.launcher android.permission.ACCESS_FINE_LOCATION")
        SettingsItem("Location services", if (locOn) "On." else "Searching finds nothing while the car's location is off.",
            status = if (locOn) "On" to Sh.green else "Off" to Sh.orange) {
            if (!locOn) PillButton("Open", a.openLocation)
        }
        if (!locOn) SettingsHint("Or: adb shell settings put secure location_mode 3")
        SettingsItem(
            "Lights",
            when (link) {
                AmbientLights.Link.CONNECTED -> "Connected to ${name ?: "the lights"}."
                AmbientLights.Link.CONNECTING -> "Connecting…"
                AmbientLights.Link.SEARCHING -> "Searching nearby…"
                AmbientLights.Link.IDLE -> saved?.let { "Saved: $it. Connects when you use a control." } ?: "Not set up yet."
            },
            status = when (link) {
                AmbientLights.Link.CONNECTED -> "Connected" to Sh.green
                AmbientLights.Link.CONNECTING, AmbientLights.Link.SEARCHING -> "Working" to Sh.orange
                AmbientLights.Link.IDLE -> null
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (link) {
                    AmbientLights.Link.CONNECTED -> PillButton("Release", { AmbientLights.disconnect() })
                    AmbientLights.Link.SEARCHING -> PillButton("Stop", { AmbientLights.stopScan() })
                    else -> {
                        if (saved != null) PillButton("Connect", { AmbientLights.connectSaved(ctx) }, bg = Sh.blue, fg = Color.White)
                        PillButton("Search", { AmbientLights.search(ctx) }, bg = if (saved == null) Sh.blue else Sh.raised, fg = if (saved == null) Color.White else Sh.ink)
                    }
                }
            }
        }
        for (f in found.take(8)) {
            SettingsItem(f.name, "${f.address} · signal ${f.rssi} dBm" + if (f.lights) " · looks like the lights" else "",
                status = if (f.lights) "Lights" to Sh.green else null) {
                PillButton("Connect", { AmbientLights.connect(ctx, f.address, f.name) })
            }
        }
    }

    SettingsGroup("Test controls") {
        SettingsItem("Lights on", "Turns the LEDs on or off.") {
            Switch(on, { v -> on = v; AmbientLights.power(v) }, "Ambient lights on")
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Brightness", Sh.text(18.sp, FontWeight.SemiBold), Modifier.weight(1f))
                Txt("${(bright * 100).toInt().coerceAtLeast(1)}%", Sh.text(16.sp, FontWeight.Medium, Sh.dim))
            }
            GradientSlider(bright, Brush.horizontalGradient(listOf(Color(0xFF2C2C2E), Color.White)), "Brightness") { v ->
                bright = v; AmbientLights.brightness((v * 100).toInt())
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Txt("Colour", Sh.text(18.sp, FontWeight.SemiBold), Modifier.weight(1f))
                Box(Modifier.size(28.dp).clip(RoundedCornerShape(14.dp)).background(hueColor(hue)))
            }
            GradientSlider(hue, Brush.horizontalGradient(RAINBOW), "Colour") { v ->
                hue = v
                val c = hueColor(v)
                AmbientLights.color((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt())
            }
        }
    }

    SettingsGroup("Diagnostics") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Txt("Command format: try Alternate if the lights connect but don't respond.", Sh.text(15.sp, color = Sh.dim))
            Segmented(AmbientLights.Protocol.entries.map { it.label }, protocol.ordinal, { AmbientLights.setProtocol(ctx, AmbientLights.Protocol.entries[it]) }, Modifier.fillMaxWidth(), greyFirst = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Search again", { AmbientLights.search(ctx, connectWhenFound = false) })
                PillButton("Forget lights", { AmbientLights.forget(ctx) })
            }
            Gap(4.dp)
            if (log.isEmpty()) Txt("Nothing yet.", Sh.text(13.sp, color = Sh.dim).copy(fontFamily = FontFamily.Monospace))
            for (line in log.take(20)) Txt(line, Sh.text(13.sp, color = Sh.ink).copy(fontFamily = FontFamily.Monospace), maxLines = 3)
        }
    }
}

private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

private fun hueColor(f: Float): Color = Color.hsv((f.coerceIn(0f, 1f) * 360f) % 360f, 1f, 1f)

/** A track painted with [brush] and a white knob; drag or tap anywhere along it. */
@Composable
private fun GradientSlider(value: Float, brush: Brush, label: String, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp)
            .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = 14.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(brush, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, h), CornerRadius(h / 2))
        val r = 15.dp.toPx()
        val x = (value.coerceIn(0f, 1f) * (size.width - 2 * r)) + r
        drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), Offset(x, size.height / 2))
        drawCircle(Color.White, r, Offset(x, size.height / 2))
    }
}
