package com.sharkos.launcher.ui

import androidx.compose.foundation.background
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.sharkos.launcher.car.ClimateState
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
import com.sharkos.launcher.update.Updater

class SettingsInfo(
    val version: String,
    val isDefaultHome: Boolean,
    val mediaAccess: Boolean,
    val link: CarLink,
    val backend: String,
    val inclinoZeroed: Boolean,
    val autoLaunch: Boolean = true,
    val canLaunchFromBackground: Boolean = false,
    val update: Updater.State = Updater.State.Idle,
    val carDataAllowed: Boolean = true,
    val barStyle: BarStyle = BarStyle.BALANCED,
    val carPlayLinked: Boolean = false,
    /** What the HVAC Bar previews show: the car's real climate and the driver's own apps. */
    val climate: ClimateState = ClimateState(),
    val dock: List<DockApp> = emptyList(),
    val updatesConfigured: Boolean = true,
    val canInstall: Boolean = true,
    val wallpaper: Wallpaper = Wallpaper.BLACK,
    val corners: Corners = Corners.LIGHT,
    val tempSide: TempSide = TempSide.RIGHT,
)

class SettingsActions(
    val chooseHome: () -> Unit,
    val grantMedia: () -> Unit,
    val zeroInclino: () -> Unit,
    val back: () -> Unit,
    val setAutoLaunch: (Boolean) -> Unit = {},
    val allowOverlay: () -> Unit = {},
    val checkUpdate: () -> Unit = {},
    val allowCarData: () -> Unit = {},
    val setBarStyle: (BarStyle) -> Unit = {},
    val installUpdate: (Updater.Release) -> Unit = {},
    val allowInstalls: () -> Unit = {},
    val setWallpaper: (Wallpaper) -> Unit = {},
    val setTempSide: (TempSide) -> Unit = {},
    val setCorners: (Corners) -> Unit = {},
    val reconnectCar: () -> Unit = {},
    val resetBar: () -> Unit = {},
    val restart: () -> Unit = {},
    val androidSettings: () -> Unit = {},
    val lights: LightsActions = LightsActions(),
)

/**
 * Settings pages, listed down the right so they sit on the driver's side (right-hand drive). Add a
 * page here and give it a branch in [PageContent].
 */
enum class SettingsPage(val label: String, val icon: ImageVector, val tint: Color) {
    GENERAL("General", ShIcons.gear, Color(0xFF8E8E93)),
    APPEARANCE("Appearance", ShIcons.brush, Color(0xFFBF5AF2)),
    HVAC("HVAC Bar", ShIcons.fan, Sh.blue),
    MEDIA("Now playing", ShIcons.music, Color(0xFFFF375F)),
    VEHICLE("Vehicle", ShIcons.car, Sh.green),
    UPDATES("Updates", ShIcons.download, Color(0xFF5E5CE6)),
    AMBIENT("Ambient lighting", ShIcons.bulb, Color(0xFFFFB340)),
    SYSTEM("System", ShIcons.chip, Color(0xFF636366)),
    ABOUT("About", ShIcons.info, Color(0xFF8E8E93)),
}

@Composable
fun SettingsScreen(s: SettingsInfo, a: SettingsActions, start: SettingsPage = SettingsPage.GENERAL) {
    var page by rememberSaveable { mutableStateOf(start) }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 32.dp, end = 24.dp, top = 24.dp)) {
            Row(Modifier.height(48.dp).press(a.back, label = "Back to Home"), verticalAlignment = Alignment.CenterVertically) {
                Glyph(ShIcons.back, Sh.blue, 22.dp)
                Txt("Home", Sh.text(19.sp, FontWeight.Medium, Sh.blue))
            }
            Gap(4.dp)
            Txt(page.label, Sh.text(32.sp, FontWeight.Bold, spacing = (-0.5).sp), Modifier.padding(start = 4.dp))
            Gap(16.dp)
            // A fresh scroll position per page
            key(page) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) { PageContent(page, s, a) }
            }
        }
        Sidebar(page, s, onPick = { page = it }, Modifier.padding(top = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}

@Composable
private fun Sidebar(page: SettingsPage, s: SettingsInfo, onPick: (SettingsPage) -> Unit, modifier: Modifier) =
    Column(
        modifier.width(300.dp).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(Sh.card).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Txt("Settings", Sh.text(24.sp, FontWeight.Bold, spacing = (-0.3).sp), Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp))
        for (p in SettingsPage.entries) {
            val on = p == page
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Sh.blue else Color.Transparent)
                    .press({ onPick(p) }, label = p.label).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(p.tint), contentAlignment = Alignment.Center) {
                    Glyph(p.icon, Color.White, 20.dp)
                }
                Gap(14.dp)
                Txt(p.label, Sh.text(18.sp, FontWeight.Medium, if (on) Color.White else Sh.ink), Modifier.weight(1f))
                badge(p, s)?.let { (text, c) ->
                    Box(
                        Modifier.height(24.dp).widthIn(min = 24.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Color.White else c).padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt(text, Sh.text(13.sp, FontWeight.Bold, if (on) c else Color.White)) }
                }
            }
        }
    }

