package com.sharkos.launcher.ui

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sharkos.launcher.car.CarPlayLink.RouteGuide
import com.sharkos.launcher.car.Tyre
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.media.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where CarPlay's map sits in SharkOS's cluster view during a route. */
enum class MapLayout(val label: String) { CENTRE("Map in the middle"), WIDE("Map wide") }

private val BLUE = Color(0xFF2F8CFF)
private val SOFT = Color(0xFFC7C7CC)

/** The iPhone's driver-display map is drawn at about this shape (BYD's cluster map area, 660 × 372). */
private const val STREAM_ASPECT = 660f / 372f

/** CarPlay's map: a TextureView the CarPlay service draws into, cropped to fill without stretching. */
@Composable
internal fun CarPlayMapView(modifier: Modifier) {
    if (LocalInspectionMode.current || ClusterDisplay.previewMap) { FakeMap(modifier); return }
    AndroidView(modifier = modifier, factory = { ctx ->
        TextureView(ctx).apply {
            var surface: Surface? = null
            fun crop(w: Int, h: Int) {
                if (w <= 0 || h <= 0) return
                val va = w.toFloat() / h
                val m = Matrix()
                if (va > STREAM_ASPECT) m.setScale(1f, va / STREAM_ASPECT, w / 2f, h / 2f)
                else m.setScale(STREAM_ASPECT / va, 1f, w / 2f, h / 2f)
                setTransform(m)
            }
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                    crop(w, h)
                    surface = Surface(st).also { ClusterDisplay.mapSurface(it) }
                }
                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = crop(w, h)
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                    ClusterDisplay.mapSurface(null); surface?.release(); surface = null; return true
                }
                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
            }
        }
    })
}

/** Stand-in map for the screenshot tests. */
@Composable
private fun FakeMap(modifier: Modifier) = Canvas(modifier.background(Color(0xFF1B2027))) {
    val w = size.width; val h = size.height
    for (i in 0..5) drawRect(Color(0xFF232A33), Offset(w * (0.05f + i * 0.16f), h * 0.08f), androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.3f))
    drawLine(Color(0xFF39414C), Offset(0f, h * 0.55f), Offset(w, h * 0.55f), 22f)
    drawLine(Color(0xFF39414C), Offset(w * 0.42f, 0f), Offset(w * 0.42f, h), 18f)
    drawPath(Path().apply { moveTo(w * 0.42f, h); lineTo(w * 0.42f, h * 0.55f); lineTo(w * 0.95f, h * 0.55f) }, BLUE,
        style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Color.White, 18f, Offset(w * 0.42f, h * 0.8f))
}

/** The arrow for a CarPlay maneuver type (iAP2 route guidance). */
@Composable
private fun TurnArrow(maneuver: Int, size: Int) = Canvas(Modifier.size(size.cpx())) {
    val s = this.size.width / 24f
    fun p(vararg pts: Float) = Path().apply { moveTo(pts[0] * s, pts[1] * s); var i = 2; while (i < pts.size) { lineTo(pts[i] * s, pts[i + 1] * s); i += 2 } }
    val stroke = Stroke(2.6f * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (maneuver) {
        1, 20 -> { drawPath(p(17f, 20f, 17f, 11f, 15f, 8f, 6f, 8f), Color.White, style = stroke); drawPath(p(10f, 4f, 6f, 8f, 10f, 12f), Color.White, style = stroke) }
        2, 21 -> { drawPath(p(7f, 20f, 7f, 11f, 9f, 8f, 18f, 8f), Color.White, style = stroke); drawPath(p(14f, 4f, 18f, 8f, 14f, 12f), Color.White, style = stroke) }
        4, 18, 26 -> { drawPath(p(16f, 20f, 16f, 9f, 13f, 5f, 9f, 5f, 6f, 9f, 6f, 14f), Color.White, style = stroke); drawPath(p(3f, 11f, 6f, 15f, 9f, 11f), Color.White, style = stroke) }
        13, 49, 52 -> { drawPath(p(15f, 20f, 15f, 13f, 8f, 6f), Color.White, style = stroke); drawPath(p(8f, 11f, 8f, 6f, 13f, 6f), Color.White, style = stroke) }
        14, 50, 53, 8, 9, 22, 23, 51 -> { drawPath(p(9f, 20f, 9f, 13f, 16f, 6f), Color.White, style = stroke); drawPath(p(11f, 6f, 16f, 6f, 16f, 11f), Color.White, style = stroke) }
        6, 7, in 28..45 -> { drawCircle(Color.White, 4f * s, Offset(12f * s, 10f * s), style = stroke); drawPath(p(12f, 20f, 12f, 14f), Color.White, style = stroke) }
        10, 12, 24, 25 -> { drawPath(p(7f, 21f, 7f, 4f, 17f, 4f, 14f, 8f, 17f, 12f, 7f, 12f), Color.White, style = stroke) }
        else -> { drawPath(p(12f, 20f, 12f, 5f), Color.White, style = stroke); drawPath(p(7f, 9f, 12f, 4f, 17f, 9f), Color.White, style = stroke) }
    }
}

@Composable
private fun NextTurn(g: RouteGuide?, big: Boolean) {
    val box = if (big) 96 else 78
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.cpx())) {
        Box(Modifier.size(box.cpx()).clip(RoundedCornerShape(22.cpx())).background(BLUE), contentAlignment = Alignment.Center) {
            TurnArrow(g?.maneuver ?: 3, (box * 0.6f).toInt())
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.cpx())) {
            Txt(g?.let { "${it.distance} ${it.distanceUnit}".trim() }?.ifBlank { null } ?: "Route on",
                Sh.text((if (big) 50 else 42).csp(), FontWeight.ExtraBold, Color.White))
            val road = g?.road?.ifBlank { null } ?: g?.description?.ifBlank { null }
            if (road != null) Txt(road, Sh.text((if (big) 24 else 21).csp(), FontWeight.Medium, SOFT), maxLines = 1)
        }
    }
}

