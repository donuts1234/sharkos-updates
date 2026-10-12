package com.sharkos.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.R

/**
 * SharkOS colours: true black, soft grey cards, one blue for "on", orange for heat, cyan for cooling.
 * Close to iOS's dark system palette, chosen for night driving on the Shark's screen.
 */
object Sh {
    val bg = Color(0xFF000000)
    val card = Color(0xFF1C1C1E)
    val raised = Color(0xFF2C2C2E)
    val line = Color(0xFF3A3A3C)
    val ink = Color(0xFFF5F5F7)
    val dim = Color(0xFFA1A1A6)
    val faint = Color(0xFF636366)
    val blue = Color(0xFF0A84FF)
    val orange = Color(0xFFFF9F0A)
    val cyan = Color(0xFF64D2FF)
    val green = Color(0xFF30D158)
    val red = Color(0xFFFF453A)
    val bar = Color(0xFF111113)

    val font = FontFamily(
        Font(R.font.figtree_regular, FontWeight.Normal),
        Font(R.font.figtree_medium, FontWeight.Medium),
        Font(R.font.figtree_semibold, FontWeight.SemiBold),
        Font(R.font.figtree_bold, FontWeight.Bold),
    )

    fun text(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = ink, spacing: TextUnit = 0.sp) =
        TextStyle(
            fontFamily = font, fontSize = size, fontWeight = weight, color = color, letterSpacing = spacing,
            // tabular figures so changing numbers don't jiggle
            fontFeatureSettings = "tnum",
        )
}
