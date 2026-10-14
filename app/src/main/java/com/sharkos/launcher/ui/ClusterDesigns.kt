package com.sharkos.launcher.ui

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.Corner
import com.sharkos.launcher.car.Drive
import com.sharkos.launcher.car.Tyre
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.media.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/*
 * The other driver-display designs. All are laid out in the cluster's own 1920 × 720 pixels and keep
 * to the window the car shows (WIN_TOP, WIN_H): it blacks out its own top row and bottom strip.
 */

private val SOFT = Color(0xFFC7C7CC)
private val DIM = Color(0xFF636366)
private val GREEN = Color(0xFF30D158)

@Composable
private fun rememberArt(track: Track?): ImageBitmap? = remember(track?.art) { track?.art?.asImageBitmap() }

@Composable
private fun rememberAccent(track: Track?): ArtColors? = remember(track?.art) { track?.art?.let(::artColors) }

/** The cover shrunk to a few pixels: stretched back up it's a soft wash of its colours (Android 11 has no blur). */
@Composable
private fun rememberWash(track: Track?, w: Int, h: Int): ImageBitmap? = remember(track?.art, w, h) {
    track?.art?.let { b -> runCatching { Bitmap.createScaledBitmap(b, w, h, true).asImageBitmap() }.getOrNull() }
}

@Composable
private fun Cover(art: ImageBitmap?, modifier: Modifier) = Box(modifier.background(Color(0xFF1C1C1E)), contentAlignment = Alignment.Center) {
    if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    else Glyph(ShIcons.music, DIM, 64.cpx())
}

/** Where the music comes from, the title and the artist. */
@Composable
private fun SongText(track: Track?, accent: Color, titleSize: Int, center: Boolean = false, chip: Boolean = true) {
    val align = if (center) Alignment.CenterHorizontally else Alignment.Start
    Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(8.cpx())) {
        if (track == null) { Txt("Nothing playing", Sh.text(44.csp(), FontWeight.Bold, GREY)); return@Column }
        if (chip) Box(Modifier.clip(RoundedCornerShape(15.cpx())).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 13.cpx(), vertical = 5.cpx())) {
            Txt("${track.source.uppercase()} · ${if (track.playing) "NOW PLAYING" else "PAUSED"}", Sh.text(15.csp(), FontWeight.Bold, accent, 0.6.sp))
        }
        Txt(track.title, Sh.text(titleSize.csp(), FontWeight.ExtraBold, Color.White, (-1.4).sp).copy(textAlign = if (center) TextAlign.Center else TextAlign.Start), maxLines = 2)
        if (track.artist.isNotBlank()) Txt(track.artist, Sh.text((titleSize / 2).coerceIn(22, 34).csp(), FontWeight.Medium, SOFT), maxLines = 1)
    }
}

@Composable
private fun Progress(track: Track?, now: Long, color: Color, width: Dp, glow: Boolean = false) {
    if (track == null || !track.knowsPosition || track.durationMs <= 0) return
    val pos = track.positionAt(now)
    Column(Modifier.width(width), verticalArrangement = Arrangement.spacedBy(10.cpx())) {
        Box(Modifier.fillMaxWidth().height(8.cpx()).clip(RoundedCornerShape(4.cpx())).background(Color.White.copy(alpha = 0.16f))) {
            Box(Modifier.fillMaxWidth((pos.toFloat() / track.durationMs).coerceIn(0f, 1f)).fillMaxHeight()
                .clip(RoundedCornerShape(4.cpx())).background(color))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Txt(clock(pos), Sh.text(19.csp(), FontWeight.Medium, GREY))
            Txt("−" + clock((track.durationMs - pos).coerceAtLeast(0)), Sh.text(19.csp(), FontWeight.Medium, GREY))
        }
    }
}

