package com.sharkos.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Backgrounds, drawn in code rather than shipped as images: no megabytes of JPEGs, sharp at any size,
 * and drawn once per screen size (drawWithCache) then reused, so they cost nothing while driving.
 * Kept dark at the top and calm behind text; the colour sits low and to the sides.
 */
enum class Wallpaper(val label: String, internal val barTint: List<Color>) {
    BLACK("Black", emptyList()),
    GRAPHITE("Graphite", listOf(Color(0xFF4A4E5C), Color(0xFF2A2C33))),
    MIDNIGHT("Midnight", listOf(Color(0xFF1A4FB0), Color(0xFF3A2A8C))),
    CONTOUR("Contour", listOf(Color(0xFF3A3A40), Color(0xFF3A3A40))),
    AURORA("Aurora", listOf(Color(0xFF16A889), Color(0xFF2AD4C8), Color(0xFF7B4DFF))),
    OCEAN("Ocean", listOf(Color(0xFF0A4BB5), Color(0xFF0A84FF), Color(0xFF1C64D8))),
    SUNSET("Sunset", listOf(Color(0xFF8E3BD9), Color(0xFFE0457B), Color(0xFFFF7A3D))),
}

/** The background's colours, for the HVAC Bar to pick up a hint of. Provided once in Root. */
val LocalWallpaper = staticCompositionLocalOf { Wallpaper.BLACK }

/**
 * The HVAC Bar's own background: its usual near-black with a faint wash of the background's colours
 * across it and a soft line of them along its top edge. Black keeps the plain bar.
 */
fun Modifier.barTint(w: Wallpaper): Modifier = if (w.barTint.isEmpty()) this else drawWithCache {
    val wash = Brush.horizontalGradient(w.barTint.map { it.copy(alpha = 0.16f) })
    val edge = Brush.horizontalGradient(w.barTint.map { it.copy(alpha = 0.55f) })
    val line = 1.dp.toPx()
    onDrawBehind {
        drawRect(wash)
        drawRect(edge, size = Size(size.width, line))
    }
}

/** How round the app's outer corners are. */
enum class Corners(val label: String, val radius: Int) {
    OFF("Square", 0),
    LIGHT("Light", 18),
    SOFT("Soft", 30),
}

fun Modifier.wallpaper(w: Wallpaper): Modifier = drawWithCache {
    val draw: DrawScope.() -> Unit = when (w) {
        Wallpaper.BLACK -> ({ drawRect(Color.Black) })
        Wallpaper.GRAPHITE -> graphite(size)
        Wallpaper.MIDNIGHT -> midnight(size)
        Wallpaper.CONTOUR -> contour(size, 1.dp.toPx())
        Wallpaper.AURORA -> aurora(size)
        Wallpaper.OCEAN -> ocean(size)
        Wallpaper.SUNSET -> sunset(size)
    }
    onDrawBehind(draw)
}

// ---- The designs. Sizes are fractions of the screen so thumbnails match the real thing. ----

private fun graphite(s: Size): DrawScope.() -> Unit {
    val fade = Brush.verticalGradient(listOf(Color(0xFF1E1F24), Color(0xFF0B0B0D), Color.Black), 0f, s.height)
    val glow = glow(Offset(s.width * 0.82f, -s.height * 0.1f), s.width * 0.6f, Color(0xFF3A3D48), 0.55f)
    return { drawRect(fade); drawRect(glow) }
}

private fun midnight(s: Size): DrawScope.() -> Unit {
    val a = glow(Offset(s.width * 0.08f, s.height * 1.05f), s.width * 0.75f, Color(0xFF123C8C), 0.7f)
    val b = glow(Offset(s.width * 0.95f, -s.height * 0.05f), s.width * 0.55f, Color(0xFF2B1F6E), 0.55f)
    return { drawRect(Color(0xFF02030A)); drawRect(a); drawRect(b) }
}

private fun contour(s: Size, line: Float): DrawScope.() -> Unit {
    // Faint topographic lines: there, but never louder than the cards.
    val paths = (0 until 18).map { i ->
        val base = s.height * (0.08f + i * 0.055f)
        wave(s, base, s.height * (0.03f + 0.012f * (i % 4)), s.width * 0.9f, i * 0.45f, s.width * 0.37f, s.height * 0.015f, fill = false)
    }
    val stroke = Stroke(line)
    return {
        drawRect(Color.Black)
        paths.forEachIndexed { i, p -> drawPath(p, Color.White.copy(alpha = if (i % 5 == 2) 0.11f else 0.055f), style = stroke) }
    }
}

