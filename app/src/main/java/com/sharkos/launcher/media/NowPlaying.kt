package com.sharkos.launcher.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What's playing right now, from whichever app owns the active media session. */
data class Track(
    val title: String,
    val artist: String,
    val source: String,
    val art: Bitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    /** When [positionMs] was sampled (elapsedRealtime), to advance the bar between updates. */
    val sampledAt: Long,
    /** From the head unit's CarPlay service, not an Android media session: controls go there. */
    val carPlay: Boolean = false,
    /** False when the source gives no position at all: hide the time rather than show 0:00. */
    val knowsPosition: Boolean = true,
) {
    /** Where playback is now, advancing from the last sample while playing. */
    fun positionAt(nowElapsed: Long): Long {
        val p = if (playing) positionMs + (nowElapsed - sampledAt) else positionMs
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/**
 * Bridges Android's media sessions to the Home screen. Android only hands the session list to an
 * app the user has granted notification access (our [MediaListener]); without it, [track] stays
 * null and Home offers the music app instead.
 */
object NowPlaying {
    private val _track = MutableStateFlow<Track?>(null)
    val track: StateFlow<Track?> = _track.asStateFlow()

    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null
    private val main = Handler(Looper.getMainLooper())

    fun hasAccess(ctx: Context): Boolean =
        ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() { controller = null; publish() }
    }

    /** Start following sessions. Safe to call repeatedly; does nothing without access. */
    fun attach(ctx: Context) {
        if (manager != null || !hasAccess(ctx)) return
        val m = ctx.getSystemService(MediaSessionManager::class.java) ?: return
        val me = ComponentName(ctx, MediaListener::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, me, main)
            manager = m
            pick(m.getActiveSessions(me))
        }
    }

    fun detach() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        manager = null
    }

    private fun pick(sessions: List<MediaController>) {
        // Prefer whatever is actually playing; otherwise the most recent session that has a song.
        // Sessions with no song at all (Amazon Music idling, a disconnected Bluetooth player) are skipped.
        val withSong = sessions.filter { !it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE).isNullOrBlank() }
        val next = withSong.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: withSong.firstOrNull()
        if (next?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback, main)
        publish()
    }

    private fun publish() {
        val c = controller
        val md = c?.metadata
        if (c == null || md == null) { _track.value = null; return }
        val ps = c.playbackState
        _track.value = Track(
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            source = sourceNames[c.packageName] ?: c.packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            positionMs = ps?.position?.coerceAtLeast(0) ?: 0,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            sampledAt = ps?.lastPositionUpdateTime?.takeIf { it > 0 } ?: android.os.SystemClock.elapsedRealtime(),
            knowsPosition = ps != null && ps.position >= 0,
        )
    }

    /** Friendly names for the sources a Shark is likely to play from. */
    private val sourceNames = mapOf(
        "com.byd.localmusic" to "Music",
        "com.byd.bluetoothmusic" to "Bluetooth audio",
        "com.spotify.music" to "Spotify",
        "com.google.android.youtube" to "YouTube",
        "com.google.android.apps.youtube.music" to "YouTube Music",
    )

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}

/** Exists only so Android will share media sessions with SharkOS; it ignores notifications. */
class MediaListener : NotificationListenerService() {
    override fun onListenerConnected() { NowPlaying.attach(applicationContext) }
}