/** A one-line tyre check: green "all good", or the first warning in orange. */
@Composable
private fun TyreStatus(tyres: List<Tyre?>, limits: TyreLimits, size: TextUnit) {
    val warn = limits.check(tyres).firstOrNull()
    val known = tyres.any { (it?.psi ?: 0.0) >= 5 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.cpx())) {
        Box(Modifier.size(12.cpx()).clip(CircleShape).background(when { warn != null -> ORANGE; known -> GREEN; else -> DIM }))
        Txt(when { warn != null -> warn.message; known -> "Tyres all good"; else -> "Tyres: waiting for the car" },
            Sh.text(size, FontWeight.SemiBold, if (warn != null) ORANGE else SOFT))
    }
}

/** Positions inside the 1920 × 720 picture. */
@Composable
private fun At(x: Int, y: Int, content: @Composable () -> Unit) = Box(Modifier.padding(start = x.cpx(), top = y.cpx())) { content() }

@Composable
private fun Window(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().background(Color.Black)) { content() }


// ---- Stage: the cover centre stage ----

@Composable
internal fun StageLayout(track: Track?, now: Long, tyres: List<Tyre?>, limits: TyreLimits) = Window {
    val art = rememberArt(track); val c = rememberAccent(track); val accent = c?.accent ?: Sh.blue
    Box(Modifier.padding(top = WIN_TOP.cpx()).fillMaxWidth().height(WIN_H.cpx()).background(Brush.horizontalGradient(
        0f to Color.Black, 0.3f to accent.copy(alpha = 0.28f), 0.5f to (c?.shade ?: Color.Black).copy(alpha = 0.6f), 0.7f to accent.copy(alpha = 0.28f), 1f to Color.Black)))
    At(758, WIN_TOP + 11) {
        val r = RoundedCornerShape(22.cpx())
        Cover(art, Modifier.size(404.cpx()).shadow(40.cpx(), r, ambientColor = accent, spotColor = accent).clip(r))
    }
    At(140, WIN_TOP + 40) {
        Column(Modifier.width(560.cpx()).height((WIN_H - 80).cpx()), verticalArrangement = Arrangement.spacedBy(30.cpx(), Alignment.CenterVertically)) {
            SongText(track, accent, 58)
            Progress(track, now, accent, 520.cpx())
        }
    }
    At(1250, WIN_TOP + 90) {
        Column(verticalArrangement = Arrangement.spacedBy(14.cpx())) {
            Txt("TIME", Sh.text(15.csp(), FontWeight.Bold, GREY, 0.7.sp))
            Txt(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()), Sh.text(40.csp(), FontWeight.SemiBold, SOFT))
            Txt("TYRES", Sh.text(15.csp(), FontWeight.Bold, GREY, 0.7.sp))
            TyreStatus(tyres, limits, 26.csp())
        }
    }
}

// ---- Vinyl: a sleeve and a record that spins while music plays ----

@Composable
internal fun VinylLayout(track: Track?, now: Long) = Window {
    val art = rememberArt(track); val c = rememberAccent(track); val accent = c?.accent ?: Sh.blue
    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.26f), Color.Transparent),
        center = Offset(480f, WIN_TOP + WIN_H / 2f), radius = 700f)))
    val spin = rememberInfiniteTransition(label = "vinyl")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "turn")
    val playing = track?.playing == true && !LocalInspectionMode.current
    At(330, WIN_TOP + 18) {
        Box(Modifier.size(390.cpx()).rotate(if (playing) turn else 0f).clip(CircleShape)
            .background(Brush.radialGradient(listOf(Color(0xFF1A1A1C), Color(0xFF0B0B0C), Color(0xFF161618), Color(0xFF0B0B0C)))),
            contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                var r = size.minDimension / 2f - 8f
                while (r > size.minDimension * 0.22f) { drawCircle(Color(0xFF242427), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)); r -= 7f }
            }
            Cover(art, Modifier.size(150.cpx()).clip(CircleShape))
            Box(Modifier.size(12.cpx()).clip(CircleShape).background(Color.Black))
        }
    }
    At(110, WIN_TOP + 23) {
        val r = RoundedCornerShape(12.cpx())
        Cover(art, Modifier.size(380.cpx()).shadow(30.cpx(), r).clip(r))
    }
    At(820, WIN_TOP + 30) {
        Column(Modifier.width(640.cpx()).height((WIN_H - 60).cpx()), verticalArrangement = Arrangement.spacedBy(34.cpx(), Alignment.CenterVertically)) {
            SongText(track, accent, 64)
            Progress(track, now, accent, 640.cpx())
        }
    }
    if (track != null && track.knowsPosition && track.durationMs > 0) At(1540, WIN_TOP + 130) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(240.cpx())) {
            Txt("LEFT", Sh.text(15.csp(), FontWeight.Bold, GREY, 0.7.sp))
            Txt(clock((track.durationMs - track.positionAt(now)).coerceAtLeast(0)), Sh.text(56.csp(), FontWeight.ExtraBold, Color.White))
        }
    }
}

