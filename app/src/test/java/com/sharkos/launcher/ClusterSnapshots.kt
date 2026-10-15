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
import androidx.compose.foundation.layout.fillMaxSize
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
    private fun design(s: com.sharkos.launcher.ui.ClusterStyle) = paparazzi.snapshot {
        ClusterLayout(track, 0, tyres, TyreLimits(), s, com.sharkos.launcher.car.Drive(km = 23.4, minutes = 32, avgKph = 46.0, evShare = 0.82))
    }
    @Test fun clusterStage() = design(com.sharkos.launcher.ui.ClusterStyle.STAGE)
    @Test fun clusterVinyl() = design(com.sharkos.launcher.ui.ClusterStyle.VINYL)
    @Test fun clusterField() = design(com.sharkos.launcher.ui.ClusterStyle.FIELD)
    @Test fun clusterBackdrop() = design(com.sharkos.launcher.ui.ClusterStyle.BACKDROP)
    @Test fun clusterTiles() = design(com.sharkos.launcher.ui.ClusterStyle.TILES)
    @Test fun clusterNight() = design(com.sharkos.launcher.ui.ClusterStyle.NIGHT)
    private val guide = com.sharkos.launcher.car.CarPlayLink.RouteGuide(2, "300", "m", "Main Rd", "14", "km", 18 * 60, 1, "Turn right onto Main Rd")
    @Test fun clusterMapCentre() = paparazzi.snapshot {
        com.sharkos.launcher.ui.ClusterDisplay.previewMap = true
        com.sharkos.launcher.ui.MapLayoutView(com.sharkos.launcher.ui.MapLayout.CENTRE, track, guide, tyres, TyreLimits()) { com.sharkos.launcher.ui.CarPlayMapView(androidx.compose.ui.Modifier.fillMaxSize()) }
    }
    @Test fun clusterMapWide() = paparazzi.snapshot {
        com.sharkos.launcher.ui.ClusterDisplay.previewMap = true
        com.sharkos.launcher.ui.MapLayoutView(com.sharkos.launcher.ui.MapLayout.WIDE, track, guide, tyres, TyreLimits()) { com.sharkos.launcher.ui.CarPlayMapView(androidx.compose.ui.Modifier.fillMaxSize()) }
    }
    @Test fun clusterNothingPlaying() = paparazzi.snapshot { ClusterLayout(null, 0, List(4) { null }, TyreLimits()) }
}
