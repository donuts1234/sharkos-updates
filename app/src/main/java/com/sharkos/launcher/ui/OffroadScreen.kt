package com.sharkos.launcher.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.sensors.Attitude
import kotlin.math.abs

/**
 * Pitch and roll from the head unit's motion sensor, each on a dial with a simple ute that tips.
 * Amber past 20°, red past 30°: rough guides only; the car's own limits are in its handbook.
 */
@Composable
fun OffroadScreen(attitude: Attitude?, calibrated: Boolean, onZero: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).press(onBack, label = "Back to Home"), verticalAlignment = Alignment.CenterVertically) {
                Glyph(ShIcons.back, Sh.blue, 22.dp)
                Txt("Home", Sh.text(19.sp, FontWeight.Medium, Sh.blue))
            }
            Txt("Off-road", Sh.text(24.sp, FontWeight.Bold, spacing = (-0.3).sp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                PillButton(if (calibrated) "Re-zero" else "Zero on level ground", onZero)
            }
        }
        if (attitude == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Txt("No motion sensor on this unit", Sh.text(20.sp, FontWeight.SemiBold, Sh.dim))
            }
            return@Column
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Dial("Pitch", attitude.pitch, if (attitude.pitch >= 0) "Nose up" else "Nose down", side = true, Modifier.weight(1f).fillMaxHeight())
            Dial("Roll", attitude.roll, if (attitude.roll >= 0) "Right side down" else "Left side down", side = false, Modifier.weight(1f).fillMaxHeight())
        }
        if (!calibrated) {
            Txt("Park on flat ground and tap Zero so the readings match how the screen is mounted.", Sh.text(15.sp, color = Sh.dim))
        }
    }
}

private fun severity(v: Float): Color = when {
    abs(v) >= 30f -> Sh.red
    abs(v) >= 20f -> Sh.orange
    else -> Sh.green
}

@Composable
private fun Dial(title: String, value: Float, caption: String, side: Boolean, modifier: Modifier) = Panel(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(title, Modifier.weight(1f))
        Txt(caption, Sh.text(15.sp, color = Sh.dim))
    }
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        val color = severity(value)
        Canvas(Modifier.fillMaxHeight().aspectRatio(1f)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Sh.raised, r, c)
            drawCircle(Sh.line, r, c, style = Stroke(2.dp.toPx()))
            // ticks every 10°
            for (d in -60..60 step 10) {
                rotate(d.toFloat(), c) {
                    val long = d % 30 == 0
                    drawLine(if (d == 0) Sh.ink else Sh.faint, Offset(c.x, c.y - r + 8.dp.toPx()), Offset(c.x, c.y - r + (if (long) 22 else 14).dp.toPx()),
                        strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                }
            }
            rotate(if (side) -value else value, c) {
                // horizon line
                drawLine(color, Offset(c.x - r * 0.82f, c.y), Offset(c.x + r * 0.82f, c.y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                val w = r * 0.9f
                if (side) drawUteSide(c, w, Sh.ink) else drawUteRear(c, w * 0.62f, Sh.ink)
            }
        }
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(deg(value), Sh.text(64.sp, FontWeight.SemiBold, color, (-2).sp))
        }
    }
}

/** A plain side-on ute, nose to the right, sitting on the horizon line. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUteSide(c: Offset, w: Float, color: Color) {
    val h = w * 0.22f
    val base = c.y - w * 0.05f
    val left = c.x - w / 2
    val p = Path().apply {
        moveTo(left, base)
        lineTo(left, base - h * 0.75f)                       // tailgate
        lineTo(left + w * 0.42f, base - h * 0.75f)            // tray rail
        lineTo(left + w * 0.45f, base - h * 1.55f)            // rear of cab
        lineTo(left + w * 0.68f, base - h * 1.55f)            // roof
        lineTo(left + w * 0.80f, base - h * 0.85f)            // windscreen
        lineTo(left + w, base - h * 0.7f)                      // bonnet
        lineTo(left + w, base)
        close()
    }
    drawPath(p, color, style = Stroke(2.5.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
    for (x in listOf(left + w * 0.2f, left + w * 0.8f)) {
        drawCircle(Sh.card, h * 0.42f, Offset(x, base))
        drawCircle(color, h * 0.42f, Offset(x, base), style = Stroke(2.5.dp.toPx()))
    }
}

/** A plain rear view standing on the horizon line: two tyres, the tray, the cab above it. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUteRear(c: Offset, w: Float, color: Color) {
    val stroke = Stroke(2.5.dp.toPx())
    val ground = c.y
    val tyreH = w * 0.26f
    val tyreW = w * 0.17f
    for (x in listOf(c.x - w * 0.36f, c.x + w * 0.36f)) {
        drawRoundRect(Sh.raised, Offset(x - tyreW / 2, ground - tyreH), Size(tyreW, tyreH), CornerRadius(5.dp.toPx()))
        drawRoundRect(color, Offset(x - tyreW / 2, ground - tyreH), Size(tyreW, tyreH), CornerRadius(5.dp.toPx()), style = stroke)
    }
    val trayTop = ground - tyreH * 0.6f - w * 0.34f
    drawRoundRect(Sh.raised, Offset(c.x - w / 2, trayTop), Size(w, w * 0.34f), CornerRadius(6.dp.toPx()))
    drawRoundRect(color, Offset(c.x - w / 2, trayTop), Size(w, w * 0.34f), CornerRadius(6.dp.toPx()), style = stroke)
    drawRoundRect(color, Offset(c.x - w * 0.34f, trayTop - w * 0.3f), Size(w * 0.68f, w * 0.3f), CornerRadius(10.dp.toPx()), style = stroke)
}