// ---- Colour field: the cover's colours as light, the title huge ----

@Composable
internal fun FieldLayout(track: Track?, now: Long) = Window {
    val art = rememberArt(track); val wash = rememberWash(track, 4, 3)
    Box(Modifier.padding(top = WIN_TOP.cpx()).fillMaxWidth().height(WIN_H.cpx()).background(Color(0xFF07061A))) {
        if (wash != null) Image(wash, null, Modifier.fillMaxSize().alpha(0.75f), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f)))))
    }
    Box(Modifier.padding(top = (WIN_TOP + 40).cpx()).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.width(1500.cpx()), contentAlignment = Alignment.TopCenter) { SongText(track, Color.White, 112, center = true, chip = false) }
    }
    At(120, WIN_TOP + 300) { val r = RoundedCornerShape(14.cpx()); Cover(art, Modifier.size(96.cpx()).clip(r)) }
    Box(Modifier.padding(top = (WIN_TOP + 330).cpx()).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Progress(track, now, Color.White, 800.cpx())
    }
}

// ---- Backdrop: the cover blurred across the window, a sharp cover on top ----

@Composable
internal fun BackdropLayout(track: Track?, now: Long) = Window {
    val art = rememberArt(track); val wash = rememberWash(track, 12, 6)
    Box(Modifier.padding(top = WIN_TOP.cpx()).fillMaxWidth().height(WIN_H.cpx())) {
        if (wash != null) Image(wash, null, Modifier.fillMaxSize().alpha(0.7f), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.6f)))))
    }
    At(140, WIN_TOP + 21) { val r = RoundedCornerShape(22.cpx()); Cover(art, Modifier.size(384.cpx()).shadow(36.cpx(), r).clip(r)) }
    At(620, WIN_TOP + 30) {
        Column(Modifier.width(800.cpx()).height((WIN_H - 60).cpx()), verticalArrangement = Arrangement.spacedBy(34.cpx(), Alignment.CenterVertically)) {
            SongText(track, Color.White, 72)
            Progress(track, now, Color.White, 760.cpx())
        }
    }
}

// ---- Three tiles: music, this drive, tyres ----

@Composable
private fun Tile(x: Int, w: Int, label: String, labelColor: Color, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) = At(x, WIN_TOP + 20) {
    Column(Modifier.width(w.cpx()).height((WIN_H - 40).cpx()).clip(RoundedCornerShape(28.cpx())).background(Color(0xFF111114))
        .border(1.cpx(), Color(0xFF1F1F23), RoundedCornerShape(28.cpx())).padding(horizontal = 30.cpx(), vertical = 28.cpx()),
        verticalArrangement = Arrangement.spacedBy(14.cpx())) {
        Txt(label, Sh.text(15.csp(), FontWeight.Bold, labelColor, 0.7.sp))
        content()
    }
}

