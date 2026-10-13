package com.sharkos.launcher

import android.os.IBinder
import android.os.Parcel
import com.sharkos.launcher.car.CarPlayLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The CarPlay service's NowPlayingInfo parcel, written exactly as the head unit writes it. */
@RunWith(RobolectricTestRunner::class)
class CarPlayLinkTest {
    @Test fun readsNowPlayingFromTheServiceParcel() {
        val p = Parcel.obtain()
        p.writeInterfaceToken("com.ts.carplay.common.aidl.INowPlayingCallback")
        p.writeInt(1)                         // non-null
        p.writeString("Yellow")               // title
        p.writeString("Coldplay")             // artist
        p.writeLong(266_000)                  // duration
        p.writeInt(1)                         // playing
        p.writeLong(42_000)                   // elapsed
        p.writeInt(0); p.writeInt(0)          // shuffle, repeat
        p.writeInt(-1)                        // artwork size
        p.writeString(null)                   // artwork path
        p.writeByteArray(null)                // artwork data
        p.writeString("Parachutes")           // album
        p.writeString("1")                    // pid
        p.setDataPosition(0)
        CarPlayLink.callback.transact(1, p, null, IBinder.FLAG_ONEWAY)
        p.recycle()
        val s = CarPlayLink.song.value!!
        assertEquals("Yellow", s.title)
        assertEquals("Coldplay", s.artist)
        assertEquals("Parachutes", s.album)
        assertEquals(266_000L, s.durationMs)
        assertEquals(42_000L, s.elapsedMs)
        assertTrue(s.playing)
    }
}
