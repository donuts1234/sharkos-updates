package com.sharkos.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.delay
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
 * The HVAC Bar's layouts, picked in Settings. All share one cabin temperature (both zones together, so
 * the bar can never leave them out of sync) and a fan speed. A saved choice of a layout since removed
 * (Original, Capsule, Presets) falls back to Balanced.
 */
enum class BarStyle(val label: String, val blurb: String) {
    BALANCED("Balanced", "Fan arrows, A/C beside the apps, cabin temperature arrows."),
    MIRRORED("Mirrored", "Fan with level bars, Home and Back in the middle of the apps."),
    SLIDER("Slider", "Drag along the strips to set the fan and cabin temperature in one swipe."),
}

/** The ambient lighting as the bar shows it: one set of controls for both zones. Null: no lights set up. */
data class BarLights(val connected: Boolean, val on: Boolean, val brightness: Int, val hue: Float)

/** Which side of the bar the cabin temperature sits on; the fan takes the other side. */
enum class TempSide(val label: String) { LEFT("Left"), RIGHT("Right") }

/** What the HVAC Bar can do; wired to the car and the launcher in [Root]. */
class BarActions(
    val nudgeCabin: (Int) -> Unit,
    /** Set the cabin temperature outright (the slider). */
    val setCabin: (Float) -> Unit,
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
    /** The bar's back button: one step back inside SharkOS. */
    val back: () -> Unit = {},
    /** Ambient lighting, both zones together. */
    val lightsOn: (Boolean) -> Unit = {},
    val lightsBrightness: (Int) -> Unit = {},
    /** 0–1 around the colour wheel, or below 0 for white. */
    val lightsHue: (Float) -> Unit = {},
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
    lights: BarLights? = null,
    /** Start on the lighting controls (screenshots). */
    showLights: Boolean = false,
    /** Album colours on the music page, in place of the wallpaper's tint. */
    art: ArtColors? = null,
    /** A plain colour instead of any tint (CarPlay: neutral, so it never clashes with the picture). */
    plain: Color? = null,
) {
    val swap = tempSide == TempSide.LEFT
    var lightsMode by remember { mutableStateOf(showLights) }
    if (lights == null) lightsMode = false
    // Back to climate after 10 s untouched; every touch on the bar restarts the count
    var touches by remember { mutableIntStateOf(0) }
    LaunchedEffect(lightsMode, touches) { if (lightsMode && !showLights) { delay(10_000); lightsMode = false } }
    val openLights: (() -> Unit)? = lights?.let { { lightsMode = true } }
    Row(
        modifier.fillMaxWidth().height(88.dp).then(when { plain != null -> Modifier.background(plain); art != null -> Modifier.artTint(art); else -> Modifier.background(Sh.bar).barTint(LocalWallpaper.current) })
            .pointerInput(Unit) { awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); touches++ } } }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeatButton(ShIcons.flame, climate.passengerSeat.heat, Sh.orange, "Passenger seat heat") { a.cycleHeat(Zone.PASSENGER) }
        Gap(8.dp)
        SeatButton(ShIcons.vent, climate.passengerSeat.vent, Sh.cyan, "Passenger seat cooling") { a.cycleVent(Zone.PASSENGER) }

        // Between the seats: the climate layout, or (sliding across) the lighting controls
        AnimatedContent(
            lightsMode,
            Modifier.weight(1f),
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally(tween(320)) { it * dir } + fadeIn(tween(200))) togetherWith
                    (slideOutHorizontally(tween(320)) { -it * dir } + fadeOut(tween(160)))
            },
            label = "bar",
        ) { showingLights ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (showingLights && lights != null) LightsStrip(lights, a) { lightsMode = false }
                else ClimateLayout(style, swap, climate, dock, screen, a, lights, openLights)
            }
        }
        SeatButton(ShIcons.vent, climate.driverSeat.vent, Sh.cyan, "Driver seat cooling") { a.cycleVent(Zone.DRIVER) }
        Gap(8.dp)
        SeatButton(ShIcons.flame, climate.driverSeat.heat, Sh.orange, "Driver seat heat") { a.cycleHeat(Zone.DRIVER) }
    }
}

