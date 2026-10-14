package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.Airflow
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.Zone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class ClimateActions(
    val power: (Boolean) -> Unit,
    val temp: (Zone, Int) -> Unit,
    val fan: (Int) -> Unit,
    val airflow: (Airflow) -> Unit,
    val auto: (Boolean) -> Unit,
    val ac: (Boolean) -> Unit,
    val recirc: (Boolean) -> Unit,
    val frontDemist: (Boolean) -> Unit,
    val rearDemist: (Boolean) -> Unit,
    val dual: (Boolean) -> Unit,
    val seatHeat: (Zone, Int) -> Unit,
    val seatVent: (Zone, Int) -> Unit,
    val back: () -> Unit,
    /** Set a side's temperature outright (dragging its ring). */
    val setTemp: (Zone, Float) -> Unit = { _, _ -> },
)

/**
 * The climate page ("Dials"): each side's temperature in a ring you drag (passenger left, driver
 * right: right-hand drive), its seat heating and cooling under it; fan and airflow in the middle;
 * the switches in a row along the bottom.
 */
@Composable
fun ClimateScreen(c: ClimateState, a: ClimateActions, outsideC: Double? = null) {
    Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Sh.card).press(a.back, label = "Back to Home")
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(ShIcons.chevronLeft, Sh.blue, 20.dp)
                Txt("Home", Sh.text(16.sp, FontWeight.SemiBold, Sh.blue))
            }
            Txt("Climate", Sh.text(30.sp, FontWeight.Bold, spacing = (-0.5).sp))
            outsideC?.let {
                Box(Modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(Sh.card).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Txt("${it.roundToInt()}° outside", Sh.text(14.sp, color = Sh.dim))
                }
            }
            Box(Modifier.weight(1f))
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Sh.card).press({ a.power(!c.power) }, label = "Climate power")
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Glyph(ShIcons.power, if (c.power) Sh.green else Sh.faint, 18.dp)
                Txt(if (c.power) "On" else "Off", Sh.text(16.sp, FontWeight.SemiBold))
            }
        }
        val dim = Modifier.alpha(if (c.power) 1f else 0.4f)
        Row(Modifier.weight(1f).fillMaxWidth().then(dim), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SidePanel(Zone.PASSENGER, c, a, Modifier.weight(1f).fillMaxHeight())
            AirPanel(c, a, Modifier.weight(1.05f).fillMaxHeight())
            SidePanel(Zone.DRIVER, c, a, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(56.dp).then(dim), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch6("Auto", ShIcons.auto, c.auto, Sh.blue) { a.auto(!c.auto) }
            Switch6("A/C", ShIcons.snow, c.ac, Sh.blue) { a.ac(!c.ac) }
            Switch6("Dual", ShIcons.dual, c.dual, Sh.blue) { a.dual(!c.dual) }
            Switch6("Recirc", ShIcons.recirc, c.recirc, Sh.blue) { a.recirc(!c.recirc) }
            Switch6("Windscreen", ShIcons.demistFront, c.frontDefrost, Sh.orange) { a.frontDemist(!c.frontDefrost) }
            Switch6("Rear demist", ShIcons.demistRear, c.rearDefrost, Sh.orange) { a.rearDemist(!c.rearDefrost) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Switch6(label: String, icon: ImageVector, on: Boolean, color: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (on) color else Sh.raised, tween(150), label = label)
    Row(
        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(bg).press(onClick, label = label),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Glyph(icon, if (on && color == Sh.orange) Color.Black else Sh.ink, 20.dp)
        Txt(label, Sh.text(15.sp, FontWeight.SemiBold, if (on && color == Sh.orange) Color.Black else Sh.ink))
    }
}

/** One side: its temperature ring, then seat heating and cooling. */
@Composable
private fun SidePanel(zone: Zone, c: ClimateState, a: ClimateActions, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(28.dp)).background(Sh.card).padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
) {
    TempRing(zone, if (c.power) c.temp(zone) else null, { a.setTemp(zone, it) }, Modifier.weight(1f, fill = false).fillMaxWidth().aspectRatio(1f, matchHeightConstraintsFirst = true))
    val seat = c.seat(zone)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SeatChip("${zone.label} seat heat", "Heat", ShIcons.flame, seat.heat, Sh.orange) { a.seatHeat(zone, (seat.heat + 1) % (ClimateState.MAX_SEAT_LEVEL + 1)) }
        SeatChip("${zone.label} seat cooling", "Cool", ShIcons.vent, seat.vent, Sh.cyan) { a.seatVent(zone, (seat.vent + 1) % (ClimateState.MAX_SEAT_LEVEL + 1)) }
    }
}

private val RING = listOf(Color(0xFF64D2FF), Color(0xFFBF5AF2), Color(0xFFFF9F0A))

/**
 * The temperature ring: 270° from bottom-left round to bottom-right, cool to warm. Drag or tap
 * anywhere on it; whole degrees, sent once the finger settles (the car side debounces).
 */
