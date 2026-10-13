package com.sharkos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.CarLink

class SettingsInfo(
    val version: String,
    val isDefaultHome: Boolean,
    val mediaAccess: Boolean,
    val link: CarLink,
    val backend: String,
    val inclinoZeroed: Boolean,
    val autoLaunch: Boolean = true,
    val canLaunchFromBackground: Boolean = false,
)

class SettingsActions(
    val chooseHome: () -> Unit,
    val grantMedia: () -> Unit,
    val zeroInclino: () -> Unit,
    val back: () -> Unit,
    val setAutoLaunch: (Boolean) -> Unit = {},
    val allowOverlay: () -> Unit = {},
)

@Composable
fun SettingsScreen(s: SettingsInfo, a: SettingsActions) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).press(a.back, label = "Back to Home"), verticalAlignment = Alignment.CenterVertically) {
                Glyph(ShIcons.back, Sh.blue, 22.dp)
                Txt("Home", Sh.text(19.sp, FontWeight.Medium, Sh.blue))
            }
            Txt("Settings", Sh.text(24.sp, FontWeight.Bold, spacing = (-0.3).sp))
            Row(Modifier.weight(1f)) {}
        }
        Gap(16.dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Group("Home screen") {
                Item(
                    "Use SharkOS as the Home screen",
                    if (s.isDefaultHome) "SharkOS opens when you press Home." else "Choose SharkOS as the Home app so the Home button comes here.",
                    status = if (s.isDefaultHome) "On" to Sh.green else null,
                ) { if (!s.isDefaultHome) PillButton("Choose", a.chooseHome, bg = Sh.blue, fg = Color.White) }
                Hint("If the car doesn't offer a choice: adb shell cmd package set-home-activity com.sharkos.launcher/.MainActivity")
                Item(
                    "Open SharkOS when the car starts",
                    "Brings SharkOS up over BYD's home screen a few seconds after start-up. Works even if the car won't let SharkOS be the Home app.",
                ) { Switch(s.autoLaunch, a.setAutoLaunch, "Open SharkOS when the car starts") }
                if (s.autoLaunch) {
                    Item(
                        "Display over other apps",
                        "Android only lets an app open itself at start-up with this permission.",
                        status = if (s.canLaunchFromBackground) "Allowed" to Sh.green else "Needed" to Sh.orange,
                    ) { if (!s.canLaunchFromBackground) PillButton("Allow", a.allowOverlay, bg = Sh.blue, fg = Color.White) }
                    if (!s.canLaunchFromBackground) Hint("If that page won't open: adb shell appops set com.sharkos.launcher SYSTEM_ALERT_WINDOW allow")
                }
            }
            Group("Now playing") {
                Item(
                    "Media access",
                    "Lets the Home screen show and control what's playing. SharkOS reads the player only, never your notifications.",
                    status = if (s.mediaAccess) "Allowed" to Sh.green else "Off" to Sh.faint,
                ) { if (!s.mediaAccess) PillButton("Allow", a.grantMedia, bg = Sh.blue, fg = Color.White) }
                if (!s.mediaAccess) Hint("If the settings page won't open: adb shell cmd notification allow_listener com.sharkos.launcher/com.sharkos.launcher.media.MediaListener")
            }
            Group("Car") {
                Item(
                    "Vehicle connection",
                    when (s.link) {
                        CarLink.CONNECTED -> "Connected through ${s.backend}."
                        CarLink.SEARCHING -> "Looking for the car's climate and vehicle services…"
                        CarLink.OFFLINE -> "BYD's vehicle services weren't found. Controls still respond but nothing reaches the car."
                    },
                    status = when (s.link) {
                        CarLink.CONNECTED -> "Connected" to Sh.green
                        CarLink.SEARCHING -> "Searching" to Sh.orange
                        CarLink.OFFLINE -> "Offline" to Sh.faint
                    },
                )
                Item("Inclinometer", if (s.inclinoZeroed) "Zeroed for this screen position." else "Not zeroed yet: park on flat ground first.") {
                    PillButton(if (s.inclinoZeroed) "Re-zero" else "Zero now", a.zeroInclino)
                }
            }
            Group("About") {
                Item("SharkOS ${s.version}", "A light launcher for the BYD Shark 6.")
                Item(
                    "Credits",
                    "Car connection, climate mapping and inclinometer adapted from Shark Hub by Chris Murray (MIT licence). " +
                        "Figtree typeface by Erik Kennedy (SIL Open Font Licence).",
                )
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) = Column(Modifier.widthIn(max = 860.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel(title, Modifier.padding(start = 18.dp))
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Sh.card)) { content() }
}

@Composable
private fun Item(title: String, body: String, status: Pair<String, Color>? = null, action: @Composable () -> Unit = {}) =
    Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Txt(title, Sh.text(18.sp, FontWeight.SemiBold))
            Txt(body, Sh.text(15.sp, color = Sh.dim), maxLines = 3)
        }
        Gap(16.dp)
        status?.let { (t, c) -> Txt(t, Sh.text(15.sp, FontWeight.SemiBold, c)); Gap(12.dp) }
        action()
    }

@Composable
private fun Hint(text: String) =
    Txt(text, Sh.text(13.sp, color = Sh.dim).copy(fontFamily = FontFamily.Monospace), Modifier.padding(start = 22.dp, end = 22.dp, bottom = 16.dp), maxLines = 2)
