package com.sharkos.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.sharkos.launcher.apps.AppEntry
import com.sharkos.launcher.car.Airflow
import com.sharkos.launcher.car.CarLink
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.SeatClimate
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.car.Tyre
import com.sharkos.launcher.media.Track
import com.sharkos.launcher.sensors.Attitude
import com.sharkos.launcher.ui.AppsScreen
import com.sharkos.launcher.ui.BarActions
import com.sharkos.launcher.ui.BottomBar
import com.sharkos.launcher.ui.SettingsPage
import com.sharkos.launcher.ui.wallpaper
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.ui.ClimateActions
import com.sharkos.launcher.ui.ClimateScreen
import com.sharkos.launcher.ui.DockApp
import com.sharkos.launcher.ui.HomeActions
import com.sharkos.launcher.ui.HomeScreen
import com.sharkos.launcher.ui.OffroadScreen
import com.sharkos.launcher.ui.Screen
import com.sharkos.launcher.ui.SettingsActions
import com.sharkos.launcher.ui.SettingsInfo
import com.sharkos.launcher.ui.SettingsScreen
import com.sharkos.launcher.ui.Sh
import org.junit.Rule
import org.junit.Test

/**
 * Renders each screen at the Shark 6 head unit's size (1920x1080 @ 240 dpi) on the JVM, so the
 * layout can be checked without the car. Sample values only; nothing here reaches a car.
 * Run: ./gradlew recordPaparazziDebug  (PNGs land in app/src/test/snapshots/images)
 */
