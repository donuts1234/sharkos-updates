package com.sharkos.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.apps.Apps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Where app icons come from: the real package manager on the car, nothing in previews. */
fun interface IconSource { fun load(pkg: String, px: Int): ImageBitmap? }

val LocalIcons = compositionLocalOf { IconSource { _, _ -> null } }

fun Apps.iconSource() = IconSource { pkg, px -> icon(pkg, px)?.asImageBitmap() }

/** An app's own icon, decoded off the main thread; a letter tile until (or unless) it arrives. */
@Composable
fun AppIcon(pkg: String, label: String, size: Dp, modifier: Modifier = Modifier) {
    val source = LocalIcons.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bmp by produceState<ImageBitmap?>(null, pkg, px) { value = withContext(Dispatchers.IO) { source.load(pkg, px) } }
    val b = bmp
    if (b != null) {
        Image(b, label, modifier.size(size))
    } else {
        Box(
            modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(Sh.raised),
            contentAlignment = Alignment.Center,
        ) {
            Txt(label.take(1).uppercase(), Sh.text((size.value * 0.42f).sp, FontWeight.SemiBold, Sh.dim))
        }
    }
}