/** A page's badge in the sidebar: something new, or something that needs allowing. */
private fun badge(p: SettingsPage, s: SettingsInfo): Pair<String, Color>? = when (p) {
    SettingsPage.UPDATES -> when {
        s.update is Updater.State.Available -> "1" to Sh.red
        !s.canInstall -> "!" to Sh.orange
        else -> null
    }
    SettingsPage.GENERAL -> if (!s.isDefaultHome && s.autoLaunch && !s.canLaunchFromBackground) "!" to Sh.orange else null
    SettingsPage.MEDIA -> if (!s.mediaAccess) "!" to Sh.orange else null
    SettingsPage.VEHICLE -> if (!s.carDataAllowed) "!" to Sh.orange else null
    else -> null
}

@Composable
private fun PageContent(page: SettingsPage, s: SettingsInfo, a: SettingsActions) {
    when (page) {
        SettingsPage.GENERAL -> Group("Home screen") {
            Item(
                "Use SharkOS as the Home screen",
                if (s.isDefaultHome) "SharkOS opens when you press Home." else "Choose SharkOS as the Home app so the Home button comes here.",
                status = if (s.isDefaultHome) "On" to Sh.green else null,
            ) { if (!s.isDefaultHome) PillButton("Choose", a.chooseHome, bg = Sh.blue, fg = Color.White) }
            if (!s.isDefaultHome) Hint("If the car doesn't offer a choice: adb shell cmd package set-home-activity com.sharkos.launcher/.MainActivity")
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
        SettingsPage.APPEARANCE -> AppearancePage(s, a)
        SettingsPage.HVAC -> HvacPage(s, a)
        SettingsPage.MEDIA -> Group("Sources") {
            Item(
                "Media access",
                "Lets the Home screen show and control what's playing in apps like Spotify. SharkOS reads the player only, never your notifications.",
                status = if (s.mediaAccess) "Allowed" to Sh.green else "Off" to Sh.faint,
            ) { if (!s.mediaAccess) PillButton("Allow", a.grantMedia, bg = Sh.blue, fg = Color.White) }
            if (!s.mediaAccess) Hint("If the settings page won't open: adb shell cmd notification allow_listener com.sharkos.launcher/com.sharkos.launcher.media.MediaListener")
            Item(
                "CarPlay music",
                "Song, artwork and time come straight from the car's CarPlay service.",
                status = if (s.carPlayLinked) "Connected" to Sh.green else "Not found" to Sh.faint,
            )
        }
        SettingsPage.VEHICLE -> {
            Group("Car data") {
                Item(
                    "Car data access",
                    "Battery, electric range, fuel and total range come from BYD's statistics service, which needs this one permission.",
                    status = if (s.carDataAllowed) "Allowed" to Sh.green else "Needed" to Sh.orange,
                ) { if (!s.carDataAllowed) PillButton("Allow", a.allowCarData, bg = Sh.blue, fg = Color.White) }
                if (!s.carDataAllowed) Hint("If no prompt appears: adb shell pm grant com.sharkos.launcher android.permission.BYDAUTO_STATISTIC_COMMON")
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
            }
            Group("Off-road") {
                Item("Inclinometer", if (s.inclinoZeroed) "Zeroed for this screen position." else "Not zeroed yet: park on flat ground first.") {
                    PillButton(if (s.inclinoZeroed) "Re-zero" else "Zero now", a.zeroInclino)
                }
            }
        }
        SettingsPage.UPDATES -> Group("Over the air") { Updates(s, a) }
        SettingsPage.AMBIENT -> AmbientPage(a.lights)
        SettingsPage.SYSTEM -> SystemPage(a)
        SettingsPage.ABOUT -> {
            Group("SharkOS") {
                Item("Version", "SharkOS ${s.version}, a light launcher for the BYD Shark 6.")
                Item("Made for", "BYD Shark 6, right-hand drive, Desay SV head unit (Android 11).")
            }
            Group("Licences") {
                Item("Shark Hub", "Car connection, climate mapping and inclinometer adapted from Shark Hub by Chris Murray. MIT licence.")
                Item("Figtree", "Typeface by Erik Kennedy. SIL Open Font Licence 1.1.")
                Item("AndroidX and Jetpack Compose", "Google. Apache licence 2.0.")
                Item("Kotlin", "JetBrains. Apache licence 2.0.")
            }
        }
    }
}

@Composable
private fun AppearancePage(s: SettingsInfo, a: SettingsActions) {
    Group("Background") {
        // Thumbnails are the real backgrounds, drawn small
        val rows = Wallpaper.entries.chunked(4)
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            for (row in rows) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (w in row) {
                    val on = w == s.wallpaper
                    Column(Modifier.weight(1f).press({ a.setWallpaper(w) }, label = "${w.label} background"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                                .then(if (on) Modifier.border(3.dp, Sh.blue, RoundedCornerShape(14.dp)) else Modifier.border(1.dp, Sh.line, RoundedCornerShape(14.dp)))
                                .padding(if (on) 5.dp else 1.dp)
                                .clip(RoundedCornerShape(if (on) 10.dp else 13.dp))
                                .wallpaper(w),
                        )
                        Gap(8.dp)
                        Txt(w.label, Sh.text(15.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) Sh.blue else Sh.ink))
                    }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
    Group("Corners") {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Txt("Rounds the outside edge of SharkOS, like a phone screen.", Sh.text(15.sp, color = Sh.dim))
            Segmented(Corners.entries.map { it.label }, s.corners.ordinal, { a.setCorners(Corners.entries[it]) }, Modifier.fillMaxWidth(), greyFirst = false)
        }
    }
}

/** Which side the temperature goes, shown both ways; then each layout drawn small. Tap to use. */
@Composable
private fun HvacPage(s: SettingsInfo, a: SettingsActions) {
    Txt("Pick the bar along the bottom of every screen. One cabin temperature keeps both sides in sync.", Sh.text(16.sp, color = Sh.dim), maxLines = 2)
    SectionLabel("Temperature side", Modifier.padding(start = 18.dp))
    for (side in listOf(TempSide.RIGHT, TempSide.LEFT)) {
        Choice(
            title = "Temperature on the ${side.label.lowercase()}",
            blurb = if (side == TempSide.RIGHT) "Fan on the left, temperature by the driver." else "Temperature on the left, fan by the driver.",
            on = side == s.tempSide,
            onPick = { a.setTempSide(side) },
        ) { BarPreview(s.barStyle, side, s) }
    }
    SectionLabel("Layout", Modifier.padding(start = 18.dp, top = 8.dp))
    for (style in BarStyle.entries) {
        Choice(style.label, style.blurb, style == s.barStyle, { a.setBarStyle(style) }) { BarPreview(style, s.tempSide, s) }
    }
}

@Composable
private fun Choice(title: String, blurb: String, on: Boolean, onPick: () -> Unit, preview: @Composable () -> Unit) =
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Sh.card)
            .then(if (on) Modifier.border(2.dp, Sh.blue, RoundedCornerShape(22.dp)) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Txt(title, Sh.text(18.sp, FontWeight.SemiBold))
                Txt(blurb, Sh.text(14.sp, color = Sh.dim), maxLines = 2)
            }
            Gap(16.dp)
            if (on) Txt("In use", Sh.text(15.sp, FontWeight.SemiBold, Sh.blue))
            else PillButton("Use", onPick)
        }
        preview()
    }

