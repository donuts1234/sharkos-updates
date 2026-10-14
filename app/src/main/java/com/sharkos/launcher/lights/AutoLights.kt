package com.sharkos.launcher.lights

import android.content.Context
import android.graphics.Bitmap
import com.sharkos.launcher.car.CarPlayLink
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.media.NowPlaying
import com.sharkos.launcher.ui.Wallpaper
import com.sharkos.launcher.ui.artColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Ambient lighting that picks its own colour, from two switches:
 *  - **match album art**: each cover's main colour, both zones;
 *  - **match wallpaper**: the wallpaper's two colours, door trims and pockets/footwells.
 *
 * With both on, album art wins while music plays and the wallpaper's colours come back when it
 * stops or pauses. With only album art on, the last cover's colour stays when the music pauses.
 * Turning both off puts back the colours the lights had before. Runs for the whole process, so it
 * keeps working while CarPlay or another app is in front. Only colour changes: brightness and
 * on/off stay the driver's.
 */
object AutoLights {
    private const val KEY_ART = "follow_art"
    private const val KEY_WALL = "follow_wallpaper"
    private const val KEY_BEFORE = "auto_before_"
    private fun prefs(c: Context) = c.getSharedPreferences("sharkos_lights", Context.MODE_PRIVATE)

    private val _art = MutableStateFlow(false)
    val followArt: StateFlow<Boolean> = _art.asStateFlow()
    private val _wall = MutableStateFlow(false)
    val followWallpaper: StateFlow<Boolean> = _wall.asStateFlow()

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What's playing now: its art, and whether it's actually playing. */
    private var art: Bitmap? = null
    private var playing = false
    private var wallpaper = Wallpaper.BLACK
    /** The colours last sent, so nothing is resent unchanged. */
    private var applied: Pair<Float, Float>? = null

    fun start(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        runCatching {
            val p = prefs(c)
            _art.value = p.getBoolean(KEY_ART, false)
            _wall.value = p.getBoolean(KEY_WALL, false)
            wallpaper = Wallpaper.entries.firstOrNull { it.name == Prefs(c).wallpaper } ?: Wallpaper.BLACK
        }
        scope.launch {
            combine(CarPlayLink.song, NowPlaying.track) { cp, session ->
                // What's playing wins; CarPlay first, as on the Home card
                when {
                    cp?.playing == true && cp.art != null -> cp.art to true
                    session?.playing == true && session.art != null -> session.art to true
                    else -> (cp?.art ?: session?.art) to false
                }
            }.distinctUntilChanged().collect { (a, p) ->
                art = a; playing = p
                update(c)
            }
        }
    }

    fun setFollowArt(ctx: Context, on: Boolean) = set(ctx, KEY_ART, _art, on)
    fun setFollowWallpaper(ctx: Context, on: Boolean) = set(ctx, KEY_WALL, _wall, on)

    /** The wallpaper changed in Settings → Appearance. */
    fun wallpaperChanged(ctx: Context, w: Wallpaper) {
        start(ctx)
        wallpaper = w
        update(ctx.applicationContext)
    }

    private fun set(ctx: Context, key: String, flow: MutableStateFlow<Boolean>, on: Boolean) {
        val c = ctx.applicationContext
        start(c)
        if (flow.value == on) return
        val wasAuto = _art.value || _wall.value
        if (on && !wasAuto) rememberBefore(c)          // the colours to go back to
        flow.value = on
        prefs(c).edit().putBoolean(key, on).apply()
        if (!_art.value && !_wall.value) restoreBefore(c) else update(c)
    }

    private fun update(c: Context) {
        val want = target() ?: return
        if (want == applied || Lights.address(c) == null) return
        applied = want
        if (want.first == want.second) Lights.setHue(c, null, want.first)
        else {
            Lights.setHue(c, Lights.Zone.TRIMS, want.first)
            Lights.setHue(c, Lights.Zone.POCKETS, want.second)
        }
    }

    /** The colours the switches call for now, or null to leave the lights alone. */
    private fun target(): Pair<Float, Float>? {
        val a = art
        return when {
            _art.value && a != null && (playing || !_wall.value) -> hueOf(a).let { it to it }
            _wall.value -> wallpaper.lights
            else -> null
        }
    }

    private fun rememberBefore(c: Context) {
        val z = Lights.zones.value
        prefs(c).edit()
            .putFloat(KEY_BEFORE + Lights.Zone.TRIMS.name, z.getValue(Lights.Zone.TRIMS).hue)
            .putFloat(KEY_BEFORE + Lights.Zone.POCKETS.name, z.getValue(Lights.Zone.POCKETS).hue)
            .apply()
    }

    private fun restoreBefore(c: Context) {
        applied = null
        val p = prefs(c)
        for (zone in Lights.Zone.entries) {
            if (p.contains(KEY_BEFORE + zone.name)) Lights.setHue(c, zone, p.getFloat(KEY_BEFORE + zone.name, 0.6f))
        }
    }

    /** The art's main colour as a hue (0–1), or [Lights.WHITE] for covers with no real colour. */
    fun hueOf(art: Bitmap): Float {
        val accent = artColors(art).accent
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((accent.red * 255).toInt(), (accent.green * 255).toInt(), (accent.blue * 255).toInt(), hsv)
        return if (hsv[1] < 0.2f) Lights.WHITE else hsv[0] / 360f
    }
}
