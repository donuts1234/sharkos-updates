package com.sharkos.launcher.ui

import android.content.Context
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.media.Track
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The two looks night mode can give the main screen. */
enum class NightStyle(val label: String, val blurb: String) {
    GLOW("Glow line", "A soft clock, a glow line in the song's colour, and the song with its controls. Matches the cluster's Night design."),
    CLOCK("Big clock", "A big thin clock on the left, the song (cover dimmed) and its controls on the right.");
    companion object { fun parse(s: String?) = entries.firstOrNull { it.name == s } ?: GLOW }
}

/**
 * Night mode: switched on and off with the moon pill. The main screen goes almost black (in the
 * chosen [NightStyle]) and the instrument cluster shows its Night design until it's turned off.
 */
object NightMode {
    val active = mutableStateOf(false)
    val style = mutableStateOf(NightStyle.GLOW)
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        runCatching { style.value = NightStyle.parse(Prefs(ctx.applicationContext).nightStyle) }
        loaded = true
    }

    fun toggle() { active.value = !active.value }

    fun setStyle(ctx: Context, s: NightStyle) {
        style.value = s
        runCatching { Prefs(ctx.applicationContext).nightStyle = s.name }
    }
}

private val DIM = Color(0xFF48484A)
private val MID = Color(0xFF636366)
private val FADED = Color(0xFFF5F5F7).copy(alpha = 0.6f)

/** The moon pill: night mode on or off. Lit when on. */
@Composable
fun NightPill(modifier: Modifier = Modifier) {
    val on = NightMode.active.value
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
            .background(if (on) Color(0xFF1C1A33) else Sh.card)
            .press({ NightMode.toggle() }, label = if (on) "Turn night mode off" else "Turn night mode on")
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Glyph(ShIcons.moon, if (on) Color(0xFF9B8CFF) else Sh.dim, 20.dp)
        Txt("Night", Sh.text(16.sp, FontWeight.Medium, if (on) Color(0xFFB4A8FF) else Sh.ink))
    }
}

/** The main screen at night. */
@Composable
fun NightHome(now: Long, t: Telemetry, track: Track?, a: HomeActions, limits: TyreLimits, style: NightStyle, showPill: Boolean = true) {
    val accent = remember(track?.art) { track?.art?.let(::artColors)?.accent } ?: Color(0xFF7A6CFF)
    val warn = limits.check(t.tyres).firstOrNull()
    val time = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now))
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (style) {
            NightStyle.GLOW -> {
                Box(Modifier.padding(top = 300.dp).fillMaxWidth().height(200.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.14f), Color.Transparent))))
                Box(Modifier.padding(top = 398.dp).fillMaxWidth().height(3.dp)
                    .background(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to accent, 0.7f to accent.copy(alpha = 0.8f), 1f to Color.Transparent)))
                Txt(time, Sh.text(150.sp, FontWeight.Light, FADED, (-4).sp), Modifier.align(Alignment.TopCenter).padding(top = 100.dp))
                Column(Modifier.align(Alignment.TopCenter).padding(top = 430.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NightSong(track, center = true)
                    Box(Modifier.padding(top = 24.dp)) { NightControls(track, a) }
                }
                NightStatus(t, warn, Modifier.padding(start = 40.dp, top = 34.dp))
            }
            NightStyle.CLOCK -> {
                Column(Modifier.padding(start = 80.dp, top = 140.dp)) {
                    Txt(time, Sh.text(220.sp, FontWeight.Light, FADED, (-8).sp))
                    Txt(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(now)), Sh.text(30.sp, FontWeight.Medium, DIM), Modifier.padding(start = 8.dp))
                }
                Column(Modifier.align(Alignment.TopEnd).padding(top = 190.dp, end = 80.dp).width(560.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = remember(track?.art) { track?.art?.asImageBitmap() }
                        Box(Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF111113)).alpha(0.55f)) {
                            if (art != null) Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        NightSong(track, center = false)
                    }
                    if (track != null && track.knowsPosition && track.durationMs > 0) {
                        val f = (track.positionMs.toFloat() / track.durationMs).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1C1C1E))) {
                            Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(accent.copy(alpha = 0.7f)))
                        }
                    }
                    NightControls(track, a)
                }
                NightStatus(t, warn, Modifier.align(Alignment.BottomStart).padding(start = 88.dp, bottom = 50.dp))
            }
        }
        // The way back out, where the pills normally are
        if (showPill) NightPill(Modifier.align(Alignment.TopEnd).padding(top = 24.dp, end = 32.dp))
    }
}

