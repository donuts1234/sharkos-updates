package com.sharkos.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharkos.launcher.lights.Lights
import com.sharkos.launcher.lights.Lights.Zone
import kotlinx.coroutines.delay

/** What the Ambient lighting page needs an Activity for. */
class LightsActions(
    val requestPermission: () -> Unit = {},
    val enableBluetooth: () -> Unit = {},
)

/** Settings → Ambient lighting: the two zones, then setup. */
@Composable
fun AmbientPage(a: LightsActions) {
    val ctx = LocalContext.current
    remember { Lights.init(ctx) }
    val link by Lights.link.collectAsStateWithLifecycle()
    val zones by Lights.zones.collectAsStateWithLifecycle()
    val linked by Lights.linked.collectAsStateWithLifecycle()

    // Settings that live outside a flow, re-read every couple of seconds while the page is open
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(2000); tick++ } }
    val btOn = remember(tick) { Lights.bluetoothOn(ctx) }
    val allowed = remember(tick) { Lights.hasPermission(ctx) }
    var address by remember { mutableStateOf(Lights.address(ctx)) }
    var auto by remember { mutableStateOf(Lights.autoConnect(ctx)) }
    var swapped by remember { mutableStateOf(Lights.swapped(ctx)) }
    var editing by remember { mutableStateOf(address == null) }

    SettingsGroup("Lights") {
        SettingsItem(
            "Connection",
            when {
                address == null -> "Enter your lights' Bluetooth address below to get started."
                link == Lights.Link.CONNECTED -> "Connected. SharkOS keeps the lights connected and reconnects at start-up."
                link == Lights.Link.CONNECTING -> "Looking for your lights… Close the phone app if it's connected to them."
                else -> "Not connected."
            },
            status = when (link) {
                Lights.Link.CONNECTED -> "Connected" to Sh.green
                Lights.Link.CONNECTING -> "Connecting" to Sh.orange
                Lights.Link.OFF -> "Off" to Sh.faint
            },
        ) { if (link == Lights.Link.OFF && address != null) PillButton("Connect", { Lights.connect(ctx) }, bg = Sh.blue, fg = Color.White) }
        SettingsItem("Same settings for both zones", "Off to set the door trims and the pockets and footwells separately.") {
            Switch(linked, { Lights.setLinked(ctx, it) }, "Same settings for both zones")
        }
    }

    if (linked) {
        ZoneCard("Both zones", zones.getValue(Zone.TRIMS), null)
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (z in Zone.entries) ZoneCard(z.label, zones.getValue(z), z, Modifier.weight(1f), compact = true)
        }
    }

    SettingsGroup("Setup") {
        if (!btOn) SettingsItem("Bluetooth", "Bluetooth is off.", status = "Off" to Sh.orange) {
            PillButton("Turn on", a.enableBluetooth, bg = Sh.blue, fg = Color.White)
        }
        if (!allowed) {
            SettingsItem("Location permission", "Android needs it to wake the car's Bluetooth before connecting. SharkOS never reads where you are.",
                status = "Needed" to Sh.orange) { PillButton("Allow", a.requestPermission, bg = Sh.blue, fg = Color.White) }
            SettingsHint("If no prompt appears: adb shell pm grant com.sharkos.launcher android.permission.ACCESS_FINE_LOCATION")
        }
        SettingsItem("Connect automatically", "Connects when the car starts and stays connected.") {
            Switch(auto, { auto = it; Lights.setAutoConnect(ctx, it); if (it) Lights.connect(ctx) }, "Connect automatically")
        }
        SettingsItem("Lights box", address?.let { "Bluetooth address $it" } ?: "Not set yet.") {
            if (!editing) PillButton("Change", { editing = true })
        }
        if (editing) AddressEntry(address.orEmpty()) { raw ->
            if (Lights.setAddress(ctx, raw)) { address = Lights.address(ctx); editing = false }
        }
        SettingsItem("Swap zones", "If the door trims answer to the pockets and footwells controls, and the other way round.") {
            Switch(swapped, { swapped = it; Lights.setSwapped(ctx, it) }, "Swap zones")
        }
        SettingsItem("Disconnect", "Lets go of the lights so the phone app can connect. SharkOS reconnects at the next start-up.") {
            PillButton("Disconnect", { Lights.disconnect(ctx) })
        }
    }
}

