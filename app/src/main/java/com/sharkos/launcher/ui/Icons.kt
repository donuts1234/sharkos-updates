package com.sharkos.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Line icons drawn from SVG path data on a 24-unit grid (the same paths as the design mockups).
 * Kept in code instead of an icon library, which would add megabytes for a couple of dozen glyphs.
 * Strokes and fills are black here and tinted where they're drawn.
 */
object ShIcons {
    private fun icon(name: String, stroke: List<String> = emptyList(), fill: List<String> = emptyList(), width: Float = 2f) =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            for (d in fill) addPath(addPathNodes(d), fill = SolidColor(Color.Black))
            for (d in stroke) addPath(
                addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }.build()

    val home = icon("home", listOf("M3 10.5 12 3l9 7.5", "M5.5 9v11h13V9", "M10 20v-6h4v6"))
    val grid = icon("grid", listOf(
        "M4.8 3h3.4A1.8 1.8 0 0 1 10 4.8v3.4A1.8 1.8 0 0 1 8.2 10H4.8A1.8 1.8 0 0 1 3 8.2V4.8A1.8 1.8 0 0 1 4.8 3z",
        "M15.8 3h3.4A1.8 1.8 0 0 1 21 4.8v3.4a1.8 1.8 0 0 1-1.8 1.8h-3.4A1.8 1.8 0 0 1 14 8.2V4.8A1.8 1.8 0 0 1 15.8 3z",
        "M4.8 14h3.4a1.8 1.8 0 0 1 1.8 1.8v3.4A1.8 1.8 0 0 1 8.2 21H4.8A1.8 1.8 0 0 1 3 19.2v-3.4A1.8 1.8 0 0 1 4.8 14z",
        "M15.8 14h3.4a1.8 1.8 0 0 1 1.8 1.8v3.4a1.8 1.8 0 0 1-1.8 1.8h-3.4a1.8 1.8 0 0 1-1.8-1.8v-3.4a1.8 1.8 0 0 1 1.8-1.8z",
    ))
    val fan = icon("fan", listOf(
        "M12 10a2 2 0 1 1 0 4a2 2 0 1 1 0-4z",
        "M12 10c0-4 1-7 4-7s3 4-2 7M14 12c4 0 7 1 7 4s-4 3-7-2M12 14c0 4-1 7-4 7s-3-4 2-7M10 12c-4 0-7-1-7-4s4-3 7 2",
    ))
    val snow = icon("snow", listOf("M12 2v20", "M4.9 4.9l14.2 14.2", "M2 12h20", "M4.9 19.1 19.1 4.9"))
    val flame = icon("flame", listOf("M12 22c4 0 7-3 7-7 0-5-5-7-5-12-3 2-4 5-4 8-1-1-2-2-2-4-2 2-3 5-3 8 0 4 3 7 7 7z"))
    val vent = icon("vent", listOf("M3 8h11a3 3 0 1 0-3-3", "M3 16h15a3 3 0 1 1-3 3", "M3 12h18"))
    val recirc = icon("recirc", listOf("M3 12a9 9 0 0 1 15-6.7L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-15 6.7L3 16", "M3 21v-5h5"))
    val demistFront = icon("demistFront", listOf("M3 8c6-3 12-3 18 0l-2 10H5z", "M9 11c-1 1 1 2 0 3", "M12 10.5c-1 1 1 2 0 3", "M15 11c-1 1 1 2 0 3"))
    val demistRear = icon("demistRear", listOf("M4 6h16v12H4z", "M8 9.5c-1 1 1 2 0 3", "M12 9.5c-1 1 1 2 0 3", "M16 9.5c-1 1 1 2 0 3"))
    val power = icon("power", listOf("M12 3v8", "M6.3 6.3a8 8 0 1 0 11.4 0"))
    val back = icon("back", listOf("M15 5 8 12l7 7"), width = 2.4f)
    val chevronLeft = icon("chevronLeft", listOf("M14.5 6 8.5 12l6 6"), width = 2.6f)
    val chevronRight = icon("chevronRight", listOf("M9.5 6l6 6-6 6"), width = 2.6f)
    val minus = icon("minus", listOf("M5 12h14"), width = 2.4f)
    val plus = icon("plus", listOf("M12 5v14", "M5 12h14"), width = 2.4f)
    val play = icon("play", fill = listOf("M7 4.5v15l12.5-7.5z"))
    val pause = icon("pause", fill = listOf(
        "M6.9 4h1.7a1.4 1.4 0 0 1 1.4 1.4v13.2A1.4 1.4 0 0 1 8.6 20H6.9a1.4 1.4 0 0 1-1.4-1.4V5.4A1.4 1.4 0 0 1 6.9 4z",
        "M15.4 4h1.7a1.4 1.4 0 0 1 1.4 1.4v13.2a1.4 1.4 0 0 1-1.4 1.4h-1.7a1.4 1.4 0 0 1-1.4-1.4V5.4A1.4 1.4 0 0 1 15.4 4z",
    ))
    val previous = icon("previous", fill = listOf("M19 19.5 9.5 12 19 4.5z", "M5 4.5h2.6v15H5z"))
    val next = icon("next", fill = listOf("M5 4.5 14.5 12 5 19.5z", "M16.4 4.5H19v15h-2.6z"))
    val music = icon("music", listOf("M9 18V5l12-2v13", "M6 15a3 3 0 1 1 0 6a3 3 0 1 1 0-6z", "M18 13a3 3 0 1 1 0 6a3 3 0 1 1 0-6z"), width = 1.6f)
    val mountain = icon("mountain", listOf("m8 3 4 8 5-5 5 15H2L8 3z"))
    val gear = icon("gear", listOf(
        "M12 9a3 3 0 1 1 0 6a3 3 0 1 1 0-6z",
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    ))
    val cloud = icon("cloud", listOf("M17.5 19H9a7 7 0 1 1 6.7-9h1.8a4.5 4.5 0 0 1 0 9z"))
    val bolt = icon("bolt", listOf("M13 2 3 14h9l-1 8 10-12h-9l1-8z"))
    val fuel = icon("fuel", listOf("M3 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18", "M3 22h12", "M7 7h4", "M15 10h2a2 2 0 0 1 2 2v5a1.5 1.5 0 0 0 3 0V8l-3-3"))
    val car = icon("car", listOf("M2.5 16v-3.5L5 7.5h8.5l3.5 5h4.5V16", "M2.5 16h1.5M9 16h6M20 16h1.5", "M6.5 14a2 2 0 1 1 0 4a2 2 0 1 1 0-4z", "M17.5 14a2 2 0 1 1 0 4a2 2 0 1 1 0-4z", "M9.5 7.5v5h7.5"))
    val info = icon("info", listOf("M12 3a9 9 0 1 1 0 18a9 9 0 1 1 0-18z", "M12 11v5.5", "M12 7.6v.01"), width = 2.2f)
    val download = icon("download", listOf("M12 3.5v11", "M7 10l5 5 5-5", "M4.5 20h15"))
    val chip = icon("chip", listOf(
        "M8 6h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z", "M10 10h4v4h-4z",
        "M9.5 2.5V6M14.5 2.5V6M9.5 18v3.5M14.5 18v3.5M2.5 9.5H6M2.5 14.5H6M18 9.5h3.5M18 14.5h3.5",
    ))
    val brush = icon("brush", listOf("M12 3a9 9 0 1 0 0 18c1.2 0 2-.8 2-1.8 0-.5-.2-.9-.5-1.2-.3-.3-.5-.7-.5-1.2 0-1 .8-1.8 1.8-1.8H17a4 4 0 0 0 4-4c0-4.4-4-8-9-8z", "M7.5 11.5v.01M9.5 7.5v.01M14.5 7.5v.01M17 11v.01"), width = 2.2f)
    val bulb = icon("bulb", listOf("M9 18h6", "M10 21h4", "M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2V16h5v-.1c0-.8.4-1.5 1-2A6 6 0 0 0 12 3z"))
    val backArrow = icon("backArrow", listOf("M9 14 4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11"))
    val sun = icon("sun", listOf("M12 8a4 4 0 1 1 0 8a4 4 0 1 1 0-8z", "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4"))
}
