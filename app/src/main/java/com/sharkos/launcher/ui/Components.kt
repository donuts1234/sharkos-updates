package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Tap feedback the iOS way: the control dims and shrinks a touch while held. No ripple. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.press(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    label: String? = null,
    role: Role = Role.Button,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, tween(120), label = "press")
    Modifier
        .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (pressed) 0.7f else 1f }
        .then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier)
        .combinedClickable(interactionSource = source, indication = null, role = role, onLongClick = onLongClick, onClick = onClick)
}

@Composable
fun Txt(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = 1) =
    BasicText(text, modifier, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines)

@Composable
fun Glyph(icon: ImageVector, tint: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) =
    Image(rememberVectorPainter(icon), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))

/** The rounded grey card everything sits on. */
@Composable
fun Panel(modifier: Modifier = Modifier, padding: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier.clip(RoundedCornerShape(28.dp)).background(Sh.card).padding(padding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content,
    )

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Sh.dim) =
    Txt(text.uppercase(), Sh.text(13.sp, FontWeight.SemiBold, color, 0.4.sp), modifier)

/** A round icon button. */
@Composable
fun RoundButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
    bg: Color = Sh.raised,
    tint: Color = Sh.ink,
    modifier: Modifier = Modifier,
) = Box(
    modifier.size(size).clip(RoundedCornerShape(size / 2)).background(bg).press(onClick, label = label),
    contentAlignment = Alignment.Center,
) { Glyph(icon, tint, iconSize) }

/** A tile that is either on (blue) or off (grey), with an optional icon above its label. */
@Composable
fun ToggleTile(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, height: Dp = 64.dp) {
    val bg by animateColorAsState(if (on) Sh.blue else Sh.raised, tween(180), label = "tile")
    Row(
        modifier.height(height).clip(RoundedCornerShape(18.dp)).background(bg)
            .press(onClick, label = "$label ${if (on) "on" else "off"}").padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Glyph(icon, if (on) Color.White else Sh.ink, 20.dp)
        Txt(label, Sh.text(16.sp, FontWeight.SemiBold, if (on) Color.White else Sh.ink))
    }
}

/** iOS segmented control. [accent] fills the chosen segment; index 0 ("Off") uses grey. */
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Sh.blue,
    onAccent: Color = Color.White,
    greyFirst: Boolean = true,
    height: Dp = 48.dp,
) = Row(
    modifier.clip(RoundedCornerShape(16.dp)).background(Sh.raised).padding(4.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
) {
    options.forEachIndexed { i, label ->
        val on = i == selected
        val fill = when {
            !on -> Color.Transparent
            i == 0 && greyFirst -> Sh.faint
            else -> accent
        }
        val bg by animateColorAsState(fill, tween(180), label = "seg")
        Box(
            Modifier.weight(1f).height(height).clip(RoundedCornerShape(12.dp)).background(bg)
                .press({ onSelect(i) }, label = label, role = Role.RadioButton),
            contentAlignment = Alignment.Center,
        ) {
            Txt(label, Sh.text(15.sp, FontWeight.SemiBold, if (on && !(i == 0 && greyFirst)) onAccent else Sh.ink))
        }
    }
}

/** iOS-style on/off switch. */
@Composable
fun Switch(on: Boolean, onChange: (Boolean) -> Unit, label: String) {
    val track by animateColorAsState(if (on) Sh.green else Sh.line, tween(180), label = "track")
    val knob by animateDpAsState(if (on) 24.dp else 2.dp, tween(180), label = "knob")
    Box(
        Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(15.dp)).background(track)
            .press({ onChange(!on) }, label = label, role = Role.Switch),
    ) {
        Box(Modifier.offset(x = knob, y = 2.dp).size(26.dp).clip(RoundedCornerShape(13.dp)).background(Color.White))
    }
}

/** A thin progress bar. */
@Composable
fun Meter(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 5.dp, track: Color = Sh.line) =
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(height / 2)).background(color))
    }

/** A pill-shaped grey button with text. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, bg: Color = Sh.raised, fg: Color = Sh.ink) =
    Box(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(bg).press(onClick).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) { Txt(text, Sh.text(16.sp, FontWeight.SemiBold, fg)) }

@Composable
fun Gap(size: Dp) = Spacer(Modifier.size(size))
