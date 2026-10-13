package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.Preset
import com.sharkos.launcher.car.Zone
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlin.math.roundToInt

data class DockApp(val pkg: String, val label: String)

/**
 * The HVAC Bar's three layouts, picked in Settings. All share one cabin temperature (both zones
 * together, so the bar can never leave them out of sync) and a fan speed with its own arrows.
 */
enum class BarStyle(val label: String, val blurb: String) {
    ORIGINAL("Original", "Separate passenger and driver temperatures; the fan button opens the Climate page."),
    BALANCED("Balanced", "Fan by the passenger, temperature by the driver, A/C beside the apps."),
    CAPSULE("Capsule", "Fan, A/C and temperature together in one pill on the driver's side."),
    MIRRORED("Mirrored", "Fan with level bars on the left, temperature on the right, Home centred."),
    SLIDER("Slider", "Drag along the strips to set the fan and cabin temperature in one swipe."),
    PRESETS("Presets", "One-tap Cool, Comfort, Warm and Demist scenes beside the temperature."),
}

/** Which side of the bar the cabin temperature sits on; the fan takes the other side. */
enum class TempSide(val label: String) { LEFT("Left"), RIGHT("Right") }

/** What the HVAC Bar can do; wired to the car and the launcher in [Root]. */
class BarActions(
    val nudgeCabin: (Int) -> Unit,
    /** Set the cabin temperature outright (the slider). */
    val setCabin: (Float) -> Unit,
    /** One zone only: the Original layout's separate temperatures. */
    val nudgeTemp: (Zone, Int) -> Unit,
    val preset: (Preset) -> Unit,
    val nudgeFan: (Int) -> Unit,
    val cycleHeat: (Zone) -> Unit,
    val cycleVent: (Zone) -> Unit,
    val toggleAc: () -> Unit,
    val home: () -> Unit,
    val apps: () -> Unit,
    val climate: () -> Unit,
    val launch: (String) -> Unit,
    /** Tap on an empty + spot: pick an app for it. */
    val addApp: () -> Unit,
    /** Hold a pinned app: take it off the bar. */
    val removeApp: (DockApp) -> Unit,
    /** Set the fan speed outright (the fan slider). */
    val setFan: (Int) -> Unit = {},
)

/**
 * The bar on every SharkOS screen, standing in for BYD's own climate bar. Seat heat and cooling sit
 * on the outer edges in every style: passenger on the left, driver on the right (right-hand drive).
 */
