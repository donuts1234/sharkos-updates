package com.sharkos.launcher

import com.sharkos.launcher.car.CarMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** CarMedia reads BYD's MediaInfo by field name, since its exact shape is only known on the car. */
class CarMediaTest {
    @Test fun readsTitleArtistDurationInMs() {
        val m = CarMedia.fromFields(30, 1, 61_000, mapOf("PackageName" to "com.ts.carplay", "Title" to "My Universe", "Singer" to "Coldplay", "Album" to "Music of the Spheres", "Duration" to "227000"), 0)
        assertEquals("CarPlay", m.source)
        assertEquals("My Universe", m.title)
        assertEquals("Coldplay", m.artist)
        assertEquals(227_000L, m.durationMs)
        assertEquals(61_000L, m.positionMs)
        assertTrue(m.playing)
    }

    @Test fun durationInSecondsIsNormalised() {
        val m = CarMedia.fromFields(16, 2, 61, mapOf("SongName" to "Yellow", "Artist" to "Coldplay", "TotalTime" to "266"), 0)
        assertEquals("Yellow", m.title)
        assertEquals(266_000L, m.durationMs)
        assertEquals(61_000L, m.positionMs)
        assertEquals(false, m.playing)
    }

    @Test fun noSongMeansNothingToShow() {
        val m = CarMedia.fromFields(30, 1, 0, emptyMap(), 0)
        assertEquals(false, m.hasSong)
    }
}