private val PreviewLights = BarLights(connected = true, on = true, brightness = 100, hue = 0.62f)

private val NoBar = BarActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

/** The real bar, drawn at the width of the screen and shrunk to fit; touches go to [Use], not the bar. */
@Composable
private fun BarPreview(style: BarStyle, side: TempSide, s: SettingsInfo) = BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
    val scale = maxWidth / 1280.dp
    val d = LocalDensity.current
    Box(Modifier.fillMaxWidth().height(88.dp * scale)) {
        CompositionLocalProvider(LocalDensity provides Density(d.density * scale, d.fontScale)) {
            BottomBar(s.climate, s.dock, Screen.HOME, NoBar, style = style, tempSide = side, lights = PreviewLights)
        }
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } })
    }
}

@Composable
private fun SystemPage(a: SettingsActions) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val dm = ctx.resources.displayMetrics
    // Read once when the page opens: nothing here polls.
    val memMb = remember {
        runCatching {
            val am = ctx.getSystemService(android.app.ActivityManager::class.java)
            am.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))[0].totalPss / 1024
        }.getOrNull()
    }
    Group("Head unit") {
        Item("Android", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        Item("Firmware", Build.DISPLAY.ifBlank { "Unknown" })
        Item("Screen", "${dm.widthPixels} × ${dm.heightPixels} px at ${dm.densityDpi} dpi (${config.screenWidthDp} × ${config.screenHeightDp} dp)")
        if (memMb != null && memMb > 0) Item("SharkOS memory", "$memMb MB in use right now.")
    }
    Group("Tools") {
        Item("Reconnect to the car", "Re-opens BYD's climate and vehicle services if readings stop updating.") { PillButton("Reconnect", a.reconnectCar) }
        Item("Reset the bar's apps", "Puts the bar's app spots back to Music, Phone and 360 View.") { PillButton("Reset", a.resetBar) }
        Item("Restart SharkOS", "Closes and reopens SharkOS. Nothing you've set is lost.") { PillButton("Restart", a.restart) }
        Item("Android settings", "The head unit's own Android settings, where BYD allows them.") { PillButton("Open", a.androidSettings) }
    }
}