@Composable
fun BottomBar(
    climate: ClimateState,
    dock: List<DockApp>,
    screen: Screen,
    a: BarActions,
    modifier: Modifier = Modifier,
    style: BarStyle = BarStyle.BALANCED,
    tempSide: TempSide = TempSide.RIGHT,
) {
    val swap = tempSide == TempSide.LEFT
    Row(
        modifier.fillMaxWidth().height(88.dp).background(Sh.bar).barTint(LocalWallpaper.current).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeatButton(ShIcons.flame, climate.passengerSeat.heat, Sh.orange, "Passenger seat heat") { a.cycleHeat(Zone.PASSENGER) }
        Gap(8.dp)
        SeatButton(ShIcons.vent, climate.passengerSeat.vent, Sh.cyan, "Passenger seat cooling") { a.cycleVent(Zone.PASSENGER) }
        Gap(12.dp)

        // Each layout as written has the temperature on the right; [swap] mirrors the climate parts.
        // Seats stay put: passenger left, driver right.
        when (style) {
            BarStyle.ORIGINAL -> {
                ZoneTemp(climate, Zone.PASSENGER, a)
                Centre {
                    if (swap) { FanButton(climate, screen == Screen.CLIMATE, a.climate); AcButton(climate, a); Divider(); Dock(dock, screen, a) }
                    else { Dock(dock, screen, a); Divider(); AcButton(climate, a); FanButton(climate, screen == Screen.CLIMATE, a.climate) }
                }
                ZoneTemp(climate, Zone.DRIVER, a)
            }
            BarStyle.SLIDER -> {
                if (swap) TempSlider(climate, a) else FanSlider(climate, a)
                Centre { Dock(dock, screen, a) }
                if (swap) FanSlider(climate, a) else TempSlider(climate, a)
            }
            BarStyle.PRESETS -> {
                val presets: @Composable () -> Unit = { Capsule { for (p in Preset.entries) PresetChip(p, p.matches(climate)) { a.preset(p) } } }
                if (swap) { CabinArrows(climate, a); Gap(8.dp); presets(); Centre { Dock(dock, screen, a) } }
                else { Centre { Dock(dock, screen, a) }; presets(); Gap(8.dp); CabinArrows(climate, a) }
            }
            BarStyle.BALANCED -> {
                if (swap) CabinArrows(climate, a) else FanArrows(climate, a)
                Centre {
                    if (swap) { AcButton(climate, a); Divider(); Dock(dock, screen, a) }
                    else { Dock(dock, screen, a); Divider(); AcButton(climate, a) }
                }
                if (swap) FanArrows(climate, a) else CabinArrows(climate, a)
            }
            BarStyle.CAPSULE -> {
                val capsule: @Composable () -> Unit = {
                    Capsule {
                        if (swap) CabinArrows(climate, a, compact = true) else FanArrows(climate, a, compact = true)
                        CapsuleDivider()
                        AcButton(climate, a, size = 48.dp, round = true)
                        CapsuleDivider()
                        if (swap) FanArrows(climate, a, compact = true) else CabinArrows(climate, a, compact = true)
                    }
                }
                if (swap) { capsule(); Centre { Dock(dock, screen, a) } }
                else { Centre { Dock(dock, screen, a) }; capsule() }
            }
            BarStyle.MIRRORED -> {
                val fan: @Composable () -> Unit = { Capsule { FanArrows(climate, a, bars = true) } }
                val cabin: @Composable () -> Unit = { Capsule { CabinArrows(climate, a, caption = "Cabin · synced") } }
                if (swap) cabin() else fan()
                Centre { Dock(dock, screen, a, homeInMiddle = true); AcButton(climate, a) }
                if (swap) fan() else cabin()
            }
        }

        Gap(12.dp)
        SeatButton(ShIcons.vent, climate.driverSeat.vent, Sh.cyan, "Driver seat cooling") { a.cycleVent(Zone.DRIVER) }
        Gap(8.dp)
        SeatButton(ShIcons.flame, climate.driverSeat.heat, Sh.orange, "Driver seat heat") { a.cycleHeat(Zone.DRIVER) }
    }
}

const val DOCK_SLOTS = 5

@Composable
private fun RowScope.Centre(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.weight(1f),
    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

@Composable
private fun Capsule(content: @Composable RowScope.() -> Unit) = Row(
    Modifier.height(68.dp).clip(RoundedCornerShape(34.dp)).background(Sh.card).padding(horizontal = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
)

@Composable
private fun Divider() = Box(Modifier.width(1.dp).height(36.dp).background(Sh.line))

@Composable
private fun CapsuleDivider() = Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(32.dp).background(Sh.line))

/** Home, the pinned apps, empty + spots, and All apps. */
@Composable
private fun Dock(dock: List<DockApp>, screen: Screen, a: BarActions, homeInMiddle: Boolean = false) {
    val slots: List<@Composable () -> Unit> = dock.map { app ->
        @Composable {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    .press({ a.launch(app.pkg) }, onLongClick = { a.removeApp(app) }, label = app.label),
                contentAlignment = Alignment.Center,
            ) { AppIcon(app.pkg, app.label, 46.dp) }
        }
    } + List((DOCK_SLOTS - dock.size).coerceAtLeast(0)) { @Composable { AddSlot(a.addApp) } }
    val home: @Composable () -> Unit = { BarIcon(ShIcons.home, "Home", screen == Screen.HOME, onClick = a.home) }
    if (homeInMiddle) {
        val half = slots.size / 2
        slots.take(half).forEach { it() }
        home()
        slots.drop(half).forEach { it() }
    } else {
        home()
        slots.forEach { it() }
    }
    BarIcon(ShIcons.grid, "All apps", screen == Screen.APPS, onClick = a.apps)
}

/** An empty spot on the bar: a + that opens the app picker. */
@Composable
private fun AddSlot(onClick: () -> Unit) = Box(
    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).press(onClick, label = "Add an app to the bar"),
    contentAlignment = Alignment.Center,
) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Sh.card), contentAlignment = Alignment.Center) {
        Glyph(ShIcons.plus, Sh.faint, 20.dp)
    }
}

