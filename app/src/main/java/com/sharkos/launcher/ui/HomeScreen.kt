package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.CarLink
import com.sharkos.launcher.car.Corner
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.media.Track
import com.sharkos.launcher.sensors.Attitude
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

class HomeActions(
    val playPause: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val openMusic: () -> Unit,
    val grantMedia: () -> Unit,
    val offroad: () -> Unit,
    val settings: () -> Unit,
    /** Tap on the music card: the full-screen music page. */
    val openNowPlaying: () -> Unit = {},
    /** Tap on the Shark 6 card: the Trip page. */
    val openTrips: () -> Unit = {},
    // The extra cards' controls
    val openClimate: () -> Unit = {},
    val toggleAc: () -> Unit = {},
    val toggleRecirc: () -> Unit = {},
    val toggleDemist: () -> Unit = {},
    val lightsOn: (Boolean) -> Unit = {},
    val lightsBrightness: (Int) -> Unit = {},
    val lightsHue: (Float) -> Unit = {},
    val openLights: () -> Unit = {},
    val launch: (String) -> Unit = {},
    /** An empty Shortcuts spot: pick an app for spot n. */
    val addShortcut: (Int) -> Unit = {},
    /** Hold a shortcut: empty its spot. */
    val removeShortcut: (Int) -> Unit = {},
)

/** What the optional cards show; each is read only when its card is on Home. */
data class HomeExtras(
    val drive: com.sharkos.launcher.car.Drive? = null,
    val climate: com.sharkos.launcher.car.ClimateState = com.sharkos.launcher.car.ClimateState(),
    val lights: BarLights? = null,
    /** "Matching album art" and the like, under the Lights card. */
    val lightsNote: String? = null,
    val shortcuts: List<DockApp?> = emptyList(),
)

@Composable
fun HomeScreen(
    now: Long,
    telemetry: Telemetry,
    link: CarLink,
    track: Track?,
    mediaAccess: Boolean,
    attitude: Attitude?,
    a: HomeActions,
    carDataAllowed: Boolean = true,
    tyreLimits: com.sharkos.launcher.car.TyreLimits = com.sharkos.launcher.car.TyreLimits(),
    cards: List<HomeCard> = HomeCard.DEFAULT,
    extras: HomeExtras = HomeExtras(),
) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 28.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Header(now, telemetry, link, a.settings)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // The first card is the wide one; with two cards it's wider still
            cards.forEachIndexed { i, card ->
                val m = Modifier.weight(if (i == 0) (if (cards.size == 2) 1.6f else 1.3f) else 1f).fillMaxHeight()
                when (card) {
                    HomeCard.NOW_PLAYING -> NowPlayingCard(track, mediaAccess, a, m)
                    HomeCard.SHARK6 -> VehicleCard(telemetry, carDataAllowed, a.settings, m.press(a.openTrips, label = "Open trips"))
                    HomeCard.TYRES -> TyresCard(telemetry, tyreLimits, attitude, a.offroad, m)
                    HomeCard.DRIVE -> DriveCard(extras.drive, a, m)
                    HomeCard.CLIMATE -> ClimateCard(extras.climate, a, m)
                    HomeCard.LIGHTS -> LightsCard(extras.lights, extras.lightsNote, a, m)
                    HomeCard.SHORTCUTS -> ShortcutsCard(extras.shortcuts, a, m)
                }
            }
        }
    }
}

@Composable
private fun Header(now: Long, t: Telemetry, link: CarLink, onSettings: () -> Unit) {
    val date = Date(now)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Txt(SimpleDateFormat("h:mm", Locale.getDefault()).format(date), Sh.text(60.sp, FontWeight.SemiBold, spacing = (-2).sp))
            Txt(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(date), Sh.text(18.sp, FontWeight.Medium, Sh.dim))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            t.outsideTempC?.let { Chip { Glyph(ShIcons.cloud, Sh.dim, 18.dp); Txt("${it.roundToInt()}° outside", Sh.text(16.sp, FontWeight.Medium)) } }
            ClusterPill()
            Chip {
                val (dot, text) = when (link) {
                    CarLink.CONNECTED -> Sh.green to (t.speedKph?.takeIf { it >= 1 }?.let { "${it.roundToInt()} km/h" } ?: "Connected")
                    CarLink.SEARCHING -> Sh.orange to "Connecting…"
                    CarLink.OFFLINE -> Sh.faint to "Car offline"
                }
                Box(Modifier.size(9.dp).clip(RoundedCornerShape(5.dp)).background(dot))
                Txt(text, Sh.text(16.sp, FontWeight.Medium))
            }
            RoundButton(ShIcons.gear, "SharkOS settings", onSettings, size = 44.dp, iconSize = 20.dp, bg = Sh.card, tint = Sh.dim)
        }
    }
}