@Composable
private fun Updates(s: SettingsInfo, a: SettingsActions) {
    if (!s.updatesConfigured) {
        Item("Over-the-air updates", "The update address hasn't been set in this build yet.")
        return
    }
    when (val u = s.update) {
        is Updater.State.Available -> Item(
            "SharkOS ${u.r.versionName} is available",
            u.r.notes.ifBlank { "A newer version is ready to install." },
            status = "New" to Sh.blue,
        ) { PillButton("Update", { a.installUpdate(u.r) }, bg = Sh.blue, fg = Color.White) }
        is Updater.State.Downloading -> Item("Downloading update", "${u.pct}%") {}
        Updater.State.Installing -> Item("Installing", "Confirm on screen if Android asks. SharkOS reopens by itself.") {}
        Updater.State.Checking -> Item("Checking for updates…", "SharkOS ${s.version}") {}
        Updater.State.UpToDate -> Item("SharkOS is up to date", "Version ${s.version}", status = "Latest" to Sh.green) {
            PillButton("Check again", a.checkUpdate)
        }
        is Updater.State.Failed -> Item("Update", u.message, status = "Problem" to Sh.orange) { PillButton("Try again", a.checkUpdate) }
        Updater.State.Idle -> Item("Over-the-air updates", "Version ${s.version}. SharkOS also checks once a day by itself.") {
            PillButton("Check now", a.checkUpdate)
        }
    }
    if (!s.canInstall) {
        Item("Allow updates to install", "Android asks once before an app may install its own updates.", status = "Needed" to Sh.orange) {
            PillButton("Allow", a.allowInstalls, bg = Sh.blue, fg = Color.White)
        }
        Hint("If that page won't open: adb shell appops set com.sharkos.launcher REQUEST_INSTALL_PACKAGES allow")
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) = Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

// For pages kept in their own files (Ambient lighting)
@Composable internal fun SettingsGroup(title: String, content: @Composable () -> Unit) = Group(title, content)
@Composable internal fun SettingsItem(title: String, body: String, status: Pair<String, Color>? = null, action: @Composable () -> Unit = {}) = Item(title, body, status, action)
@Composable internal fun SettingsHint(text: String) = Hint(text)