@Composable
private fun Arrow(icon: ImageVector, tint: Color, label: String, width: Dp, onClick: () -> Unit) = Box(
    Modifier.size(width, 60.dp).press(onClick, label = label),
    contentAlignment = Alignment.Center,
) { Glyph(icon, tint, 22.dp) }

/**
 * ‹ 22° › for the whole cabin: sets both zones at once (and leaves dual mode), so the bar can never
 * leave the two sides different. Tapping the number opens the full Climate page.
 */
@Composable
private fun CabinArrows(c: ClimateState, a: BarActions, compact: Boolean = false, caption: String = "Cabin") {
    val w = if (compact) 38.dp else 44.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(ShIcons.chevronLeft, Sh.cyan, "Cooler", w) { a.nudgeCabin(-1) }
        Column(
            Modifier.width(if (compact) 60.dp else 72.dp).press(a.climate, label = "Climate"),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Txt(if (c.power) "${c.driverTemp.toInt()}°" else "Off", Sh.text(if (compact) 27.sp else 30.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint, (-0.6).sp))
            if (!compact) Txt(caption, Sh.text(11.sp, FontWeight.Medium, Sh.dim))
        }
        Arrow(ShIcons.chevronRight, Sh.orange, "Warmer", w) { a.nudgeCabin(1) }
    }
}

/** ‹ fan 3 › with its own arrows; [bars] shows the level as seven small bars instead. */
@Composable
private fun FanArrows(c: ClimateState, a: BarActions, compact: Boolean = false, bars: Boolean = false) {
    val w = if (compact) 38.dp else 44.dp
    val on = c.power
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(ShIcons.chevronLeft, Sh.dim, "Fan slower", w) { a.nudgeFan(-1) }
        if (bars) {
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (n in 1..ClimateState.MAX_FAN) {
                        Box(Modifier.size(8.dp, 16.dp).clip(RoundedCornerShape(3.dp)).background(if (on && n <= c.fan) Sh.blue else Sh.line))
                    }
                }
                Txt(fanLabel(c), Sh.text(11.sp, FontWeight.Medium, Sh.dim))
            }
        } else {
            Column(Modifier.width(if (compact) 60.dp else 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Glyph(ShIcons.fan, if (on) Sh.ink else Sh.faint, if (compact) 18.dp else 20.dp)
                    Txt(if (on) "${c.fan}" else "–", Sh.text(if (compact) 25.sp else 27.sp, FontWeight.SemiBold, if (on) Sh.ink else Sh.faint))
                }
                if (!compact) Txt(if (c.auto && on) "Auto" else "Fan", Sh.text(11.sp, FontWeight.Medium, Sh.dim))
            }
        }
        Arrow(ShIcons.chevronRight, Sh.dim, "Fan faster", w) { a.nudgeFan(1) }
    }
}

private fun fanLabel(c: ClimateState) = when {
    !c.power -> "Fan off"
    c.auto -> "Fan auto · ${c.fan}"
    else -> "Fan ${c.fan}"
}

@Composable
private fun AcButton(c: ClimateState, a: BarActions, size: Dp = 56.dp, round: Boolean = false) {
    val on = c.ac && c.power
    val bg by animateColorAsState(if (on) Sh.blue else if (round) Sh.raised else Color.Transparent, tween(180), label = "ac")
    Box(
        Modifier.size(size).clip(RoundedCornerShape(if (round) size / 2 else 16.dp)).background(bg)
            .press(a.toggleAc, label = "A/C ${if (on) "on" else "off"}"),
        contentAlignment = Alignment.Center,
    ) { Glyph(ShIcons.snow, if (on) Color.White else Sh.dim, if (round) 22.dp else 26.dp) }
}

/**
 * One tap steps a seat Off → Low → High → Off. The icon lights in its colour and two small bars
 * show the level, so it reads at a glance without words.
 */