/** The instrument cluster: which design it shows; tap for the bubble of designs. */
@Composable
private fun ClusterPill() {
    var open by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val on = ClusterDisplay.enabled.value
    Box {
        Chip(Modifier.press({ open = !open }, label = "Instrument cluster designs")) {
            Glyph(ShIcons.gauge, if (on) Color(0xFF30B0C7) else Sh.faint, 20.dp)
            Txt(if (on) ClusterDisplay.style.value.label else "Cluster off", Sh.text(16.sp, FontWeight.Medium))
        }
        if (open) androidx.compose.ui.window.Popup(
            alignment = Alignment.TopEnd,
            offset = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.unit.IntOffset(0, 54.dp.roundToPx()) },
            onDismissRequest = { open = false },
            properties = androidx.compose.ui.window.PopupProperties(focusable = true),
        ) { ClusterBubble() }
    }
}

@Composable
private fun Chip(modifier: Modifier = Modifier, content: @Composable () -> Unit) = Row(
    modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Sh.card).padding(horizontal = 16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) { content() }

// ---- Now playing ----

@Composable
private fun NowPlayingCard(track: Track?, access: Boolean, a: HomeActions, modifier: Modifier) {
    val art = track?.art
    if (track != null && art != null) ArtCard(track, art, a, modifier) else PlainCard(track, access, a, modifier)
}

/**
 * With album art: the cover fills the card, a wash of its own darkest tone keeps the text readable,
 * and the progress bar and buttons take the cover's colour.
 */
@Composable
private fun ArtCard(track: Track, art: android.graphics.Bitmap, a: HomeActions, modifier: Modifier) {
    val colors = remember(art) { artColors(art) }
    val accent by animateColorAsState(colors.accent, tween(500), label = "accent")
    val onAccent by animateColorAsState(colors.onAccent, tween(500), label = "onAccent")
    val shade by animateColorAsState(colors.shade, tween(500), label = "shade")
    val image = remember(art) { art.asImageBitmap() }
    Box(modifier.clip(RoundedCornerShape(28.dp)).background(shade).press(a.openNowPlaying, label = "Open now playing")) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.45f),
                    0.22f to Color.Transparent,
                    0.42f to shade.copy(alpha = 0.25f),
                    0.68f to shade.copy(alpha = 0.88f),
                    1f to shade,
                ),
            ),
        )
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Now playing", Modifier.weight(1f), color = Color.White.copy(alpha = 0.8f))
                Txt(if (track.playing) track.source else "${track.source} · Paused", Sh.text(14.sp, FontWeight.Medium, Color.White.copy(alpha = 0.8f)))
            }
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
                Txt(track.title.ifBlank { "Unknown track" }, Sh.text(28.sp, FontWeight.Bold, Color.White, spacing = (-0.4).sp), maxLines = 2)
                Gap(4.dp)
                Txt(track.artist, Sh.text(19.sp, FontWeight.Medium, Color.White.copy(alpha = 0.75f)))
                Gap(18.dp)
                Progress(track, accent, Color.White.copy(alpha = 0.7f), Color.White.copy(alpha = 0.22f))
            }
            Gap(12.dp)
            Controls(track, a, accent, onAccent, sideTint = accent)
        }
    }
}

@Composable
private fun PlainCard(track: Track?, access: Boolean, a: HomeActions, modifier: Modifier) = Panel(
    if (track != null) modifier.press(a.openNowPlaying, label = "Open now playing") else modifier,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Now playing", Modifier.weight(1f))
        track?.let { Txt(if (it.playing) it.source else "${it.source} · Paused", Sh.text(14.sp, color = Sh.dim)) }
    }
    if (track == null) {
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)).background(Sh.raised), contentAlignment = Alignment.Center) {
                Glyph(ShIcons.music, Sh.faint, 44.dp)
            }
            Gap(16.dp)
            Txt(if (access) "Nothing playing" else "Show what's playing here", Sh.text(20.sp, FontWeight.SemiBold))
            Gap(4.dp)
            Txt(
                if (access) "Start something in any music app." else "SharkOS needs notification access to see the player.",
                Sh.text(15.sp, color = Sh.dim),
            )
            Gap(18.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!access) PillButton("Allow", a.grantMedia, bg = Sh.blue, fg = Color.White)
                PillButton("Open Music", a.openMusic)
            }
        }
        return@Panel
    }
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(150.dp).clip(RoundedCornerShape(20.dp)).background(Sh.raised), contentAlignment = Alignment.Center) {
            Glyph(ShIcons.music, Sh.faint, 52.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(track.title.ifBlank { "Unknown track" }, Sh.text(28.sp, FontWeight.Bold, spacing = (-0.4).sp), maxLines = 2)
            Txt(track.artist, Sh.text(19.sp, color = Sh.dim))
        }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Bottom) { Progress(track, Sh.ink, Sh.dim, Sh.line) }
    Controls(track, a, Sh.ink, Color.Black, sideTint = Sh.ink)
}

