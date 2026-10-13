package com.sharkos.launcher.car

/**
 * What the head unit itself says is playing, from BYD's multimedia device
 * (android.hardware.bydauto.multimedia.BYDAutoMultimediaDevice). This is the channel BYD's own media
 * widget and the instrument cluster read, and the only one that carries CarPlay: CarPlay publishes no
 * Android media session on the Shark 6 (dumpsys media_session, 2026-10-13).
 *
 * Read on the car for the first time in this version, so the packaging of MediaInfo and the unit of
 * the progress value are unknown: [fields] keeps everything read for the diagnostics readout, and
 * the typed values are best guesses from field names.
 */
data class CarMedia(
    /** getMediaType(): which source is playing, BYD's MUSIC_SOURCE_* code. */
    val sourceCode: Int?,
    /** getPlayState() as read. */
    val stateCode: Int?,
    /** getPlayProgress() as read; unit unknown until seen on the car, see [positionMs]. */
    val progressRaw: Int?,
    val title: String?,
    val artist: String?,
    val album: String?,
    /** Track length if MediaInfo carries one, in ms (normalised from s or ms by size). */
    val durationMs: Long?,
    /** Everything MediaInfo exposed, name → value, for Settings → Media diagnostics. */
    val fields: Map<String, String>,
    /** When this was read (elapsedRealtime), to keep the elapsed time ticking between reads. */
    val readAt: Long,
) {
    val source: String get() = sourceName(sourceCode)

    /** BYD play-state codes (instrument MUSIC_*: 1 = play, 3 = loading). Anything else counts as paused. */
    val playing: Boolean get() = stateCode == 1 || stateCode == 3

    val hasSong: Boolean get() = !title.isNullOrBlank()

    /**
     * Elapsed time in ms when the progress value looks like a time. With a known duration it's read in
     * the same unit as the duration; without one, small values are taken as seconds.
     */
    val positionMs: Long?
        get() {
            val p = progressRaw ?: return null
            if (p < 0) return null
            val d = durationMs
            return when {
                d != null && d > 0 && rawDurationLooksLikeMs -> p.toLong()
                d != null && d > 0 -> p * 1000L
                else -> p * 1000L
            }
        }

    private val rawDurationLooksLikeMs: Boolean get() = (fields.entries.firstOrNull { isDurationKey(it.key) }?.value?.toLongOrNull() ?: 0) > 36_000

    companion object {
        /** BYD MUSIC_SOURCE_* codes (BYDAutoInstrumentDevice constants on the Shark 6). */
        private val SOURCES = mapOf(
            1 to "USB music", 4 to "SD card", 5 to "AUX", 6 to "Bluetooth audio", 7 to "FM radio",
            16 to "Spotify", 17 to "DAB radio", 18 to "AM radio", 30 to "CarPlay", 31 to "Android Auto",
            32 to "Amazon Music", 34 to "Deezer", 40 to "iHeartRadio", 44 to "Audible",
        )

        fun sourceName(code: Int?): String = code?.let { SOURCES[it] } ?: "Car audio"

        const val CARPLAY = 30

        internal fun isDurationKey(k: String): Boolean {
            val l = k.lowercase()
            return "duration" in l || "totaltime" in l || "length" in l || l.endsWith("total")
        }

        /**
         * Pull title / artist / album / duration out of whatever MediaInfo exposes, by name. BYD's
         * class names these in English (getTitle, getSinger…), but which exactly is unknown.
         */
        fun fromFields(sourceCode: Int?, stateCode: Int?, progressRaw: Int?, fields: Map<String, String>, readAt: Long): CarMedia {
            fun pick(vararg keys: String, not: List<String> = emptyList()): String? =
                fields.entries.firstOrNull { (k, v) ->
                    val l = k.lowercase()
                    v.isNotBlank() && v != "null" && keys.any { it in l } && not.none { it in l }
                }?.value
            val title = pick("title", "songname", "musicname", "name", not = listOf("singer", "artist", "album", "package", "app", "file", "path", "source"))
            val artist = pick("artist", "singer", "author")
            val album = pick("album")
            val dur = fields.entries.firstOrNull { isDurationKey(it.key) }?.value?.toLongOrNull()?.takeIf { it > 0 }
            // Seconds or milliseconds: no song is ten hours long.
            val durMs = dur?.let { if (it > 36_000) it else it * 1000 }
            return CarMedia(sourceCode, stateCode, progressRaw, title, artist, album, durMs, fields, readAt)
        }
    }
}