@Composable
private fun TempRing(zone: Zone, temp: Float?, onSet: (Float) -> Unit, modifier: Modifier) {
    val set by rememberUpdatedState(onSet)
    val min = ClimateState.MIN_TEMP; val max = ClimateState.MAX_TEMP
    val frac = ((temp ?: min) - min) / (max - min)
    fun pick(p: Offset, w: Float, h: Float) {
        val deg = Math.toDegrees(atan2((p.y - h / 2).toDouble(), (p.x - w / 2).toDouble())).toFloat()
        var rel = (deg - 135f + 720f) % 360f
        if (rel > 270f) rel = if (rel > 315f) 0f else 270f
        set((min + rel / 270f * (max - min)).roundToInt().toFloat())
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize()
                .pointerInput(zone) { detectTapGestures { pick(it, size.width.toFloat(), size.height.toFloat()) } }
                .pointerInput(zone) { detectDragGestures { ch, _ -> pick(ch.position, size.width.toFloat(), size.height.toFloat()) } },
        ) {
            val stroke = size.minDimension * 0.075f
            val r = size.minDimension / 2 - stroke
            val tl = Offset(center.x - r, center.y - r)
            val box = Size(r * 2, r * 2)
            drawArc(Sh.raised, 135f, 270f, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
            if (temp != null) {
                val brush = Brush.linearGradient(RING, start = Offset(center.x - r, center.y + r), end = Offset(center.x + r, center.y - r))
                drawArc(brush, 135f, (270f * frac).coerceAtLeast(0.5f), false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
                val ang = Math.toRadians((135f + 270f * frac).toDouble())
                val knob = Offset(center.x + r * cos(ang).toFloat(), center.y + r * sin(ang).toFloat())
                drawCircle(Color.Black.copy(alpha = 0.3f), stroke * 0.95f, knob)
                drawCircle(Color.White, stroke * 0.8f, knob)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(zone.label.uppercase(), Sh.text(13.sp, FontWeight.SemiBold, Sh.dim, 0.4.sp))
            Txt(temp?.let { "${it.toInt()}°" } ?: "—", Sh.text(64.sp, FontWeight.Bold, spacing = (-2.5).sp))
            Txt("Drag the ring", Sh.text(12.sp, color = Sh.faint))
        }
    }
}

/** Seat heat or cooling: one tap steps Off → Low → High → Off; the two bars show the level. */
@Composable
private fun SeatChip(label: String, text: String, icon: ImageVector, level: Int, color: Color, onClick: () -> Unit) {
    val on = level > 0
    val bg by animateColorAsState(if (on) color else Sh.raised, tween(150), label = label)
    val fg = if (on) Color.Black else Sh.ink
    Column(
        Modifier.width(110.dp).height(54.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = label),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Glyph(icon, fg, 16.dp)
            Txt(text, Sh.text(14.sp, FontWeight.Bold, fg))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(ClimateState.MAX_SEAT_LEVEL) { i ->
                Box(Modifier.width(13.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (on) (if (i < level) Color.Black else Color.Black.copy(alpha = 0.25f)) else Sh.line))
            }
        }
    }
}

/** Fan as rising bars (tap one to jump to it) and the four airflow choices. */
@Composable
private fun AirPanel(c: ClimateState, a: ClimateActions, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(28.dp)).background(Sh.card).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SectionLabel("Fan", Modifier.weight(1f))
        if (c.auto) Txt("Auto  ", Sh.text(14.sp, FontWeight.SemiBold, Sh.blue))
        Txt(if (c.power) "${c.fan}" else "Off", Sh.text(32.sp, FontWeight.Bold))
        if (c.power) Txt(" / ${ClimateState.MAX_FAN}", Sh.text(16.sp, color = Sh.dim), Modifier.padding(bottom = 4.dp))
    }
    Row(Modifier.fillMaxWidth().height(76.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        for (n in 1..ClimateState.MAX_FAN) {
            val lit = c.power && n <= c.fan
            val bg by animateColorAsState(if (lit) Sh.blue else Sh.raised, tween(150), label = "fan$n")
            Box(Modifier.weight(1f).fillMaxHeight(0.3f + 0.7f * n / ClimateState.MAX_FAN).clip(RoundedCornerShape(8.dp)).background(bg).press({ a.fan(n) }, label = "Fan $n"))
        }
    }
    SectionLabel("Airflow")
    val icons = mapOf(Airflow.FACE to ShIcons.airFace, Airflow.FACE_FEET to ShIcons.airFaceFeet, Airflow.FEET to ShIcons.airFeet, Airflow.FEET_SCREEN to ShIcons.airFeetScreen)
    for (row in Airflow.entries.chunked(2)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (f in row) {
                val on = c.airflow == f
                val bg by animateColorAsState(if (on) Sh.blue else Sh.raised, tween(150), label = f.name)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(bg).press({ a.airflow(f) }, label = f.label),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Glyph(icons.getValue(f), Sh.ink, 30.dp)
                    Txt(f.label, Sh.text(14.sp, FontWeight.SemiBold))
                }
            }
        }
    }
}

