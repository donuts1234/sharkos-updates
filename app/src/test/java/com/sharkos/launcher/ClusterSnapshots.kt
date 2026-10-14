package com.sharkos.launcher

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.sharkos.launcher.car.Tyre
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.media.Track
import com.sharkos.launcher.ui.ClusterLayout
import org.junit.Rule
import org.junit.Test

/** The driver display (instrument cluster): 1920 × 720 at 213 dpi. */
class ClusterSnapshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1920, screenHeight = 720, xdpi = 213, ydpi = 213,
            density = Density.create(213), orientation = ScreenOrientation.LANDSCAPE,
        ),
        theme = "android:Theme.DeviceDefault.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private fun art(): Bitmap = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).also { b ->
        val c = android.graphics.Canvas(b)
        c.drawRect(0f, 0f, 400f, 400f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 400f, intArrayOf(0xFFFFC46B.toInt(), 0xFFFF6A4D.toInt(), 0xFF7A1F5C.toInt()), null, Shader.TileMode.CLAMP) })
        c.drawCircle(230f, 175f, 70f, Paint().apply { color = 0xFFFFE3A3.toInt() })
    }

    private val track get() = Track("Somebody's Everything", "Sadie Jean", "CarPlay", art(), true, 84_000, 206_000, 0, carPlay = true)
    private val tyres = listOf(Tyre(38.0, 0), Tyre(38.0, 0), Tyre(40.0, 0), Tyre(40.0, 0))

    @Test fun clusterPanel() = paparazzi.snapshot { ClusterLayout(track, 0, tyres, TyreLimits()) }
    @Test fun clusterTyreLow() = paparazzi.snapshot { ClusterLayout(track, 0, tyres.toMutableList().also { it[2] = Tyre(29.0, 0) }, TyreLimits()) }
    @Test fun clusterCard() = paparazzi.snapshot { ClusterLayout(track, 0, tyres, TyreLimits(), com.sharkos.launcher.ui.ClusterStyle.CARD) }
    @Test fun clusterNothingPlaying() = paparazzi.snapshot { ClusterLayout(null, 0, List(4) { null }, TyreLimits()) }
}