@Composable
private fun SeatButton(icon: ImageVector, level: Int, color: Color, label: String, onClick: () -> Unit) {
    val on = level > 0
    val bg by animateColorAsState(if (on) color.copy(alpha = 0.18f) else Sh.raised, tween(180), label = "seat")
    Column(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .press(onClick, label = "$label ${listOf("off", "low", "high")[level.coerceIn(0, 2)]}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(icon, if (on) color else Sh.dim, 22.dp)
        Gap(5.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..2) Box(Modifier.size(10.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(if (level >= i) color else Sh.line))
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, onColor: Color = Sh.ink) {
    val bg by animateColorAsState(if (active) Sh.raised else Color.Transparent, tween(180), label = "bar")
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = label),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (active) onColor else Sh.dim, 26.dp) }
}

/** Original layout: ‹ 22° › for one zone. */
@Composable
private fun ZoneTemp(c: ClimateState, zone: Zone, a: BarActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Arrow(ShIcons.chevronLeft, Sh.cyan, "${zone.label} cooler", 44.dp) { a.nudgeTemp(zone, -1) }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (c.power) "${c.temp(zone).toInt()}°" else "Off", Sh.text(28.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint, (-0.5).sp))
            Txt(zone.label, Sh.text(11.sp, FontWeight.Medium, Sh.dim))
        }
        Arrow(ShIcons.chevronRight, Sh.orange, "${zone.label} warmer", 44.dp) { a.nudgeTemp(zone, 1) }
    }
}

/** Original layout: opens the Climate page and shows the fan speed (or AUTO). */
@Composable
private fun FanButton(c: ClimateState, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Sh.raised else Color.Transparent, tween(180), label = "fanbg")
    Column(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = "Climate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(ShIcons.fan, if (c.power) Sh.ink else Sh.dim, 22.dp)
        Txt(
            when { !c.power -> "OFF"; c.auto -> "AUTO"; else -> "${c.fan}" },
            Sh.text(11.sp, FontWeight.Bold, if (c.power) Sh.ink else Sh.faint, 0.3.sp).copy(textAlign = TextAlign.Center),
        )
    }
}

/**
 * Slider layout: a blue-to-orange strip; drag or tap anywhere on it to set the cabin temperature.
 * The thumb shows the value. The readout to its left opens the Climate page.
 */
@Composable
private fun TempSlider(c: ClimateState, a: BarActions) {
    val min = ClimateState.MIN_TEMP
    val max = ClimateState.MAX_TEMP
    var dragging by remember { mutableStateOf(false) }
    var dragTemp by remember { mutableFloatStateOf(c.driverTemp) }
    val shown = if (dragging) dragTemp else c.driverTemp
    val setCabin by rememberUpdatedState(a.setCabin)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(56.dp).press(a.climate, label = "Climate"), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (c.power) "${shown.roundToInt()}°" else "Off", Sh.text(24.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint, (-0.5).sp))
            Txt("Cabin", Sh.text(11.sp, FontWeight.Medium, Sh.dim))
        }
        Gap(6.dp)
        Box(
            Modifier.size(SLIDER_W, 60.dp)
                .semantics { contentDescription = "Cabin temperature slider" }
                .pointerInput(Unit) {
                    fun at(x: Float) = (min + (x / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt().toFloat()
                    detectTapGestures { setCabin(at(it.x)) }
                }
                .pointerInput(Unit) {
                    fun at(x: Float) = (min + (x / size.width).coerceIn(0f, 1f) * (max - min)).roundToInt().toFloat()
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; dragTemp = at(it.x) },
                        onDragEnd = { dragging = false; setCabin(dragTemp) },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        val t = at(change.position.x)
                        if (t != dragTemp) { dragTemp = t; setCabin(t) }
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val trackH = 14.dp.toPx()
                val y = size.height / 2
                val thumbR = 22.dp.toPx()
                val left = thumbR
                val width = size.width - thumbR * 2
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Sh.cyan, Color(0xFF8E8EFF), Sh.orange), startX = left, endX = left + width),
                    Offset(left, y - trackH / 2), Size(width, trackH), CornerRadius(trackH / 2),
                    alpha = if (c.power) 1f else 0.35f,
                )
                val f = ((shown - min) / (max - min)).coerceIn(0f, 1f)
                drawCircle(Color.White, thumbR, Offset(left + f * width, y))
            }
            // the number rides inside the thumb
            val f = ((shown - min) / (max - min)).coerceIn(0f, 1f)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.padding(start = SLIDER_THUMB_TRAVEL * f).size(44.dp), contentAlignment = Alignment.Center) {
                    Txt("${shown.roundToInt()}", Sh.text(17.sp, FontWeight.Bold, Color.Black))
                }
            }
        }
    }
}