private fun aurora(s: Size): DrawScope.() -> Unit = layered(
    s, Color(0xFF020507),
    glows = listOf(
        Triple(Offset(s.width * 0.15f, s.height * 0.95f), s.width * 0.6f, Color(0xFF0E6B5C)),
        Triple(Offset(s.width * 0.85f, s.height * 0.2f), s.width * 0.5f, Color(0xFF3B1E7A)),
    ),
    waves = listOf(
        WaveSpec(0.50f, 0.10f, 1.30f, 0.0f, Color(0xFF7B4DFF), 0.35f),
        WaveSpec(0.60f, 0.08f, 0.95f, 1.7f, Color(0xFF1FBF9A), 0.40f),
        WaveSpec(0.72f, 0.07f, 1.10f, 3.1f, Color(0xFF2AD4C8), 0.32f),
        WaveSpec(0.84f, 0.05f, 0.80f, 4.4f, Color(0xFF0B8F7A), 0.45f),
    ),
)

private fun ocean(s: Size): DrawScope.() -> Unit = layered(
    s, Color(0xFF01040B),
    glows = listOf(Triple(Offset(s.width * 0.5f, -s.height * 0.2f), s.width * 0.7f, Color(0xFF0B2A5A))),
    waves = listOf(
        WaveSpec(0.48f, 0.07f, 1.40f, 0.6f, Color(0xFF0A4BB5), 0.35f),
        WaveSpec(0.60f, 0.06f, 1.00f, 2.2f, Color(0xFF0A84FF), 0.30f),
        WaveSpec(0.71f, 0.06f, 1.20f, 3.9f, Color(0xFF1C64D8), 0.40f),
        WaveSpec(0.82f, 0.05f, 0.85f, 5.0f, Color(0xFF0B3C8F), 0.55f),
        WaveSpec(0.91f, 0.04f, 1.10f, 0.9f, Color(0xFF64D2FF), 0.22f),
    ),
)

private fun sunset(s: Size): DrawScope.() -> Unit = layered(
    s, Color(0xFF07030A),
    glows = listOf(
        Triple(Offset(s.width * 0.9f, s.height * 1.0f), s.width * 0.55f, Color(0xFF8A2A1A)),
        Triple(Offset(s.width * 0.05f, s.height * 0.1f), s.width * 0.45f, Color(0xFF3A1250)),
    ),
    waves = listOf(
        WaveSpec(0.52f, 0.09f, 1.25f, 1.0f, Color(0xFF8E3BD9), 0.30f),
        WaveSpec(0.63f, 0.08f, 0.95f, 2.6f, Color(0xFFE0457B), 0.34f),
        WaveSpec(0.75f, 0.06f, 1.15f, 4.0f, Color(0xFFFF7A3D), 0.34f),
        WaveSpec(0.86f, 0.05f, 0.85f, 5.3f, Color(0xFFFF9F0A), 0.30f),
    ),
)

// ---- Building blocks ----

/** A wave crossing the screen at [base] (fraction of height) that fades out below its crest. */
private class WaveSpec(val base: Float, val amp: Float, val length: Float, val phase: Float, val color: Color, val alpha: Float)

private fun layered(s: Size, bg: Color, glows: List<Triple<Offset, Float, Color>>, waves: List<WaveSpec>): DrawScope.() -> Unit {
    val glowBrushes = glows.map { (c, r, col) -> glow(c, r, col, 0.6f) }
    val shapes = waves.map { w ->
        val base = s.height * w.base
        val amp = s.height * w.amp
        val path = wave(s, base, amp, s.width * w.length, w.phase, s.width * w.length * 0.43f, amp * 0.35f, fill = true)
        val brush = Brush.verticalGradient(
            listOf(w.color.copy(alpha = w.alpha), w.color.copy(alpha = w.alpha * 0.35f), Color.Transparent),
            startY = base - amp, endY = base + s.height * 0.35f,
        )
        path to brush
    }
    return {
        drawRect(bg)
        glowBrushes.forEach { drawRect(it) }
        shapes.forEach { (p, b) -> drawPath(p, b) }
    }
}

/** Two sines added, so the line never looks mechanical. Filled to the bottom edge, or an open line. */
private fun wave(s: Size, base: Float, amp: Float, length: Float, phase: Float, length2: Float, amp2: Float, fill: Boolean): Path {
    val p = Path()
    val step = (s.width / 96f).coerceAtLeast(2f)
    var x = 0f
    fun y(x: Float) = base + amp * sin(2 * PI * x / length + phase).toFloat() + amp2 * sin(2 * PI * x / length2 + phase * 1.7).toFloat()
    p.moveTo(0f, y(0f))
    while (x < s.width) { x = (x + step).coerceAtMost(s.width); p.lineTo(x, y(x)) }
    if (fill) { p.lineTo(s.width, s.height); p.lineTo(0f, s.height); p.close() }
    return p
}

private fun glow(center: Offset, radius: Float, color: Color, alpha: Float) =
    Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.3f), Color.Transparent), center, radius)
