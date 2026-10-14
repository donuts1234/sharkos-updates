package com.sharkos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.apps.AppEntry

/** Every installed app, A to Z. Hold an app to add it to (or take it off) the bottom bar. */
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    pinned: List<String>,
    onOpen: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onSettings: () -> Unit,
    /** True when opened from a + spot on the bar: a tap adds the app instead of opening it. */
    picking: Boolean = false,
    onCancelPick: () -> Unit = {},
    /** What the pick is for, shown as the title. */
    pickTitle: String = "Add to the bar",
) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (picking) {
                Txt(pickTitle, Sh.text(30.sp, FontWeight.Bold, spacing = (-0.5).sp), Modifier.weight(1f))
                Txt("Tap an app to put it in the empty spot", Sh.text(15.sp, color = Sh.dim))
                Gap(16.dp)
                PillButton("Cancel", onCancelPick)
            } else {
                Txt("Apps", Sh.text(30.sp, FontWeight.Bold, spacing = (-0.5).sp), Modifier.weight(1f))
                Txt("Hold an app to add it to the bar or take it off", Sh.text(15.sp, color = Sh.dim))
                Gap(16.dp)
                RoundButton(ShIcons.gear, "SharkOS settings", onSettings, size = 44.dp, iconSize = 20.dp, bg = Sh.card, tint = Sh.dim)
            }
        }
        Gap(12.dp)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(132.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(apps, key = { it.pkg }) { app ->
                val isPinned = app.pkg in pinned
                Column(
                    Modifier.padding(4.dp).clip(RoundedCornerShape(20.dp))
                        .press({ if (picking) onTogglePin(app.pkg) else onOpen(app.pkg) }, onLongClick = { onTogglePin(app.pkg) }, label = app.label)
                        .padding(vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box {
                        AppIcon(app.pkg, app.label, 68.dp)
                        if (isPinned) Box(Modifier.align(Alignment.TopEnd).size(14.dp).clip(RoundedCornerShape(7.dp)).background(Sh.blue))
                    }
                    Txt(app.label, Sh.text(14.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center), Modifier.fillMaxWidth().padding(horizontal = 6.dp))
                }
            }
        }
    }
}
