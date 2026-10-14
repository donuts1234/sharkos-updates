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

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class RgbFadeTest {
    private fun ch(c: Int) = Triple(c shr 16 and 255, c shr 8 and 255, c and 255)

    @Test fun closeColoursBlendStraight() {
        // red to orange: halfway is between them, no other colour
        val mid = ch(AutoLights.blend(0xFF0000, 0xFF8000, 0.5f))
        assertEquals(255, mid.first); assertEquals(64, mid.second); assertEquals(0, mid.third)
    }

    @Test fun farColoursDimThroughNotGreyOrGreen() {
        // orange to blue: never a mix of the two (grey) and never green
        for (i in 0..20) {
            val (r, g, b) = ch(AutoLights.blend(0xFF8000, 0x0040FF, i / 20f))
            val orangeish = b == 0
            val blueish = r == 0
            org.junit.Assert.assertTrue("step $i was ($r,$g,$b)", orangeish || blueish)
        }
        assertEquals(0x0040FF, AutoLights.blend(0xFF8000, 0x0040FF, 1f))
    }
}
