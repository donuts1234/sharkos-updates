package com.sharkos.launcher.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A CarPlay song as the head unit's CarPlay service reports it. */
data class CarPlaySong(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Elapsed time at [sampledAt]; -1 when CarPlay didn't say. */
    val elapsedMs: Long,
    /** 0 stopped, 1 playing, 2 paused, 3/4 seeking. */
    val status: Int,
    val art: Bitmap?,
    val sampledAt: Long,
) {
    val playing: Boolean get() = status == 1 || status == 3 || status == 4
}

/**
 * Now playing from CarPlay, read straight from the Desay head unit's CarPlay service
 * (`com.ts.carplay/.CarPlayService`) — the same feed BYD's own music widget uses. CarPlay publishes no
 * Android media session, so this is the only place its song, elapsed time and artwork exist.
 *
 * Spoken over raw Binder with the service's AIDL layout (ICarPlayService / INowPlayingCallback /
 * NowPlayingInfo from the head unit's ts.platform.library), so SharkOS needs none of Desay's libraries
 * and nothing happens at all on a unit without the service.
 */
object CarPlayLink {
    private const val TAG = "SharkOS/CarPlay"
    private const val COMPONENT = "com.ts.carplay/.CarPlayService"
    private const val SERVICE = "com.ts.carplay.common.aidl.ICarPlayService"
    private const val CALLBACK = "com.ts.carplay.common.aidl.INowPlayingCallback"
    private const val TX_REGISTER_NOW_PLAYING = 15
    private const val TX_UNREGISTER_NOW_PLAYING = 16
    private const val TX_HID_OVER_IAP = 35
    private const val CB_INFO_CHANGED = 1
    private const val CB_ARTWORK = 3

    /** Media keys for [key] (CarPlayConstants.RemoteMediaPlaybackKeyCode). */
    const val KEY_PLAY_PAUSE = 64
    const val KEY_NEXT = 4
    const val KEY_PREV = 8

    private val _song = MutableStateFlow<CarPlaySong?>(null)
    val song: StateFlow<CarPlaySong?> = _song.asStateFlow()

    private val _linked = MutableStateFlow(false)
    /** True once SharkOS is registered with the CarPlay service, for Settings → Now playing. */
    val linked: StateFlow<Boolean> = _linked.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var app: Context? = null
    private var service: IBinder? = null
    private var bound = false