@Composable
private fun NightSong(track: Track?, center: Boolean) = Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Txt(track?.title ?: "Nothing playing", Sh.text(40.sp, FontWeight.Bold, Color(0xFFF5F5F7).copy(alpha = if (track != null) 0.85f else 0.4f)), maxLines = 1)
    if (!track?.artist.isNullOrBlank()) Txt(track!!.artist, Sh.text(24.sp, FontWeight.Medium, MID), maxLines = 1)
}

@Composable
private fun NightControls(track: Track?, a: HomeActions) {
    if (track == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(64.dp).clip(CircleShape).press(a.previous, label = "Previous track"), contentAlignment = Alignment.Center) { Glyph(ShIcons.previous, MID, 36.dp) }
        Box(Modifier.size(86.dp).clip(CircleShape).border(2.dp, Color(0xFF3A3A3C), CircleShape).press(a.playPause, label = if (track.playing) "Pause" else "Play"),
            contentAlignment = Alignment.Center) { Glyph(if (track.playing) ShIcons.pause else ShIcons.play, Color(0xFFC7C7CC), 34.dp) }
        Box(Modifier.size(64.dp).clip(CircleShape).press(a.next, label = "Next track"), contentAlignment = Alignment.Center) { Glyph(ShIcons.next, MID, 36.dp) }
    }
}

/** Range, outside temperature and tyres, quietly; a tyre warning stays orange. */
@Composable
private fun NightStatus(t: Telemetry, warn: com.sharkos.launcher.car.TyreWarning?, modifier: Modifier) =
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(26.dp), verticalAlignment = Alignment.CenterVertically) {
        val range = listOfNotNull(t.socPercent?.let { "${it.roundToInt()}%" }, t.evRangeKm?.let { "${it.roundToInt()} km" }).joinToString(" · ")
        if (range.isNotEmpty()) Txt(range, Sh.text(19.sp, FontWeight.SemiBold, DIM))
        t.outsideTempC?.let { Txt("${it.roundToInt()}° outside", Sh.text(19.sp, FontWeight.SemiBold, DIM)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (warn != null) Color(0xFFFF9F0A) else Color(0xFF2E7D46)))
            Txt(warn?.message ?: "Tyres OK", Sh.text(19.sp, FontWeight.SemiBold, if (warn != null) Color(0xFFFF9F0A) else Color(0xFF2E7D46)))
        }
    }

/** Settings → Night time: the two looks side by side, drawn small; tap to choose. */
@Composable
fun NightStylePicker(sample: Track?) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val noop = remember { HomeActions({}, {}, {}, {}, {}, {}, {}) }
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        for (s in NightStyle.entries) {
            val on = s == NightMode.style.value
            Column(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (on) Sh.blue.copy(alpha = 0.16f) else Color.Transparent)
                    .border(2.dp, if (on) Sh.blue else Color.Transparent, RoundedCornerShape(18.dp))
                    .press({ NightMode.setStyle(ctx, s) }, label = s.label).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val w = 320.dp; val full = androidx.compose.ui.unit.DpSize(1280.dp, 640.dp); val k = w / full.width
                Box(Modifier.size(w, full.height * k).clip(RoundedCornerShape(12.dp))) {
                    Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(full).graphicsLayer {
                        scaleX = k; scaleY = k; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    }) { NightHome(System.currentTimeMillis(), Telemetry(), sample, noop, TyreLimits(), s, showPill = false) }
                }
                Txt(s.label, Sh.text(17.sp, FontWeight.SemiBold, if (on) Sh.blue else Sh.ink))
            }
        }
    }
}
