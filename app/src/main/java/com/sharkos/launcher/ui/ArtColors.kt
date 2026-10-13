package com.sharkos.launcher.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color

/** Colours taken from album art: [accent] for the controls, [shade] for the dark wash under the text. */
data class ArtColors(val accent: Color, val onAccent: Color, val shade: Color)

/**
 * The art's most characterful colour, found by shrinking it to 24×24 and voting by hue, with each
 * pixel's vote weighted by how vivid it is. Brightened so it reads on the dark card; art with no real
 * colour (black and white covers) gets soft white. A few hundred pixels: well under a millisecond,
 * run once per song, no palette library needed.
 */
fun artColors(art: Bitmap): ArtColors {
    val small = runCatching { Bitmap.createScaledBitmap(art, 24, 24, true) }.getOrNull() ?: return NEUTRAL
    val bins = 24
    val weight = FloatArray(bins)
    val sumR = FloatArray(bins); val sumG = FloatArray(bins); val sumB = FloatArray(bins)
    var avgR = 0f; var avgG = 0f; var avgB = 0f
    val hsv = FloatArray(3)
    for (y in 0 until small.height) for (x in 0 until small.width) {
        val c = small.getPixel(x, y)
        val r = (c shr 16 and 0xFF); val g = (c shr 8 and 0xFF); val b = (c and 0xFF)
        avgR += r; avgG += g; avgB += b
        android.graphics.Color.RGBToHSV(r, g, b, hsv)
        val (h, s, v) = Triple(hsv[0], hsv[1], hsv[2])
        if (v < 0.2f || s < 0.18f) continue                 // near-black and greys don't vote
        val w = s * s * v                                    // vivid wins over merely bright
        val i = ((h / 360f) * bins).toInt().coerceIn(0, bins - 1)
        weight[i] += w; sumR[i] += r * w; sumG[i] += g * w; sumB[i] += b * w
    }
    val n = small.width * small.height
    if (small !== art) small.recycle()
    val shade = toned(Color(avgR / n / 255f, avgG / n / 255f, avgB / n / 255f), value = 0.16f, maxSat = 0.6f)
    // Neighbouring hues count together, so a cover in two close oranges isn't split
    val best = (0 until bins).maxByOrNull { weight[it] + 0.5f * (weight[(it + 1) % bins] + weight[(it + bins - 1) % bins]) } ?: return NEUTRAL.copy(shade = shade)
    if (weight[best] < 2f) return NEUTRAL.copy(shade = shade)
    val w = weight[best]
    val accent = toned(Color(sumR[best] / w / 255f, sumG[best] / w / 255f, sumB[best] / w / 255f), value = 0.95f, maxSat = 0.7f, minValue = true)
    return ArtColors(accent, if (accent.luminance() > 0.45f) Color.Black else Color.White, shade)
}

private val NEUTRAL = ArtColors(Sh.ink, Color.Black, Color(0xFF141416))

/** Same hue, with brightness set to [value] (or at least it when [minValue]) and saturation capped. */
private fun toned(c: Color, value: Float, maxSat: Float, minValue: Boolean = false): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    hsv[1] = hsv[1].coerceAtMost(maxSat)
    hsv[2] = if (minValue) hsv[2].coerceAtLeast(value) else value
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