class Snapshots {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1920, screenHeight = 1080, xdpi = 240, ydpi = 240,
            density = Density.HIGH, orientation = ScreenOrientation.LANDSCAPE,
        ),
        theme = "android:Theme.DeviceDefault.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private val climate = ClimateState(
        auto = true, driverTemp = 22f, passengerTemp = 21f, fan = 3, airflow = Airflow.FACE_FEET,
        driverSeat = SeatClimate(heat = 2), passengerSeat = SeatClimate(vent = 1), dual = true,
    )
    private val telemetry = Telemetry(
        socPercent = 64.0, evRangeKm = 48.0, fuelPercent = 61.0, fuelRangeKm = 422.0, totalRangeKm = 470.0,
        odometerKm = 21883.0, speedKph = 0.0, outsideTempC = 14.0,
        tyres = listOf(Tyre(37.9, 0), Tyre(37.9, 0), Tyre(41.3, 0), Tyre(41.6, 0)),
    )
    // The out-of-the-box bar: three BYD apps and two empty + spots
    private val dock = listOf(DockApp("music", "Music"), DockApp("phone", "Phone"), DockApp("avm", "360 View"))
    private val bar = BarActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val home = HomeActions({}, {}, {}, {}, {}, {}, {})
    private val track = Track("Sample Song Title", "Artist Name", "Bluetooth audio", null, true, 102_000, 238_000, 0)
    // 9:41 on Tuesday 13 October 2026 (local), fixed so the clock doesn't change between runs
    private val now = java.util.Calendar.getInstance().apply { set(2026, 9, 13, 9, 41, 0) }.timeInMillis

    @Composable
    private fun Frame(
        screen: Screen, c: ClimateState = climate, style: com.sharkos.launcher.ui.BarStyle = com.sharkos.launcher.ui.BarStyle.BALANCED,
        side: com.sharkos.launcher.ui.TempSide = com.sharkos.launcher.ui.TempSide.RIGHT,
        lights: com.sharkos.launcher.ui.BarLights? = com.sharkos.launcher.ui.BarLights(true, true, 100, 0.62f),
        showLights: Boolean = false,
        wallpaper: com.sharkos.launcher.ui.Wallpaper = com.sharkos.launcher.ui.Wallpaper.BLACK,
        art: com.sharkos.launcher.ui.ArtColors? = null, content: @Composable () -> Unit,
    ) =
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).wallpaper(wallpaper),
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            androidx.compose.runtime.CompositionLocalProvider(com.sharkos.launcher.ui.LocalWallpaper provides wallpaper) {
                BottomBar(c, dock, screen, bar, style = style, tempSide = side, lights = lights, showLights = showLights, art = art)
            }
        }



    @Test fun homeSliderBar() = paparazzi.snapshot {
        Frame(Screen.HOME, style = com.sharkos.launcher.ui.BarStyle.SLIDER) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }


    @Test fun homeMirroredBar() = paparazzi.snapshot {
        Frame(Screen.HOME, style = com.sharkos.launcher.ui.BarStyle.MIRRORED) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun home() = paparazzi.snapshot {
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun homeCarPlaySong() = paparazzi.snapshot {
        val t = Track("My Universe", "Coldplay", "CarPlay", null, true, 61_000, 227_000, 0, carPlay = true)
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, t, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun homeNoMediaAccess() = paparazzi.snapshot {
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, null, false, Attitude(0f, 0f), home) }
    }

    @Test fun homeOffline() = paparazzi.snapshot {
        Frame(Screen.HOME, ClimateState()) { HomeScreen(now, Telemetry(), CarLink.OFFLINE, null, true, null, home) }
    }

    @Test fun climate() = paparazzi.snapshot {
        Frame(Screen.CLIMATE) {
            ClimateScreen(climate, outsideC = 14.0, a = ClimateActions({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}))
        }
    }

    @Test fun climateOff() = paparazzi.snapshot {
        val off = climate.copy(power = false)
        Frame(Screen.CLIMATE, off) {
            ClimateScreen(off, ClimateActions({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}))
        }
    }

    @Test fun apps() = paparazzi.snapshot {
        val names = listOf("360 View", "Bluetooth", "Calendar", "Car Settings", "Files", "Maps", "Music", "Phone",
            "Photos", "Radio", "Rage Mode", "Scene Modes", "Spotify", "Weather", "YouTube")
        val list = names.map { AppEntry(it, it.lowercase().replace(" ", ""), "Main") }
        Frame(Screen.APPS) { AppsScreen(list, listOf("maps", "music"), {}, {}, {}) }
    }

    @Test fun appsPicking() = paparazzi.snapshot {
        val names = listOf("360 View", "Bluetooth", "Car Settings", "Maps", "Music", "Phone", "Spotify", "YouTube")
        val list = names.map { AppEntry(it, it.lowercase().replace(" ", ""), "Main") }
        Frame(Screen.APPS) { AppsScreen(list, listOf("music", "phone", "360view"), {}, {}, {}, picking = true) }
    }

    @Test fun settingsUpdateAvailable() = paparazzi.snapshot {
        Frame(Screen.SETTINGS) {
            val r = com.sharkos.launcher.update.Updater.Release(5, "0.1.4", "https://example.invalid/a.apk", "CarPlay in Now Playing, and fixes.")
            SettingsScreen(
                SettingsInfo("0.1.3", true, true, CarLink.CONNECTED, "bydauto (BydHvac.apk)", true, autoLaunch = true, canLaunchFromBackground = true,
                    update = com.sharkos.launcher.update.Updater.State.Available(r)),
                SettingsActions({}, {}, {}, {}),
                start = SettingsPage.UPDATES,
            )
        }
    }

    private fun settingsPage(page: SettingsPage) = paparazzi.snapshot {
        Frame(Screen.SETTINGS) {
            SettingsScreen(
                SettingsInfo("0.1.9", true, true, CarLink.CONNECTED, "bydauto (BydHvac.apk)", true, autoLaunch = true, canLaunchFromBackground = true,
                    carPlayLinked = true, climate = climate, dock = dock),
                SettingsActions({}, {}, {}, {}),
                start = page,
            )
        }
    }

    @Test fun settingsAppearance() = paparazzi.snapshot {
        Frame(Screen.SETTINGS, wallpaper = com.sharkos.launcher.ui.Wallpaper.AURORA) {
            SettingsScreen(
                SettingsInfo("0.1.9", true, true, CarLink.CONNECTED, "bydauto (BydHvac.apk)", true, wallpaper = com.sharkos.launcher.ui.Wallpaper.AURORA),
                SettingsActions({}, {}, {}, {}),
                start = SettingsPage.APPEARANCE,
            )
        }
    }

    /** A stand-in cover: a sunset-orange gradient with a dark band, drawn in code. */
    private fun cover(top: Int, bottom: Int): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(400, 400, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, 400f, 400f, top, bottom, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 400f, 400f, p)
        p.shader = null; p.color = 0x55000000
        c.drawCircle(280f, 150f, 90f, p)
        return b
    }

    @Test fun homeAlbumArtWarm() = paparazzi.snapshot {
        val t = Track("Golden Hour", "Sample Artist", "CarPlay", cover(0xFFFF8A3D.toInt(), 0xFF7A1F5C.toInt()), true, 61_000, 227_000, 0, carPlay = true)
        Frame(Screen.HOME, wallpaper = com.sharkos.launcher.ui.Wallpaper.SUNSET) { HomeScreen(now, telemetry, CarLink.CONNECTED, t, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun homeAlbumArtCool() = paparazzi.snapshot {
        val t = Track("Blue Water", "Sample Artist", "Spotify", cover(0xFF2AD4C8.toInt(), 0xFF0A2A6B.toInt()), false, 95_000, 201_000, 0)
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, t, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun musicPage() = paparazzi.snapshot {
        val art = cover(0xFFFF8A3D.toInt(), 0xFF7A1F5C.toInt())
        val t = Track("Golden Hour", "Sample Artist", "CarPlay", art, true, 84_000, 222_000, 0, carPlay = true)
        Frame(Screen.MUSIC, art = com.sharkos.launcher.ui.artColors(art)) { com.sharkos.launcher.ui.MusicScreen(t, home, onHome = {}) }
    }

    @Test fun musicPageSettings() = paparazzi.snapshot {
        val art = cover(0xFF2AD4C8.toInt(), 0xFF0A2A6B.toInt())
        val t = Track("Blue Water", "Sample Artist", "Spotify", art, false, 95_000, 201_000, 0)
        Frame(Screen.MUSIC, art = com.sharkos.launcher.ui.artColors(art)) {
            com.sharkos.launcher.ui.MusicScreen(t, home, onHome = {}, lightsFollowArt = true, showSettings = true)
        }
    }

    @Test fun wallpapersAll() = paparazzi.snapshot {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        ) {
            for (row in com.sharkos.launcher.ui.Wallpaper.entries.drop(7).chunked(4)) androidx.compose.foundation.layout.Row(
                Modifier.weight(1f), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                for (w in row) Box(Modifier.weight(1f).fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).wallpaper(w)) {
                    com.sharkos.launcher.ui.Txt(w.label, com.sharkos.launcher.ui.Sh.text(18.sp, androidx.compose.ui.text.font.FontWeight.SemiBold), Modifier.padding(14.dp))
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }

    @Test fun homeTyreAlert() = paparazzi.snapshot {
        val t = telemetry.copy(tyres = listOf(Tyre(37.9, 0), Tyre(38.2, 0), Tyre(27.6, 0), Tyre(41.6, 0)))
        val limits = com.sharkos.launcher.car.TyreLimits()
        Frame(Screen.HOME) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(now, t, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home, tyreLimits = limits)
                com.sharkos.launcher.ui.TyreAlert(limits.check(t.tyres), {}, Modifier.align(androidx.compose.ui.Alignment.TopCenter).padding(top = 16.dp))
            }
        }
    }

    @Test fun tripsPage() = paparazzi.snapshot {
        val t = com.sharkos.launcher.car.Trips(
            drive = com.sharkos.launcher.car.Drive(42.6, 1_760_392_680_000, 47, 54.4, 18.0, 5.3, 0.78),
            trip1 = com.sharkos.launcher.car.TripMeter(186.4, avgKph = 48.0),
            trip2 = com.sharkos.launcher.car.TripMeter(1204.0, 1450, 50.0, 20.4, 2.6),
            lifetime = com.sharkos.launcher.car.Lifetime(21883.0, 14960.0, 6923.0, 21.1, 3.1),
        )
        Frame(Screen.TRIPS) { com.sharkos.launcher.ui.TripsScreen(t, onHome = {}) }
    }

    @Test fun homeOtherCards() = paparazzi.snapshot {
        val ex = com.sharkos.launcher.ui.HomeExtras(
            drive = com.sharkos.launcher.car.Drive(42.6, 0, 47, 54.0, 18.0, 5.3, 0.78),
            climate = climate,
        )
        Frame(Screen.HOME) {
            HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home,
                cards = listOf(com.sharkos.launcher.ui.HomeCard.NOW_PLAYING, com.sharkos.launcher.ui.HomeCard.DRIVE, com.sharkos.launcher.ui.HomeCard.CLIMATE), extras = ex)
        }
    }

    @Test fun homeLightsShortcuts() = paparazzi.snapshot {
        val ex = com.sharkos.launcher.ui.HomeExtras(
            lights = com.sharkos.launcher.ui.BarLights(true, true, 70, 0.75f), lightsNote = "Matching album art",
            shortcuts = listOf(DockApp("carplay", "CarPlay"), DockApp("music", "Music"), DockApp("avm", "360 View"), null),
        )
        Frame(Screen.HOME) {
            HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home,
                cards = listOf(com.sharkos.launcher.ui.HomeCard.SHARK6, com.sharkos.launcher.ui.HomeCard.LIGHTS, com.sharkos.launcher.ui.HomeCard.SHORTCUTS), extras = ex)
        }
    }

    /** The CarPlay layout (SharkOS drawing it), with a stand-in for the picture. */
    @Test fun carPlayLayout() = paparazzi.snapshot {
        val r = com.sharkos.launcher.ui.CARPLAY_RADIUS
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            Box(Modifier.fillMaxWidth().height(8.dp))
            Box(Modifier.fillMaxWidth().height(604.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(r))
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF6B1F3A), androidx.compose.ui.graphics.Color(0xFF2A0B26)))))
            Box(Modifier.fillMaxWidth().height(4.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                BottomBar(climate, dock, Screen.HOME, bar, Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(r, r, 12.dp, 12.dp)),
                    lights = com.sharkos.launcher.ui.BarLights(true, true, 100, 0.62f))
            }
        }
    }

    @Test fun homeLightsBar() = paparazzi.snapshot {
        Frame(Screen.HOME, showLights = true, wallpaper = com.sharkos.launcher.ui.Wallpaper.OCEAN) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun homeSliderLeft() = paparazzi.snapshot {
        Frame(Screen.HOME, style = com.sharkos.launcher.ui.BarStyle.SLIDER, side = com.sharkos.launcher.ui.TempSide.LEFT) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun settingsHvacSides() = paparazzi.snapshot {
        Frame(Screen.SETTINGS, style = com.sharkos.launcher.ui.BarStyle.SLIDER) {
            SettingsScreen(
                SettingsInfo("0.1.9", true, true, CarLink.CONNECTED, "bydauto (BydHvac.apk)", true, barStyle = com.sharkos.launcher.ui.BarStyle.SLIDER, climate = climate, dock = dock),
                SettingsActions({}, {}, {}, {}),
                start = SettingsPage.HVAC,
            )
        }
    }

    @Test fun homeOcean() = paparazzi.snapshot {
        Frame(Screen.HOME, wallpaper = com.sharkos.launcher.ui.Wallpaper.OCEAN) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }
    @Test fun homeSunset() = paparazzi.snapshot {
        Frame(Screen.HOME, wallpaper = com.sharkos.launcher.ui.Wallpaper.SUNSET) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }
    @Test fun homeMidnight() = paparazzi.snapshot {
        Frame(Screen.HOME, wallpaper = com.sharkos.launcher.ui.Wallpaper.MIDNIGHT) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }
    @Test fun homeContour() = paparazzi.snapshot {
        Frame(Screen.HOME, wallpaper = com.sharkos.launcher.ui.Wallpaper.CONTOUR) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun settingsHvac() = settingsPage(SettingsPage.HVAC)
    private fun ambient(linked: Boolean) = paparazzi.snapshot {
        val c = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.runtime.remember {
            com.sharkos.launcher.lights.Lights.init(c)
            com.sharkos.launcher.lights.Lights.preview(linked)
            com.sharkos.launcher.lights.Lights.previewAddress = "BE:16:8B:53:40:A6"
        }
        Frame(Screen.SETTINGS) { SettingsScreen(SettingsInfo("0.1.16", true, true, CarLink.CONNECTED, "bydauto", true), SettingsActions({}, {}, {}, {}), start = SettingsPage.AMBIENT) }
    }

    @Test fun settingsAmbient() = ambient(linked = true)
    @Test fun settingsAmbientZones() = ambient(linked = false)
    @Test fun settingsSystem() = settingsPage(SettingsPage.SYSTEM)
    @Test fun settingsAbout() = settingsPage(SettingsPage.ABOUT)
    @Test fun settingsVehicle() = settingsPage(SettingsPage.VEHICLE)
    @Test fun settingsCluster() = settingsPage(SettingsPage.CLUSTER)

    @Test fun offroad() = paparazzi.snapshot {
        Frame(Screen.OFFROAD) { OffroadScreen(Attitude(12.6f, -23.4f), calibrated = true, onZero = {}, onBack = {}) }
    }

    @Test fun settings() = paparazzi.snapshot {
        Frame(Screen.SETTINGS) {
            SettingsScreen(
                SettingsInfo("0.1.2", false, false, CarLink.CONNECTED, "bydauto (BydHvac.apk)", false, autoLaunch = true, canLaunchFromBackground = false),
                SettingsActions({}, {}, {}, {}),
            )
        }
    }
}
