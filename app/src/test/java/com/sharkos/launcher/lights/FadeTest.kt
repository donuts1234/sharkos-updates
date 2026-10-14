package com.sharkos.launcher.lights

import org.junit.Assert.assertEquals
import org.junit.Test

class FadeTest {
    @Test fun halfwayTheShortWay() {
        assertEquals(0.5f, AutoLights.mix(0.4f, 0.6f, 0.5f), 1e-4f)
        // red (0.0) to purple (0.8): through magenta (0.9), not through green
        assertEquals(0.9f, AutoLights.mix(0.0f, 0.8f, 0.5f), 1e-4f)
        assertEquals(0.05f, AutoLights.mix(0.9f, 0.2f, 0.5f), 1e-4f)
        assertEquals(0.2f, AutoLights.mix(0.9f, 0.2f, 1f), 1e-4f)
    }
}
