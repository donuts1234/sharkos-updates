package com.sharkos.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

/** The launcher icon at a few sizes and mask shapes, on dark and light, to judge it by eye. */
class IconPreview {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 2400, screenHeight = 1040, xdpi = 320, ydpi = 320, density = com.android.resources.Density.XHIGH, orientation = com.android.resources.ScreenOrientation.LANDSCAPE))

    @Composable
    private fun Icon(size: Dp, shape: Shape) = Box(Modifier.size(size).clip(shape)) {
        // An adaptive icon shows the middle 72/108 of each layer
        val full = size * 1.5f
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(full), contentScale = ContentScale.FillBounds)
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(full), contentScale = ContentScale.FillBounds)
        }
    }

    @Test fun icon() = paparazzi.snapshot {
        Row(Modifier.fillMaxSize()) {
            for (bg in listOf(Color.Black, Color(0xFFF2F2F7))) {
                Row(Modifier.background(bg).size(600.dp, 520.dp), horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    Icon(180.dp, RoundedCornerShape(42.dp))
                    Icon(96.dp, RoundedCornerShape(22.dp))
                    Icon(56.dp, CircleShape)
                }
            }
        }
    }
}