@Composable
internal fun TilesLayout(track: Track?, now: Long, tyres: List<Tyre?>, limits: TyreLimits, drive: Drive?) = Window {
    val art = rememberArt(track); val accent = rememberAccent(track)?.accent ?: Sh.blue
    Tile(60, 600, "NOW PLAYING", accent) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.cpx()), verticalAlignment = Alignment.CenterVertically) {
            Cover(art, Modifier.size(190.cpx()).clip(RoundedCornerShape(20.cpx())))
            Column(verticalArrangement = Arrangement.spacedBy(8.cpx())) {
                Txt(track?.title ?: "Nothing playing", Sh.text(40.csp(), FontWeight.ExtraBold, if (track != null) Color.White else GREY), maxLines = 2)
                Txt(track?.artist.orEmpty(), Sh.text(24.csp(), FontWeight.Medium, SOFT), maxLines = 1)
            }
        }
        Progress(track, now, accent, 540.cpx())
    }
    Tile(690, 540, "THIS DRIVE", GREEN) {
        val km = drive?.km ?: 0.0
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("%.1f".format(km), Sh.text(84.csp(), FontWeight.ExtraBold, Color.White, (-2).sp))
            Txt(" km", Sh.text(28.csp(), FontWeight.SemiBold, GREY), Modifier.padding(bottom = 12.cpx()))
        }
        Txt(listOfNotNull(drive?.minutes?.takeIf { it > 0 }?.let { "$it min" }, drive?.avgKph?.let { "${it.roundToInt()} km/h average" })
            .joinToString(" · ").ifEmpty { "km so far" }, Sh.text(22.csp(), FontWeight.Medium, SOFT))
        drive?.evShare?.let { share ->
            Box(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().height(12.cpx()).clip(RoundedCornerShape(6.cpx()))) {
                if (share > 0) Box(Modifier.weight(share.toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(GREEN))
                if (share < 1) Box(Modifier.weight((1 - share).toFloat().coerceAtLeast(0.01f)).fillMaxHeight().background(ORANGE))
            }
            Txt("${(share * 100).roundToInt()}% electric", Sh.text(17.csp(), FontWeight.Medium, GREY))
        }
    }
    Tile(1260, 600, "TYRES", GREY) {
        val warn = limits.check(tyres).associateBy { it.corner }
        Column(Modifier.padding(top = 10.cpx()), verticalArrangement = Arrangement.spacedBy(24.cpx())) {
            for (row in listOf(Corner.LF to Corner.RF, Corner.LR to Corner.RR)) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for ((i, corner) in row.toList().withIndex()) Column(horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                    val psi = tyres.getOrNull(corner.ordinal)?.psi?.takeIf { it >= 5 }
                    Txt(psi?.roundToInt()?.toString() ?: "—", Sh.text(58.csp(), FontWeight.Bold, if (corner in warn) ORANGE else Color.White))
                    Txt("${corner.label} psi", Sh.text(15.csp(), FontWeight.Medium, if (corner in warn) ORANGE else GREY))
                }
            }
        }
    }
}

// ---- Night: almost nothing ----

@Composable
internal fun NightLayout(track: Track?, tyres: List<Tyre?>, limits: TyreLimits) = Window {
    val accent = rememberAccent(track)?.accent ?: Color(0xFF5A6CFF)
    Box(Modifier.padding(top = 287.cpx()).fillMaxWidth().height(200.cpx())
        .background(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.16f), Color.Transparent)))
        .background(Brush.horizontalGradient(0f to Color.Black, 0.25f to Color.Transparent, 0.75f to Color.Transparent, 1f to Color.Black)))
    Box(Modifier.padding(top = 385.cpx()).fillMaxWidth().height(5.cpx())
        .background(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to accent.copy(alpha = 0.9f), 0.7f to accent.copy(alpha = 0.9f), 1f to Color.Transparent)))
    if (track != null) Box(Modifier.padding(top = 420.cpx()).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.cpx())) {
            Txt(track.title, Sh.text(34.csp(), FontWeight.Bold, Color.White.copy(alpha = 0.85f)), maxLines = 1)
            if (track.artist.isNotBlank()) Txt(track.artist, Sh.text(22.csp(), FontWeight.Medium, DIM), maxLines = 1)
        }
    }
    // Only speaks up when a tyre needs attention
    val warn = limits.check(tyres).firstOrNull()
    At(1500, 520) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.cpx())) {
            Box(Modifier.size(10.cpx()).clip(CircleShape).background(if (warn != null) ORANGE else GREEN.copy(alpha = 0.6f)))
            Txt(warn?.message ?: "Tyres OK", Sh.text(18.csp(), FontWeight.SemiBold, if (warn != null) ORANGE else DIM))
        }
    }
}
