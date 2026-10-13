package com.sharkos.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
    private val bar = BarActions({ _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val home = HomeActions({}, {}, {}, {}, {}, {}, {})
    private val track = Track("Sample Song Title", "Artist Name", "Bluetooth audio", null, true, 102_000, 238_000, 0)
    // 9:41 on Tuesday 13 October 2026 (local), fixed so the clock doesn't change between runs
    private val now = java.util.Calendar.getInstance().apply { set(2026, 9, 13, 9, 41, 0) }.timeInMillis

    @Composable
    private fun Frame(screen: Screen, c: ClimateState = climate, content: @Composable () -> Unit) =
        Column(Modifier.fillMaxSize().background(Sh.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            BottomBar(c, dock, screen, bar)
        }

    @Test fun home() = paparazzi.snapshot {
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, track, true, Attitude(2.4f, -1.2f), home) }
    }

    @Test fun homeNoMediaAccess() = paparazzi.snapshot {
        Frame(Screen.HOME) { HomeScreen(now, telemetry, CarLink.CONNECTED, null, false, Attitude(0f, 0f), home) }
    }

    @Test fun homeOffline() = paparazzi.snapshot {
        Frame(Screen.HOME, ClimateState()) { HomeScreen(now, Telemetry(), CarLink.OFFLINE, null, true, null, home) }
    }

    @Test fun climate() = paparazzi.snapshot {
        Frame(Screen.CLIMATE) {
            ClimateScreen(climate, ClimateActions({}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }, {}))
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
            )
        }
    }

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