/** The time bar: elapsed and remaining with a length, elapsed alone without one. */
@Composable
private fun Progress(track: Track, fill: Color, text: Color, empty: Color) {
    val pos = track.positionMs.coerceAtLeast(0)
    if (track.durationMs > 0) {
        val p = pos.coerceAtMost(track.durationMs)
        Meter(p.toFloat() / track.durationMs, fill, height = 5.dp, track = empty)
        Gap(8.dp)
        Row(Modifier.fillMaxWidth()) {
            Txt(clock(p), Sh.text(13.sp, FontWeight.Medium, text), Modifier.weight(1f))
            Txt("−" + clock(track.durationMs - p), Sh.text(13.sp, FontWeight.Medium, text))
        }
    } else if (track.knowsPosition) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(empty))
            Gap(12.dp)
            Txt(clock(pos), Sh.text(13.sp, FontWeight.Medium, text))
        }
    }
}

@Composable
private fun Controls(track: Track, a: HomeActions, play: Color, onPlay: Color, sideTint: Color) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        RoundButton(ShIcons.previous, "Previous track", a.previous, size = 60.dp, iconSize = 28.dp, bg = Color.Transparent, tint = sideTint)
        RoundButton(if (track.playing) ShIcons.pause else ShIcons.play, if (track.playing) "Pause" else "Play", a.playPause,
            size = 72.dp, iconSize = 30.dp, bg = play, tint = onPlay)
        RoundButton(ShIcons.next, "Next track", a.next, size = 60.dp, iconSize = 28.dp, bg = Color.Transparent, tint = sideTint)
    }

internal fun clock(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

// ---- Vehicle ----

@Composable
private fun VehicleCard(t: Telemetry, allowed: Boolean, onSettings: () -> Unit, modifier: Modifier) = Panel(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Shark 6", Modifier.weight(1f))
        if (!allowed) Txt("Allow car data in Settings", Sh.text(14.sp, FontWeight.SemiBold, Sh.orange), Modifier.press(onSettings))
        else t.odometerKm?.let { Txt("%,d km".format(it.roundToInt()), Sh.text(14.sp, color = Sh.dim)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
        val soc = t.socPercent
        Box(Modifier.size(128.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 13.dp.toPx()
                val arc = Size(size.width - w, size.height - w)
                val o = Offset(w / 2, w / 2)
                drawArc(Sh.raised, -90f, 360f, false, o, arc, style = Stroke(w))
                if (soc != null) drawArc(Sh.green, -90f, 360f * (soc / 100f).toFloat(), false, o, arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(soc?.let { "${it.roundToInt()}%" } ?: "—", Sh.text(32.sp, FontWeight.Bold, spacing = (-1).sp))
                Txt("Battery", Sh.text(12.sp, color = Sh.dim))
            }
        }
        Column {
            Txt("Electric range", Sh.text(15.sp, color = Sh.dim))
            Txt(km(t.evRangeKm), Sh.text(40.sp, FontWeight.Bold, spacing = (-1).sp))
        }
    }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Bottom) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Fuel", km(t.fuelRangeKm), Modifier.weight(1f).fillMaxHeight()) {
                Meter(((t.fuelPercent ?: 0.0) / 100).toFloat(), Sh.orange)
            }
            StatTile("Total range", km(t.totalRangeKm), Modifier.weight(1f).fillMaxHeight()) {
                Txt("Battery + fuel", Sh.text(13.sp, color = Sh.dim))
            }
        }
    }
}

private fun km(v: Double?) = v?.let { "${it.roundToInt()} km" } ?: "— km"

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier, footer: @Composable () -> Unit) = Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(Sh.raised).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
) {
    Txt(label, Sh.text(14.sp, color = Sh.dim))
    Txt(value, Sh.text(24.sp, FontWeight.SemiBold))
    footer()
}

// ---- Tyres + off-road ----

@Composable
private fun TyresCard(t: Telemetry, limits: com.sharkos.launcher.car.TyreLimits, attitude: Attitude?, onOffroad: () -> Unit, modifier: Modifier) = Panel(modifier) {
    SectionLabel("Tyres")
    val warned = limits.check(t.tyres).map { it.corner }.toSet()
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in listOf(listOf(Corner.LF, Corner.RF), listOf(Corner.LR, Corner.RR))) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (c in row) {
                    val tyre = t.tyres.getOrNull(c.ordinal)
                    val warn = c in warned
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp))
                            .background(if (warn) Sh.orange.copy(alpha = 0.18f) else Sh.raised).padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Txt(c.short, Sh.text(13.sp, FontWeight.SemiBold, Sh.dim))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt(tyre?.let { "%.0f".format(it.psi) } ?: "—", Sh.text(30.sp, FontWeight.SemiBold, if (warn) Sh.orange else Sh.ink))
                            Txt(" psi", Sh.text(14.sp, color = Sh.dim))
                        }
                    }
                }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(18.dp)).background(Sh.raised).press(onOffroad, label = "Off-road").padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(ShIcons.mountain, Sh.orange, 22.dp)
        Gap(12.dp)
        Txt("Off-road", Sh.text(17.sp, FontWeight.SemiBold), Modifier.weight(1f))
        attitude?.let {
            Txt("Pitch ${deg(it.pitch)} · Roll ${deg(it.roll)}", Sh.text(15.sp, color = Sh.dim))
        }
    }
}

fun deg(v: Float): String {
    val r = v.roundToInt()
    return if (r < 0) "−${abs(r)}°" else "$r°"
}