    internal val callback = object : Binder() {
        init { attachInterface(null, CALLBACK) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != CB_INFO_CHANGED && code != CB_ARTWORK && code != 2) return super.onTransact(code, data, reply, flags)
            runCatching {
                data.enforceInterface(CALLBACK)
                if (code == 2) return true                         // playback list: not used
                if (data.readInt() == 0) return true               // null info
                val info = readInfo(data)
                publish(info, artworkOnly = code == CB_ARTWORK)
            }.onFailure { Log.w(TAG, "callback $code: ${it.message}") }
            return true
        }
    }

    private class Info(
        val title: String?, val artist: String?, val durationMs: Long, val status: Int, val elapsedMs: Long,
        val artPath: String?, val artData: ByteArray?, val album: String?,
    )

    /** NowPlayingInfo(Parcel), field for field. */
    private fun readInfo(p: Parcel): Info {
        val title = p.readString()
        val artist = p.readString()
        val duration = p.readLong()
        val status = p.readInt()
        val elapsed = p.readLong()
        p.readInt()                    // shuffle
        p.readInt()                    // repeat
        p.readInt()                    // artwork size
        val artPath = p.readString()
        val artData = p.createByteArray()
        val album = p.readString()
        return Info(title, artist, duration, status, elapsed, artPath, artData, album)
    }

    private fun publish(i: Info, artworkOnly: Boolean) {
        val prev = _song.value
        val art = decodeArt(i.artData, i.artPath)
        val next = if (artworkOnly && prev != null) prev.copy(art = art ?: prev.art) else {
            val sameSong = prev != null && prev.title == i.title.orEmpty() && prev.artist == i.artist.orEmpty()
            CarPlaySong(
                title = i.title.orEmpty(), artist = i.artist.orEmpty(), album = i.album.orEmpty(),
                durationMs = i.durationMs.coerceAtLeast(0), elapsedMs = i.elapsedMs, status = i.status,
                art = art ?: if (sameSong) prev?.art else null,
                sampledAt = SystemClock.elapsedRealtime(),
            )
        }
        _song.value = next
    }

    /** Artwork at about card size (the cover fills the Now Playing card), not a full-size JPEG. */
    private fun decodeArt(data: ByteArray?, path: String?): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val fromData = data != null && data.isNotEmpty()
        if (fromData) BitmapFactory.decodeByteArray(data, 0, data!!.size, opts)
        else if (!path.isNullOrBlank()) BitmapFactory.decodeFile(path, opts) else return null
        if (opts.outWidth <= 0) return null
        var sample = 1
        while (opts.outWidth / (sample * 2) >= 560) sample *= 2
        val real = BitmapFactory.Options().apply { inSampleSize = sample }
        if (fromData) BitmapFactory.decodeByteArray(data, 0, data!!.size, real) else BitmapFactory.decodeFile(path, real)
    }.getOrNull()

    // ---- CarPlay's app states: is the iPhone giving turn-by-turn directions right now? ----

    private const val STATE_CALLBACK = "com.ts.carplay.common.aidl.ICarPlayStateCallback"
    private const val TX_REGISTER_STATE = 3
    private const val CB_APP_STATE = 4
    private const val APP_STATE_NAVIGATION = 1

    private val _routeActive = MutableStateFlow(false)
    /** True while a CarPlay route is being guided (the iPhone's turn-by-turn state). */
    val routeActive: StateFlow<Boolean> = _routeActive.asStateFlow()

    internal val stateCallback = object : Binder() {
        init { attachInterface(null, STATE_CALLBACK) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code !in 1..5) return super.onTransact(code, data, reply, flags)
            // The phone disconnected (status 4): no route can still be running
            if (code == 2 || code == 3) runCatching {
                data.enforceInterface(STATE_CALLBACK)
                val status = data.readInt()
                Log.w(TAG, "session state $status")
                if (status == 4) _routeActive.value = false
            }
            if (code == CB_APP_STATE) runCatching {
                data.enforceInterface(STATE_CALLBACK)
                val appState = data.readInt()
                val enable = data.readInt() != 0
                val speechMode = data.readInt()
                Log.w(TAG, "app state $appState enable=$enable speech=$speechMode")
                if (appState == APP_STATE_NAVIGATION) _routeActive.value = enable
            }.onFailure { Log.w(TAG, "state callback: ${it.message}") }
            return true
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) { if (binder != null) attach(binder) }
        override fun onServiceDisconnected(name: ComponentName?) { service = null; _linked.value = false }
        override fun onBindingDied(name: ComponentName?) {
            runCatching { app?.unbindService(this) }
            bound = false; service = null; _linked.value = false
            main.postDelayed({ app?.let(::start) }, 5000)
        }
    }

    /** Start listening. Cheap, idempotent, and silent on a unit without the CarPlay service. */
    fun start(ctx: Context) {
        val c = ctx.applicationContext
        app = c
        if (bound || service != null) return
        val i = Intent().setComponent(ComponentName.unflattenFromString(COMPONENT))
        val ok = runCatching { c.bindService(i, conn, Context.BIND_AUTO_CREATE) }
        bound = ok.getOrDefault(false)
        if (!bound) Log.w(TAG, "can't bind: ${ok.exceptionOrNull()?.message ?: "no CarPlay service"}")
    }

    private fun attach(binder: IBinder) {
        service = binder
        // If CarPlay's process restarts, Android reconnects the binding and this runs again.
        val ok = runCatching { transact(binder, TX_REGISTER_NOW_PLAYING) { it.writeStrongBinder(callback) } }
        _linked.value = ok.isSuccess
        if (ok.isFailure) Log.w(TAG, "register failed: ${ok.exceptionOrNull()?.message}")
        runCatching { transact(binder, TX_REGISTER_STATE) { it.writeStrongBinder(stateCallback) } }
            .onFailure { Log.w(TAG, "state register failed: ${it.message}") }
    }

    private fun transact(b: IBinder, code: Int, write: (Parcel) -> Unit) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(SERVICE)
            write(data)
            b.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } finally { data.recycle() }
    }

    // ---- CarPlay's screen (BYD's own CarPlay app draws it; SharkOS only asks and watches) ----

    private const val TX_CHANGE_VIDEO_FOCUS = 21
    private const val TX_DISPLAY_STATUS = 22
    private const val TX_SET_SURFACE_WITH_TYPE = 25
    private const val TX_TOUCH = 28
    private const val TX_REQUEST_UI = 29
    private const val SCREEN_MAIN = 0

    /** CarPlay's picture as the iPhone sends it (BYD's view area); touches are in these units. */
    const val VIDEO_W = 1920
    const val VIDEO_H = 906

    /** Draw CarPlay into [surface] instead of BYD's screen (null: stop drawing there). */
    fun showOn(surface: android.view.Surface?): Boolean {
        val b = service ?: return false
        return runCatching {
            transact(b, TX_SET_SURFACE_WITH_TYPE) { p ->
                if (surface != null) { p.writeInt(1); surface.writeToParcel(p, 0) } else p.writeInt(0)
                p.writeInt(SCREEN_MAIN)
            }
        }.onFailure { Log.w(TAG, "setSurface: ${it.message}") }.isSuccess
    }

    /** Hand the screen to CarPlay (the iPhone streams) or back to the car (it stops). */
    fun screenToCarPlay(carPlay: Boolean): Boolean {
        val b = service ?: return false
        return runCatching {
            transact(b, TX_CHANGE_VIDEO_FOCUS) { p -> p.writeInt(if (carPlay) 1 else 0); p.writeInt(1); p.writeString(""); p.writeInt(1) }
        }.onFailure { Log.w(TAG, "videoFocus: ${it.message}") }.isSuccess
    }

    /** A touch on SharkOS's CarPlay picture, already in [VIDEO_W] × [VIDEO_H] units. */
    fun touch(event: android.view.MotionEvent): Boolean {
        val b = service ?: return false
        return runCatching { transact(b, TX_TOUCH) { p -> p.writeInt(1); event.writeToParcel(p, 0) } }.isSuccess
    }

    /** Ask the iPhone to bring up CarPlay (launch mode 0 normal, 1 maps, 2 music, 3 phone). */
    fun requestUi(mode: Int = 0): Boolean {
        val b = service ?: return false
        return runCatching { transact(b, TX_REQUEST_UI) { it.writeInt(mode) } }.isSuccess
    }

    /** Whether CarPlay says its picture is showing; null if it didn't answer. */
    fun displayShowing(): Boolean? {
        val b = service ?: return null
        val data = Parcel.obtain(); val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(SERVICE)
            b.transact(TX_DISPLAY_STATUS, data, reply, 0)
            reply.readException()
            reply.readInt() != 0
        } catch (e: Exception) { Log.w(TAG, "displayStatus: ${e.message}"); null } finally { data.recycle(); reply.recycle() }
    }

    /** Press a CarPlay media key (down then up). False if the service isn't there. */
    fun key(code: Int): Boolean {
        val b = service ?: return false
        return runCatching {
            transact(b, TX_HID_OVER_IAP) { it.writeInt(code); it.writeInt(0) }
            transact(b, TX_HID_OVER_IAP) { it.writeInt(code); it.writeInt(1) }
        }.isSuccess
    }

    fun stop() {
        val b = service
        if (b != null) runCatching { transact(b, TX_UNREGISTER_NOW_PLAYING) { it.writeStrongBinder(callback) } }
        if (bound) runCatching { app?.unbindService(conn) }
        bound = false; service = null; _linked.value = false
    }
}
