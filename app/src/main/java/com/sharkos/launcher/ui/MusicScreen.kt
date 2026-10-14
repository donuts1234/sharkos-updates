package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.media.Track

/**
 * Full-screen now playing ("Fade"): the album art fills the left and fades into its own dark tone;
 * title, artist, time bar and controls sit on the right. Opened by tapping the Home music card.
 * The HVAC Bar below takes the same colours (see [BottomBar]'s art tint).
 */
@Composable
fun MusicScreen(
    track: Track?,
    a: HomeActions,
    onHome: () -> Unit,
    /** Ambient lighting follows the album art; null when no lights are set up. */
    lightsFollowArt: Boolean? = null,
    onLightsFollowArt: (Boolean) -> Unit = {},
    /** Open with the settings panel showing (screenshots). */
    showSettings: Boolean = false,
) {
    var settingsOpen by remember { mutableStateOf(showSettings) }
    val art = track?.art
    val colors = remember(art) { art?.let { artColors(it) } ?: ArtColors(Sh.ink, Color.Black, Color(0xFF141416)) }
    val accent by animateColorAsState(colors.accent, tween(500), label = "accent")
    val shade by animateColorAsState(colors.shade, tween(500), label = "shade")
    val image = remember(art) { art?.asImageBitmap() }
    val soft = lerp(Color.White, accent, 0.28f)

    Box(Modifier.fillMaxSize().background(shade)) {
        if (image != null) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.58f)) {
                Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(0.35f to Color.Transparent, 0.7f to shade.copy(alpha = 0.7f), 1f to shade),
                    ),
                )
            }
        } else {
            Box(Modifier.fillMaxHeight().fillMaxWidth(0.58f), contentAlignment = Alignment.Center) {
                Glyph(ShIcons.music, Sh.faint, 120.dp)
            }
        }
        // A warm glow of the art's colour from the top-right corner
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), center = Offset(Float.POSITIVE_INFINITY, 0f), radius = 900f),
            ),
        )
        Row(
            Modifier.padding(20.dp).height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color.Black.copy(alpha = 0.45f))
                .press(onHome, label = "Close and go back Home").padding(start = 10.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Glyph(ShIcons.chevronDown, Color.White, 20.dp)
            Txt("Home", Sh.text(16.sp, FontWeight.SemiBold, Color.White))
        }

        // The page's own settings: a small cog in the top-right corner
        RoundButton(ShIcons.gear, "Music page settings", { settingsOpen = !settingsOpen }, size = 44.dp, iconSize = 20.dp,
            bg = Color.Black.copy(alpha = 0.45f), tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(20.dp))

        Column(
            Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.45f).padding(start = 24.dp, end = 56.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            if (track == null) {
                Txt("Nothing playing", Sh.text(44.sp, FontWeight.ExtraBold, Color.White, spacing = (-1).sp))
                Txt("Start something in CarPlay or a music app.", Sh.text(20.sp, FontWeight.Medium, soft))
                return@Column
            }
            Box(Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White.copy(alpha = 0.14f)).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                Txt((if (track.playing) track.source else "${track.source} · Paused").uppercase(), Sh.text(12.sp, FontWeight.SemiBold, Color.White, 0.4.sp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt(track.title.ifBlank { "Unknown track" }, Sh.text(44.sp, FontWeight.ExtraBold, Color.White, spacing = (-1).sp), maxLines = 2)
                Txt(track.artist, Sh.text(22.sp, FontWeight.Medium, soft))
            }
            Column {
                if (track.durationMs > 0) {
                    val p = track.positionMs.coerceIn(0, track.durationMs)
                    Meter(p.toFloat() / track.durationMs, accent, height = 7.dp, track = Color.White.copy(alpha = 0.22f))
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Txt(clock(p), Sh.text(15.sp, FontWeight.Medium, soft), Modifier.weight(1f))
                        Txt("−" + clock(track.durationMs - p), Sh.text(15.sp, FontWeight.Medium, soft))
                    }
                } else if (track.knowsPosition) {
                    Txt(clock(track.positionMs.coerceAtLeast(0)), Sh.text(15.sp, FontWeight.Medium, soft))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(ShIcons.previous, "Previous track", a.previous, size = 72.dp, iconSize = 36.dp, bg = Color.Transparent, tint = Color.White)
                RoundButton(if (track.playing) ShIcons.pause else ShIcons.play, if (track.playing) "Pause" else "Play", a.playPause,
                    size = 92.dp, iconSize = 40.dp, bg = Color.White, tint = shade)
                RoundButton(ShIcons.next, "Next track", a.next, size = 72.dp, iconSize = 36.dp, bg = Color.Transparent, tint = Color.White)
            }
        }
        if (settingsOpen) {
            // Tap anywhere else to close
            Box(Modifier.fillMaxSize().press({ settingsOpen = false }, label = "Close music page settings"))
            Column(
                Modifier.align(Alignment.TopEnd).padding(top = 74.dp, end = 20.dp).width(380.dp)
                    .clip(RoundedCornerShape(22.dp)).background(Color(0xF21C1C1E)).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Txt("Lights match album art", Sh.text(18.sp, FontWeight.SemiBold, Color.White))
                        Txt(
                            if (lightsFollowArt == null) "Set up Ambient lighting in Settings first." else "Ambient lighting takes each cover's colour.",
                            Sh.text(14.sp, color = Sh.dim), maxLines = 2,
                        )
                    }
                    Gap(12.dp)
                    if (lightsFollowArt != null) Switch(lightsFollowArt, onLightsFollowArt, "Lights match album art")
                }
            }
        }
    }
}