/** Each layout as written has the temperature on the right; [swap] mirrors the climate parts. */
@Composable
private fun RowScope.ClimateLayout(
    style: BarStyle, swap: Boolean, climate: ClimateState, dock: List<DockApp>, screen: Screen, a: BarActions,
    lights: BarLights?, openLights: (() -> Unit)?,
) {
    Gap(12.dp)
    when (style) {
        BarStyle.SLIDER -> {
            if (swap) TempSlider(climate, a) else FanSlider(climate, a)
            Centre { Dock(dock, screen, a, lights = lights, openLights = openLights) }
            if (swap) FanSlider(climate, a) else TempSlider(climate, a)
        }
        BarStyle.BALANCED -> {
            if (swap) CabinArrows(climate, a) else FanArrows(climate, a)
            Centre {
                if (swap) { AcButton(climate, a); Divider(); Dock(dock, screen, a, lights = lights, openLights = openLights) }
                else { Dock(dock, screen, a, lights = lights, openLights = openLights); Divider(); AcButton(climate, a) }
            }
            if (swap) FanArrows(climate, a) else CabinArrows(climate, a)
        }
        BarStyle.MIRRORED -> {
            val fan: @Composable () -> Unit = { Capsule { FanArrows(climate, a, bars = true) } }
            val cabin: @Composable () -> Unit = { Capsule { CabinArrows(climate, a, caption = "Cabin · synced") } }
            if (swap) cabin() else fan()
            Centre { Dock(dock, screen, a, homeInMiddle = true, lights = lights, openLights = openLights); AcButton(climate, a) }
            if (swap) fan() else cabin()
        }
    }
    Gap(12.dp)
}

/**
 * The lighting controls, in place of the climate ones: back to climate, on/off, brightness, colour
 * and a few quick colours. One set for both zones (per-zone control lives in Settings).
 */
@Composable
private fun RowScope.LightsStrip(l: BarLights, a: BarActions, close: () -> Unit) {
    Gap(12.dp)
    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).press(close, label = "Back to climate"), contentAlignment = Alignment.Center) {
        Glyph(ShIcons.chevronLeft, Sh.dim, 26.dp)
    }
    Gap(8.dp)
    val tint = if (l.on) lightColor(l.hue) else Sh.dim
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (l.on) tint.copy(alpha = 0.22f) else Sh.raised)
            .press({ a.lightsOn(!l.on) }, label = if (l.on) "Ambient lights off" else "Ambient lights on"),
        contentAlignment = Alignment.Center,
    ) { Glyph(ShIcons.bulb, tint, 26.dp) }
    Gap(20.dp)
    Glyph(ShIcons.sun, Sh.dim, 20.dp)
    Gap(10.dp)
    StripSlider(l.brightness / 100f, Brush.horizontalGradient(listOf(Color(0xFF3A3A3C), Color.White)), "Ambient brightness", Modifier.weight(0.8f)) {
        a.lightsBrightness((it * 100).toInt().coerceIn(1, 100))
    }
    Gap(24.dp)
    StripSlider(if (l.hue < 0f) -1f else l.hue, Brush.horizontalGradient(RAINBOW), "Ambient colour", Modifier.weight(1f)) { a.lightsHue(it) }
    Gap(18.dp)
    for (h in listOf(-1f, 0.08f, 0.62f, 0f, 0.8f)) {
        val on = if (h < 0f) l.hue < 0f else l.hue >= 0f && kotlin.math.abs(l.hue - h) < 0.01f
        Box(
            Modifier.padding(horizontal = 4.dp).size(36.dp).clip(RoundedCornerShape(18.dp)).background(lightColor(h))
                .then(if (on) Modifier.border(3.dp, Color.White, RoundedCornerShape(18.dp)) else Modifier)
                .press({ a.lightsHue(h) }, label = "Ambient colour preset"),
        )
    }
    if (!l.connected) { Gap(12.dp); Txt("Connecting…", Sh.text(13.sp, FontWeight.Medium, Sh.orange)) }
    Gap(12.dp)
}