/** On/off, brightness and colour for one zone ([zone] null: both). */
@Composable
private fun ZoneCard(title: String, s: Lights.ZoneState, zone: Zone?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val ctx = LocalContext.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Sh.card).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(7.dp)).background(if (s.on) swatch(s.hue) else Sh.line))
            Gap(12.dp)
            Txt(title, Sh.text(20.sp, FontWeight.SemiBold), Modifier.weight(1f))
            Switch(s.on, { Lights.setOn(ctx, zone, it) }, "$title on")
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row {
                Txt("Brightness", Sh.text(15.sp, FontWeight.Medium, Sh.dim), Modifier.weight(1f))
                Txt("${s.brightness}%", Sh.text(15.sp, FontWeight.Medium, Sh.dim))
            }
            GradientSlider(s.brightness / 100f, Brush.horizontalGradient(listOf(Color(0xFF2C2C2E), Color.White)), "$title brightness") {
                Lights.setBrightness(ctx, zone, (it * 100).toInt())
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt("Colour", Sh.text(15.sp, FontWeight.Medium, Sh.dim))
            GradientSlider(if (s.white) 0f else s.hue, Brush.horizontalGradient(RAINBOW), "$title colour", showKnob = !s.white) {
                Lights.setHue(ctx, zone, it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                for (h in PRESETS) {
                    val on = if (h < 0f) s.white else !s.white && kotlin.math.abs(s.hue - h) < 0.01f
                    Box(
                        Modifier.size(if (compact) 34.dp else 40.dp).clip(RoundedCornerShape(20.dp)).background(swatch(h))
                            .then(if (on) Modifier.border(3.dp, Color.White, RoundedCornerShape(20.dp)) else Modifier)
                            .press({ Lights.setHue(ctx, zone, h) }, label = "$title ${presetName(h)}"),
                    )
                }
            }
        }
    }
}

private val PRESETS = listOf(Lights.WHITE, 0f, 0.07f, 0.14f, 0.33f, 0.5f, 0.62f, 0.75f, 0.88f)
private fun presetName(h: Float) = when (h) { Lights.WHITE -> "white"; 0f -> "red"; 0.07f -> "orange"; 0.14f -> "yellow"; 0.33f -> "green"; 0.5f -> "cyan"; 0.62f -> "blue"; 0.75f -> "purple"; else -> "pink" }
private fun swatch(h: Float): Color = if (h < 0f) Color.White else Color.hsv((h.coerceIn(0f, 1f) * 360f) % 360f, 1f, 1f)
private val RAINBOW = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** Type the box's Bluetooth address, AA:BB:CC:DD:EE:FF. */
@Composable
private fun AddressEntry(initial: String, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    Row(Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Sh.raised).padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (text.isEmpty()) Txt("e.g. BE:16:8B:53:40:A6", Sh.text(15.sp, color = Sh.faint))
            androidx.compose.foundation.text.BasicTextField(
                text, { text = it.take(17) }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = Sh.text(16.sp, color = Sh.ink).copy(fontFamily = FontFamily.Monospace),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Sh.blue),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters),
            )
        }
        PillButton("Save", { onSave(text) }, bg = Sh.blue, fg = Color.White)
    }
}

/** A track painted with [brush] and a white knob; drag or tap anywhere along it. */
@Composable
private fun GradientSlider(value: Float, brush: Brush, label: String, showKnob: Boolean = true, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(44.dp)
            .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = 14.dp.toPx()
        val top = (size.height - h) / 2
        drawRoundRect(brush, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
        if (!showKnob) return@Canvas
        val r = 15.dp.toPx()
        val x = (value.coerceIn(0f, 1f) * (size.width - 2 * r)) + r
        drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), Offset(x, size.height / 2))
        drawCircle(Color.White, r, Offset(x, size.height / 2))
    }
}
