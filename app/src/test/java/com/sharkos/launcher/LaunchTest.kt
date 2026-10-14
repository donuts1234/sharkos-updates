package com.sharkos.launcher

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Starts the real MainActivity with no car attached (as on a bench) and walks every screen through
 * the bottom bar and the Home cards, so a crash in the wiring shows up before it reaches the car.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [30], qualifiers = "w1280dp-h720dp-land-hdpi")
class LaunchTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun startsAndNavigates() {
        compose.onNodeWithText("Now playing".uppercase()).assertExists()

        compose.onNodeWithContentDescription("Climate").performClick()
        compose.onNodeWithContentDescription("Passenger seat heat").performClick()
        compose.onNodeWithContentDescription("Passenger seat cooling").performClick()
        compose.onNodeWithContentDescription("Fan 4").performClick()
        compose.onNodeWithContentDescription("Feet").performClick()
        compose.onNodeWithContentDescription("Climate power").performClick()
        compose.onNodeWithContentDescription("Climate power").performClick()

        compose.onNodeWithContentDescription("All apps").performClick()
        compose.onNodeWithText("Apps").assertExists()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Off-road").performClick()
        compose.onNodeWithText("Off-road").assertExists()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("SharkOS settings").performClick()
        compose.onNodeWithText("Open SharkOS when the car starts").assertExists()
        compose.onNodeWithContentDescription("Vehicle").performClick()
        compose.onNodeWithText("Vehicle connection").assertExists()

        // HVAC Bar: seat buttons step their seat; the cabin arrows move both zones together
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Driver seat heat off").performClick()
        compose.onNodeWithContentDescription("Driver seat heat low").assertExists()
        compose.onNodeWithContentDescription("Warmer").performClick()
        compose.onNodeWithContentDescription("Fan faster").performClick()
        val app = compose.activity.application as SharkOsApp

        // An empty + spot opens the picker; with no apps installed on the test device it just shows the picker title
        val before = app.apps.dock.value.size
        compose.onAllNodesWithContentDescription("Add an app to the bar")[0].performClick()
        compose.onNodeWithText("Add to the bar").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Now playing".uppercase()).assertExists()
        assertEquals(before, app.apps.dock.value.size)
        app.apps.pin("com.example.maps")
        assertEquals(before + 1, app.apps.dock.value.size)
        app.apps.unpin("com.example.maps")
        assertEquals(before, app.apps.dock.value.size)

        // Start-up: the boot broadcast schedules the one-shot job that brings SharkOS to the front
        BootReceiver().onReceive(app, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
        val js = app.getSystemService(android.app.job.JobScheduler::class.java)
        assertEquals(1, js.allPendingJobs.size)
        // and with the switch off, nothing is scheduled
        js.cancelAll()
        com.sharkos.launcher.data.Prefs(app).autoLaunch = false
        BootReceiver().onReceive(app, android.content.Intent(android.content.Intent.ACTION_BOOT_COMPLETED))
        assertEquals(0, js.allPendingJobs.size)
        assertEquals(1, app.car.climate.value.driverSeat.heat)
        val c = app.car.climate.value
        assertEquals(23f, c.driverTemp)      // 22 → 23 from the bar's Warmer
        assertEquals(23f, c.passengerTemp)   // the bar keeps both sides together
        assertEquals(false, c.dual)
        assertEquals(5, c.fan)               // fan 4 tapped on the Climate page → 5 from the bar
    }
}