/** Slider tracks: two must fit beside the apps and seats, so a little shorter than one alone would be. */
private val SLIDER_W = 196.dp
private val SLIDER_THUMB_TRAVEL = SLIDER_W - 44.dp

/**
 * Slider layout's fan: a grey-to-blue strip from 1 to 7, the same size and feel as the temperature
 * slider. The readout shows the speed (or Auto) and opens the Climate page.
 */
@Composable
private fun FanSlider(c: ClimateState, a: BarActions) {
    val max = ClimateState.MAX_FAN
    var dragging by remember { mutableStateOf(false) }
    var dragFan by remember { mutableIntStateOf(c.fan) }
    val shown = if (dragging) dragFan else c.fan
    val setFan by rememberUpdatedState(a.setFan)
    fun at(x: Float, w: Float) = (1 + (x / w).coerceIn(0f, 1f) * (max - 1)).roundToInt().coerceIn(1, max)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(56.dp).press(a.climate, label = "Climate"), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Glyph(ShIcons.fan, if (c.power) Sh.ink else Sh.faint, 16.dp)
                Txt(if (c.power) "$shown" else "–", Sh.text(24.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint))
            }
            Txt(if (c.auto && c.power) "Auto" else "Fan", Sh.text(11.sp, FontWeight.Medium, Sh.dim))
        }
        Gap(6.dp)
        Box(
            Modifier.size(SLIDER_W, 60.dp)
                .semantics { contentDescription = "Fan speed slider" }
                .pointerInput(Unit) { detectTapGestures { setFan(at(it.x, size.width.toFloat())) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; dragFan = at(it.x, size.width.toFloat()) },
                        onDragEnd = { dragging = false; setFan(dragFan) },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        val f = at(change.position.x, size.width.toFloat())
                        if (f != dragFan) { dragFan = f; setFan(f) }
                    }
                },
        ) {
            val f = (shown - 1).toFloat() / (max - 1)
            Canvas(Modifier.fillMaxSize()) {
                val trackH = 14.dp.toPx()
                val y = size.height / 2
                val thumbR = 22.dp.toPx()
                val left = thumbR
                val width = size.width - thumbR * 2
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF3B6FD8), Sh.blue), startX = left, endX = left + width),
                    Offset(left, y - trackH / 2), Size(width, trackH), CornerRadius(trackH / 2),
                    alpha = if (c.power) 1f else 0.35f,
                )
                // a tick for each speed
                for (n in 0 until max) drawCircle(Color.White.copy(alpha = 0.35f), 2.dp.toPx(), Offset(left + width * n / (max - 1), y))
                drawCircle(Color.White, thumbR, Offset(left + f.coerceIn(0f, 1f) * width, y))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.padding(start = SLIDER_THUMB_TRAVEL * f.coerceIn(0f, 1f)).size(44.dp), contentAlignment = Alignment.Center) {
                    Txt("$shown", Sh.text(17.sp, FontWeight.Bold, Color.Black))
                }
            }
        }
    }
}

/** Presets layout: one scene button; lit while the cabin is set that way. */
@Composable
private fun PresetChip(p: Preset, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) (if (p == Preset.DEMIST || p == Preset.WARM) Sh.orange else Sh.blue) else Color.Transparent, tween(180), label = "preset")
    val fg = if (active) (if (p == Preset.DEMIST || p == Preset.WARM) Color(0xFF2B1700) else Color.White) else Sh.ink
    Column(
        Modifier.size(66.dp, 56.dp).clip(RoundedCornerShape(28.dp)).background(bg).press(onClick, label = "${p.label} preset"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(when (p) { Preset.COOL -> ShIcons.snow; Preset.COMFORT -> ShIcons.fan; Preset.WARM -> ShIcons.flame; Preset.DEMIST -> ShIcons.demistFront }, fg, 20.dp)
        Gap(2.dp)
        Txt(p.label, Sh.text(11.sp, FontWeight.SemiBold, fg))
    }
}