@Composable
private fun Arrival(g: RouteGuide?) {
    if (g == null || g.secondsLeft < 0) return
    val left = (g.secondsLeft - (System.currentTimeMillis() - g.receivedAt) / 1000).coerceAtLeast(0)
    val arrive = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(System.currentTimeMillis() + left * 1000))
    val leftText = if (left >= 3600) "${left / 3600} h ${(left % 3600) / 60} min" else "${(left / 60).coerceAtLeast(1)} min"
    Row(horizontalArrangement = Arrangement.spacedBy(34.cpx())) {
        for ((label, value) in listOfNotNull("ARRIVE" to arrive, "LEFT" to leftText,
            g.remaining.ifBlank { null }?.let { "DISTANCE" to "$it ${g.remainingUnit}".trim() })) {
            Column {
                Txt(label, Sh.text(15.csp(), FontWeight.Bold, GREY, 0.7.sp))
                Txt(value, Sh.text(34.csp(), FontWeight.Bold, Color.White))
            }
        }
    }
}

@Composable
private fun MiniSong(track: Track?, width: Int) {
    if (track == null) return
    val art = remember(track.art) { track.art?.asImageBitmap() }
    val accent = remember(track.art) { track.art?.let(::artColors)?.accent } ?: Sh.blue
    Row(Modifier.width(width.cpx()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.cpx())) {
        Box(Modifier.size(84.cpx()).clip(RoundedCornerShape(14.cpx())).background(Color(0xFF1C1C1E))) {
            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.cpx())) {
            Txt(track.title, Sh.text(28.csp(), FontWeight.ExtraBold, Color.White), maxLines = 1)
            Txt(track.artist, Sh.text(19.csp(), FontWeight.Medium, SOFT), maxLines = 1)
            if (track.knowsPosition && track.durationMs > 0) {
                val f = (track.positionAt(android.os.SystemClock.elapsedRealtime()).toFloat() / track.durationMs).coerceIn(0f, 1f)
                Box(Modifier.padding(top = 6.cpx()).fillMaxWidth().height(6.cpx()).clip(RoundedCornerShape(3.cpx())).background(Color.White.copy(alpha = 0.15f))) {
                    Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(accent))
                }
            }
        }
    }
}

@Composable
private fun TyreLine(tyres: List<Tyre?>, limits: TyreLimits) {
    val warn = limits.check(tyres).firstOrNull()
    Column(verticalArrangement = Arrangement.spacedBy(10.cpx())) {
        Txt("TYRES", Sh.text(15.csp(), FontWeight.Bold, GREY, 0.7.sp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.cpx())) {
            Box(Modifier.size(12.cpx()).clip(CircleShape).background(if (warn != null) ORANGE else Color(0xFF30D158)))
            Txt(warn?.message ?: "All good", Sh.text(24.csp(), FontWeight.SemiBold, if (warn != null) ORANGE else SOFT))
        }
    }
}

@Composable
private fun At(x: Int, y: Int, content: @Composable () -> Unit) = Box(Modifier.padding(start = x.cpx(), top = y.cpx())) { content() }

/** SharkOS's cluster view during a CarPlay route: the iPhone's map in [mapSlot], SharkOS around it. */
@Composable
internal fun MapLayoutView(layout: MapLayout, track: Track?, g: RouteGuide?, tyres: List<Tyre?>, limits: TyreLimits, mapSlot: @Composable () -> Unit) =
    Box(Modifier.fillMaxSize().background(ClusterDisplay.bgColor)) {
        when (layout) {
            MapLayout.CENTRE -> {
                At(560, WIN_TOP + 8) { Box(Modifier.size(800.cpx(), (WIN_H - 16).cpx()).clip(RoundedCornerShape(26.cpx()))) { mapSlot() } }
                At(70, WIN_TOP + 40) { NextTurn(g, big = true) }
                At(70, WIN_TOP + 250) { MiniSong(track, 440) }
                At(1420, WIN_TOP + 60) { Arrival(g) }
                At(1420, WIN_TOP + 190) { TyreLine(tyres, limits) }
            }
            MapLayout.WIDE -> {
                At(40, WIN_TOP + 8) { Box(Modifier.size(1240.cpx(), (WIN_H - 16).cpx()).clip(RoundedCornerShape(26.cpx()))) { mapSlot() } }
                At(1330, WIN_TOP + 30) { NextTurn(g, big = false) }
                At(1330, WIN_TOP + 150) { Arrival(g) }
                At(1330, WIN_TOP + 270) { MiniSong(track, 540) }
            }
        }
    }
