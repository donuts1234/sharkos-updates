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
        assertEquals(2, compose.onAllNodesWithText("Seat heat").fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("Fan up").performClick()
        compose.onAllNodesWithContentDescription("Driver warmer")[0].performClick()
        compose.onNodeWithContentDescription("Climate power").performClick()
        compose.onNodeWithContentDescription("Climate power").performClick()

        compose.onNodeWithContentDescription("All apps").performClick()
        compose.onNodeWithText("Apps").assertExists()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Off-road").performClick()
        compose.onNodeWithText("Off-road").assertExists()

        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("SharkOS settings").performClick()
        compose.onNodeWithText("Vehicle connection").assertExists()

        // Bottom-bar seat buttons and temperatures step the shared climate state
        compose.onNodeWithContentDescription("Home").performClick()
        compose.onNodeWithContentDescription("Driver seat heat off").performClick()
        compose.onNodeWithContentDescription("Driver seat heat low").assertExists()
        compose.onNodeWithContentDescription("Passenger warmer").performClick()
        val app = compose.activity.application as SharkOsApp
        assertEquals(1, app.car.climate.value.driverSeat.heat)
        assertEquals(23f, app.car.climate.value.driverTemp)   // warmed once on the Climate page
        assertEquals(24f, app.car.climate.value.passengerTemp) // zones were linked at 23; the bar splits them and warms the passenger
        assertEquals(true, app.car.climate.value.dual)
        assertEquals(4, app.car.climate.value.fan)
    }
}