/** A light setting's colour: around the wheel, or white below 0. */
fun lightColor(h: Float): Color = if (h < 0f) Color.White else Color.hsv((h.coerceIn(0f, 1f) * 360f) % 360f, 1f, 1f)

/** A bar-height strip slider; a value below 0 hides the knob (white chosen on the colour strip). */
@Composable
private fun StripSlider(value: Float, brush: Brush, label: String, modifier: Modifier, onChange: (Float) -> Unit) {
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier.height(56.dp).semantics { contentDescription = label }
            .pointerInput(Unit) { detectTapGestures { change((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { c, _ -> change((c.position.x / size.width).coerceIn(0f, 1f)) } },
    ) {
        val h = 14.dp.toPx()
        val r = 15.dp.toPx()
        drawRoundRect(brush, Offset(r - h / 2, (size.height - h) / 2), Size(size.width - 2 * r + h, h), CornerRadius(h / 2))
        if (value < 0f) return@Canvas
        val x = r + value.coerceIn(0f, 1f) * (size.width - 2 * r)
        drawCircle(Color.Black.copy(alpha = 0.35f), r + 2.dp.toPx(), Offset(x, size.height / 2))
        drawCircle(Color.White, r, Offset(x, size.height / 2))
    }
}

const val DOCK_SLOTS = 3

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


/**
 * All apps on the left, then the three app spots (empty ones are + spots), then Home and Back on the
 * right, by the driver. [homeInMiddle] (Mirrored) puts Home and Back in the middle of the spots.
 */
@Composable
private fun Dock(dock: List<DockApp>, screen: Screen, a: BarActions, homeInMiddle: Boolean = false, lights: BarLights? = null, openLights: (() -> Unit)? = null) {
    val slots: List<@Composable () -> Unit> = dock.take(DOCK_SLOTS).map { app ->
        @Composable {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                    .press({ a.launch(app.pkg) }, onLongClick = { a.removeApp(app) }, label = app.label),
                contentAlignment = Alignment.Center,
            ) { AppIcon(app.pkg, app.label, 46.dp) }
        }
    } + List((DOCK_SLOTS - dock.size).coerceAtLeast(0)) { @Composable { AddSlot(a.addApp) } }
    // Home then Back, both plain icons (no tile), so they read as navigation rather than app spots
    val homeBack: @Composable () -> Unit = {
        BarIcon(ShIcons.home, "Home", false, onClick = a.home)
        BarIcon(ShIcons.backArrow, "Back", false, onClick = a.back)
        // Ambient lighting: the bulb glows in the lights' colour while they're on
        if (lights != null && openLights != null) BarIcon(ShIcons.bulb, "Ambient lighting", false, onClick = openLights,
            tint = if (lights.on) lightColor(lights.hue) else Sh.dim)
    }
    BarIcon(ShIcons.grid, "All apps", screen == Screen.APPS, onClick = a.apps)
    if (homeInMiddle) {
        slots.take(1).forEach { it() }
        homeBack()
        slots.drop(1).forEach { it() }
    } else {
        slots.forEach { it() }
        homeBack()
    }
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
private fun BarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, onColor: Color = Sh.ink, tint: Color? = null) {
    val bg by animateColorAsState(if (active) Sh.raised else Color.Transparent, tween(180), label = "bar")
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = label),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint ?: if (active) onColor else Sh.dim, 26.dp) }
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
                    Brush.horizontalGradient(listOf(Sh.cyan, Color(0xFF8E8EFF), Sh.orange), startX = left - trackH / 2, endX = left + width + trackH / 2),
                    Offset(left - trackH / 2, y - trackH / 2), Size(width + trackH, trackH), CornerRadius(trackH / 2),
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
private val SLIDER_W = 192.dp
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
                    // The strip runs half its height past the first and last speed, so the end dots sit inside it
                    Brush.horizontalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF3B6FD8), Sh.blue), startX = left - trackH / 2, endX = left + width + trackH / 2),
                    Offset(left - trackH / 2, y - trackH / 2), Size(width + trackH, trackH), CornerRadius(trackH / 2),
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

