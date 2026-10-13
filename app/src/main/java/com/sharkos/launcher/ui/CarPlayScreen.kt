package com.sharkos.launcher.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sharkos.launcher.car.CarPlayLink
import kotlinx.coroutines.delay

/**
 * CarPlay inside SharkOS (test version): the CarPlay picture drawn into SharkOS through the car's own
 * CarPlay service, the same hooks BYD's CarPlay screen uses, with the HVAC Bar still below. Touches are
 * scaled to CarPlay's 1920 × 906 picture. The line at the bottom reports what the car says, for testing.
 */
@Composable
fun CarPlayScreen(onHome: () -> Unit) {
    val ctx = LocalContext.current
    var report by remember { mutableStateOf("Starting…") }
    var surfaceReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            val shown = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { CarPlayLink.displayShowing() }
            report = "CarPlay link ${if (CarPlayLink.linkedNow) "on" else "OFF"} · surface ${if (surfaceReady) "given" else "not yet"} · " +
                "car says picture ${when (shown) { true -> "showing"; false -> "not showing"; null -> "unknown" }}"
            delay(1500)
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().aspectRatio(CarPlayLink.VIDEO_W / CarPlayLink.VIDEO_H.toFloat()).align(Alignment.Center)) {
            if (!LocalInspectionMode.current) {
                AndroidView(
                    factory = { c -> carPlaySurface(c) { ready -> surfaceReady = ready } },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF101418)), contentAlignment = Alignment.Center) {
                    Txt("CarPlay", Sh.text(28.sp, FontWeight.SemiBold, Sh.faint))
                }
            }
        }
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("‹ Home", onHome)
            Chip("Open in BYD CarPlay") {
                val i = ctx.packageManager.getLaunchIntentForPackage("com.ts.carplay.app")
                    ?: Intent().setClassName("com.ts.carplay.app", "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity")
                runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            Chip("Ask iPhone to show CarPlay") { CarPlayLink.requestUi(0) }
        }
        Txt(report, Sh.text(13.sp, FontWeight.Medium, Color.White.copy(alpha = 0.75f)),
            Modifier.align(Alignment.BottomStart).padding(16.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) = Box(
    Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.55f)).press(onClick, label = text).padding(horizontal = 16.dp),
    contentAlignment = Alignment.Center,
) { Txt(text, Sh.text(15.sp, FontWeight.SemiBold, Color.White)) }

/** The SurfaceView CarPlay draws into; hands its surface to the car and passes touches back, scaled. */
@SuppressLint("ClickableViewAccessibility")
private fun carPlaySurface(c: android.content.Context, onReady: (Boolean) -> Unit): SurfaceView {
    val v = SurfaceView(c)
    v.holder.addCallback(object : SurfaceHolder.Callback {
        override fun surfaceCreated(h: SurfaceHolder) {}
        override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, hgt: Int) {
            val ok = CarPlayLink.showOn(h.surface)
            CarPlayLink.screenToCarPlay(true)
            onReady(ok)
        }
        override fun surfaceDestroyed(h: SurfaceHolder) {
            CarPlayLink.showOn(null)
            CarPlayLink.screenToCarPlay(false)
            onReady(false)
        }
    })
    v.setOnTouchListener { view, e ->
        if (view.width > 0 && view.height > 0) {
            val scaled = MotionEvent.obtain(e)
            scaled.transform(Matrix().apply { setScale(CarPlayLink.VIDEO_W / view.width.toFloat(), CarPlayLink.VIDEO_H / view.height.toFloat()) })
            CarPlayLink.touch(scaled)
            scaled.recycle()
        }
        true
    }
    return v
}
