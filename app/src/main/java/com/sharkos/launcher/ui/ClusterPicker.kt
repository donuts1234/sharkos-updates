package com.sharkos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharkos.launcher.car.CarPlayLink
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.media.NowPlaying

/**
 * A design as a small live preview: the part of the cluster picture the car actually shows (its
 * window), drawn at [width] by giving the design a density that makes 1920 cluster pixels that wide.
 */
@Composable
fun ClusterThumb(style: ClusterStyle, width: Dp) {
    val cp by CarPlayLink.song.collectAsStateWithLifecycle()
    val session by NowPlaying.track.collectAsStateWithLifecycle()
    val track = ClusterDisplay.previewTrack ?: chooseTrack(cp, session)
    val ctx = LocalContext.current
    val limits = remember { runCatching { Prefs(ctx).let { TyreLimits(it.tyreLow, it.tyreHigh) } }.getOrDefault(TyreLimits()) }
    val dens = LocalDensity.current
    val scale = with(dens) { width.toPx() } / 1920f
    val winH = with(dens) { (425 * scale).toDp() }
    val full = with(dens) { DpSize(1920.toDp(), 720.toDp()) }
    // Laid out at the cluster's real 1920 × 720 pixels, then shrunk: only the car's window shows
    Box(Modifier.size(width, winH).clip(RoundedCornerShape(12.dp)).clipToBounds()) {
        Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(full)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
                translationY = -175f * scale
            }) {
            ClusterLayout(track, android.os.SystemClock.elapsedRealtime(), ClusterDisplay.liveTyres.value, limits, style, ClusterDisplay.liveDrive.value)
        }
    }
}

/** Every design as a preview: tap one to put it on the cluster. */
@Composable
fun ClusterGallery(columns: Int, thumbWidth: Dp) {
    val ctx = LocalContext.current
    val chosen = ClusterDisplay.style.value
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in ClusterStyle.entries.chunked(columns)) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            for (s in row) {
                val on = s == chosen
                Column(
                    Modifier.clip(RoundedCornerShape(18.dp))
                        .background(if (on) Sh.blue.copy(alpha = 0.16f) else Color.Transparent)
                        .border(2.dp, if (on) Sh.blue else Color.Transparent, RoundedCornerShape(18.dp))
                        .press({ ClusterDisplay.setStyle(ctx, s) }, label = s.label)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ClusterThumb(s, thumbWidth)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Txt(s.label, Sh.text(17.sp, FontWeight.SemiBold, if (on) Sh.blue else Sh.ink))
                        if (on) Txt("On the cluster", Sh.text(14.sp, FontWeight.Medium, Sh.dim))
                    }
                }
            }
        }
    }
}

/** The bubble from the Home screen's cluster pill: on/off and the designs. */
@Composable
fun ClusterBubble() {
    val ctx = LocalContext.current
    Column(
        Modifier.width(720.dp).clip(RoundedCornerShape(28.dp)).background(Color(0xFF1C1C1E))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Txt("Instrument cluster", Sh.text(22.sp, FontWeight.Bold))
                Txt(if (ClusterDisplay.enabled.value) "Tap a design to put it on the driver display" else "Off: the cluster shows BYD's own view",
                    Sh.text(15.sp, FontWeight.Medium, Sh.dim))
            }
            Switch(ClusterDisplay.enabled.value, { ClusterDisplay.setEnabled(ctx, it) }, "Show SharkOS on the instrument cluster")
        }
        Spacer(Modifier.height(2.dp))
        ClusterGallery(columns = 2, thumbWidth = 320.dp)
    }
}
