package com.sharkos.launcher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sharkos.launcher.MainActivity
import com.sharkos.launcher.SharkOsApp
import com.sharkos.launcher.car.CarPlayLink
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.lights.Lights
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * CarPlay with SharkOS's HVAC Bar. BYD's own CarPlay screen draws CarPlay and stays the app in
 * front, so the car keeps CarPlay's screen, the driver display's directions and the steering-wheel
 * buttons. SharkOS floats its bar over the bottom in a "display over other apps" window and asks
 * the car to hide its own bars while that window has focus.
 *
 * With the car's bars hidden, BYD's CarPlay picture (a fixed 1920 × 906, top-aligned) runs from the
 * top of the screen; the bar fills exactly the space below it. The overlay closes itself as soon as
 * CarPlay stops showing (another app, BYD's Home, or the iPhone unplugged).
 */
/** CarPlay's own panels are rounded about this much; the picture and the bar echo it. */
internal val CARPLAY_RADIUS = 22.dp

object CarPlayOverlay {
    private const val TAG = "SharkOS/CarPlay"
    const val BYD_CARPLAY = "com.ts.carplay.app"
    private const val BYD_DISPLAY = "com.ts.carplay.app.ui.display.view.CarPlayDisplayActivity"
    const val ALLOW_COMMAND = "adb shell appops set com.sharkos.launcher SYSTEM_ALERT_WINDOW allow"
    /** BYD's CarPlay picture height in screen pixels (CarPlayDisplayFragment's fixed size). */
    private const val PICTURE_H = 906
    private const val FALLBACK_BAR_H = 174

    private var view: View? = null
    /** Drawing CarPlay ourselves this time (see [Prefs.carPlayInSharkOs]). */
    private var drawing = false
    private var surface: android.view.Surface? = null
    /** While handing CarPlay's picture over, the car briefly reports "not showing": don't close for it. */
    @Volatile private var handoverUntil = 0L
    private var owner: OverlayOwner? = null
    private var watch: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val isOpen: Boolean get() = view != null

    /** Open BYD's CarPlay with SharkOS's bar on top. Returns why it couldn't, or null. */
    /** SharkOS's own screen is in front (set by MainActivity): no overlay over it. */
    @Volatile var sharkOsInFront = false
    private var autoJob: Job? = null

    /**
     * Whenever BYD's CarPlay comes on screen without SharkOS's bar (after the reversing camera,
     * from BYD's own CarPlay icon, or the iPhone asking for the screen for a call or Siri), put the
     * bar (and SharkOS's picture, if that's on) back over it. Runs for the whole process.
     */
    fun startAutoAttach(ctx: Context) {
        if (autoJob != null) return
        val app = ctx.applicationContext
        autoJob = scope.launch {
            var showingFor = 0
            while (true) {
                delay(500)
                if (isOpen || sharkOsInFront || !com.sharkos.launcher.data.Prefs(app).carPlayAlwaysBar || !Settings.canDrawOverlays(app)) {
                    showingFor = 0; continue
                }
                val showing = withContext(Dispatchers.IO) { CarPlayLink.displayShowing() } == true
                // Twice in a row (a second), so a passing blip doesn't pop the bar up
                showingFor = if (showing) showingFor + 1 else 0
                if (showingFor >= 2) {
                    Log.w(TAG, "CarPlay is on screen without SharkOS's bar: adding it")
                    showingFor = 0
                    show(app)
                }
            }
        }
    }

    fun open(ctx: Context): String? {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return "SharkOS needs \"display over other apps\": $ALLOW_COMMAND"
        val launch = app.packageManager.getLaunchIntentForPackage(BYD_CARPLAY)
            ?: Intent(Intent.ACTION_MAIN).setClassName(BYD_CARPLAY, BYD_DISPLAY)
        if (runCatching { app.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isFailure) {
            return "BYD's CarPlay didn't open: is the iPhone connected?"
        }
        show(app)
        return null
    }

    /** Take the bar away; [then] is where SharkOS goes next (null: stay wherever the car is). */
    fun close(ctx: Context, then: Screen? = Screen.HOME) {
        val app = ctx.applicationContext
        watch?.cancel(); watch = null
        if (drawing) { CarPlayLink.showOn(null); surface = null }
        view?.let { v -> runCatching { app.getSystemService(WindowManager::class.java).removeViewImmediate(v) } }
        view = null
        owner?.destroy(); owner = null
        if (then != null) {
            val i = Intent(app, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_SCREEN, then.name)
            runCatching { app.startActivity(i) }
        }
    }

    @SuppressLint("RtlHardcoded")
    private fun show(app: Context) {
        if (view != null) { watchCarPlay(app); return }
        val wm = app.getSystemService(WindowManager::class.java)
        drawing = com.sharkos.launcher.data.Prefs(app).carPlayInSharkOs
        val o = OverlayOwner()
        val screenH = screenHeight(wm)
        val cv = ComposeView(app).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setViewTreeViewModelStoreOwner(o)
            setContent {
                if (drawing) FullCarPlay(app as SharkOsApp, screenH, onSurface = { onSurface(app, it) })
                else OverlayBar(app as SharkOsApp)
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (drawing) WindowManager.LayoutParams.MATCH_PARENT else barHeight(wm),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // Focusable (no FLAG_NOT_FOCUSABLE): the focused window decides whether the car's bars show.
            // Touches outside the bar still go to CarPlay underneath.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.LEFT
            title = "SharkOS CarPlay bar"
            if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        cv.viewTreeObserver.addOnWindowFocusChangeListener { focused -> if (focused) hideCarBars(cv) }
        if (runCatching { wm.addView(cv, lp) }.onFailure { Log.w(TAG, "overlay: ${it.message}") }.isFailure) {
            o.destroy(); return
        }
        view = cv; owner = o
        cv.post { hideCarBars(cv) }
        watchCarPlay(app)
    }

    /** Ask the car to hide its top and bottom bars while the overlay has focus. */
    @Suppress("DEPRECATION")
    private fun hideCarBars(v: View) {
        if (Build.VERSION.SDK_INT >= 30) {
            v.windowInsetsController?.let {
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            }
        }
        v.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }

    private fun screenHeight(wm: WindowManager): Int =
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height()
        else @Suppress("DEPRECATION") android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.heightPixels

    /** The space under BYD's CarPlay picture, in screen pixels. */
    private fun barHeight(wm: WindowManager): Int = (screenHeight(wm) - PICTURE_H).takeIf { it in 100..400 } ?: FALLBACK_BAR_H

    /**
     * Our picture's surface is ready: take CarPlay's picture over from BYD's screen. The iPhone only
     * starts drawing on a new surface at the start of a stream, so after handing the surface over the
     * screen goes to the car for a moment and straight back, which restarts the stream on ours.
     * BYD's screen stays the app in front throughout (driver display, steering wheel, VideoModel).
     */
    private fun onSurface(app: Context, s: android.view.Surface?) {
        surface = s
        if (s == null) { CarPlayLink.showOn(null); return }
        handoverUntil = System.currentTimeMillis() + 12_000
        scope.launch(Dispatchers.IO) {
            // Let BYD's screen get going first (it hands over its own surface once, as it opens):
            // wait for CarPlay to be showing, then a moment more, so ours is the last one given.
            val until = System.currentTimeMillis() + 8000
            while (CarPlayLink.displayShowing() != true && System.currentTimeMillis() < until) delay(200)
            delay(1000)
            if (surface !== s) return@launch          // closed or replaced meanwhile
            handoverUntil = System.currentTimeMillis() + 4000
            Log.w(TAG, "taking CarPlay's picture over")
            CarPlayLink.showOn(s)
            delay(150)
            CarPlayLink.screenToCarPlay(false)
            delay(450)
            CarPlayLink.screenToCarPlay(true)
            delay(150)
            CarPlayLink.requestUi(0)
        }
    }

    /**
     * Close the bar the moment CarPlay stops showing: BYD's Home, another app, or the iPhone unplugged.
     * Checked four times a second; one "not showing" after it has shown is enough (the car only
     * reports that when the screen really changed hands).
     */
    private fun watchCarPlay(app: Context) {
        watch?.cancel()
        watch = scope.launch {
            var seen = false
            val start = System.currentTimeMillis()
            while (true) {
                val showing = withContext(Dispatchers.IO) { CarPlayLink.displayShowing() }
                if (showing == true) seen = true
                val gone = seen && showing == false && System.currentTimeMillis() > handoverUntil
                val neverShowed = !seen && System.currentTimeMillis() - start > 10_000
                if (gone || neverShowed) {
                    Log.w(TAG, "overlay closing: ${if (gone) "CarPlay left the screen" else "CarPlay never showed"}")
                    close(app, then = null); break
                }
                delay(250)
            }
        }
    }

    /** What the overlay window needs to host Compose outside an activity. */
    private class OverlayOwner : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init {
            saved.performAttach()
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }
}

/** The HVAC Bar under CarPlay's picture. */
@Composable
private fun OverlayBar(app: SharkOsApp) {
    val outer = remember { Corners.entries.firstOrNull { it.name == Prefs(app).corners } ?: Corners.LIGHT }.radius.dp
    val ctx = app.applicationContext
    val car = app.car
    val apps = app.apps
    val prefs = remember { Prefs(ctx) }
    val climate by car.climate.collectAsStateWithLifecycle()
    val dock by apps.dock.collectAsStateWithLifecycle()
    val lightZones by Lights.zones.collectAsStateWithLifecycle()
    val lightLink by Lights.link.collectAsStateWithLifecycle()
    val wallpaper = remember { Wallpaper.entries.firstOrNull { it.name == prefs.wallpaper } ?: Wallpaper.BLACK }
    val style = remember { BarStyle.entries.firstOrNull { it.name == prefs.barStyle } ?: BarStyle.BALANCED }
    val side = remember { TempSide.entries.firstOrNull { it.name == prefs.tempSide } ?: TempSide.RIGHT }
    val lights = remember(lightZones, lightLink) {
        Lights.address(ctx)?.let {
            val first = lightZones.getValue(Lights.Zone.TRIMS)
            BarLights(lightLink == Lights.Link.CONNECTED, lightZones.values.any { it.on }, first.brightness, first.hue)
        }
    }
    val dockApps = dock.mapNotNull { pkg -> apps.find(pkg)?.let { DockApp(it.pkg, it.label) } }
    val actions = remember(car, apps) {
        val to: (Screen) -> Unit = { s -> CarPlayOverlay.close(ctx, s) }
        BarActions(
            nudgeCabin = car::nudgeCabin,
            setCabin = car::setCabinTemp,
            nudgeFan = car::nudgeFan,
            cycleHeat = car::cycleSeatHeat,
            cycleVent = car::cycleSeatVent,
            toggleAc = { car.setAcOn(!car.climate.value.ac) },
            home = { to(Screen.HOME) },
            apps = { to(Screen.APPS) },
            climate = { to(Screen.CLIMATE) },
            launch = { pkg -> if (pkg != CarPlayOverlay.BYD_CARPLAY) { CarPlayOverlay.close(ctx, then = null); apps.launch(pkg) } },
            addApp = { to(Screen.APPS) },
            removeApp = { },
            setFan = car::setFanSpeed,
            lightsOn = { Lights.setOn(ctx, null, it) },
            lightsBrightness = { Lights.setBrightness(ctx, null, it) },
            lightsHue = { Lights.setHue(ctx, null, it) },
            back = { to(Screen.HOME) },
        )
    }
    CompositionLocalProvider(LocalIcons provides remember(apps) { apps.iconSource() }, LocalWallpaper provides wallpaper) {
        // The bar fills the whole space under the picture (taller than SharkOS's own bar, same controls)
        // A rounded card under CarPlay: soft top corners against the picture, the screen's own
        // corner setting at the bottom, black showing around it as on every SharkOS screen.
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            BottomBar(
                climate, dockApps, Screen.HOME, actions,
                Modifier.fillMaxHeight().clip(androidx.compose.foundation.shape.RoundedCornerShape(CARPLAY_RADIUS, CARPLAY_RADIUS, outer, outer)),
                style = style, tempSide = side, lights = lights,
                // Neutral dark grey under CarPlay: no wallpaper or album colours to clash with the
                // picture, a touch lighter than the black around it so the card still reads
                plain = Color(0xFF1C1C1F),
            )
        }
    }
}

/**
 * CarPlay drawn by SharkOS: the iPhone's 1920 × 906 picture moved down from the screen's top edge
 * by the chosen gap (black), the HVAC Bar in the space left under it. Touches go straight back to
 * the iPhone. A TextureView rather than a SurfaceView: a SurfaceView marks the rest of the window
 * see-through, which let BYD's last frame show in the gap above the picture.
 */
@Composable
internal fun FullCarPlay(app: SharkOsApp, screenH: Int, onSurface: (android.view.Surface?) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gapPx = androidx.compose.runtime.remember { com.sharkos.launcher.data.Prefs(app).carPlayTopGap }
        .coerceIn(0, (screenH - CarPlayLink.VIDEO_H - 100).coerceAtLeast(0))
    val surfaceCb = androidx.compose.runtime.rememberUpdatedState(onSurface)
    val outer = androidx.compose.runtime.remember { Corners.entries.firstOrNull { it.name == com.sharkos.launcher.data.Prefs(app).corners } ?: Corners.LIGHT }.radius.dp
    // CarPlay as a rounded card: the screen's corners at the top, CarPlay's own softer curve at the
    // bottom, then a sliver of black and the bar as a second card under it.
    val pictureShape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (gapPx == 0) outer else CARPLAY_RADIUS, topEnd = if (gapPx == 0) outer else CARPLAY_RADIUS,
        bottomStart = CARPLAY_RADIUS, bottomEnd = CARPLAY_RADIUS,
    )
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxWidth().height(with(density) { gapPx.toDp() }).background(Color.Black))
        Box(
            Modifier.width(with(density) { CarPlayLink.VIDEO_W.toDp() }).height(with(density) { CarPlayLink.VIDEO_H.toDp() })
                .clip(pictureShape).background(Color(0xFF101418)),
        ) {
            if (androidx.compose.ui.platform.LocalInspectionMode.current) {
                // Screenshots: a stand-in for the live picture
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF3B1630), Color(0xFF14202E)))))
            } else {
                androidx.compose.ui.viewinterop.AndroidView(factory = { c -> carPlayView(c) { surfaceCb.value(it) } }, modifier = Modifier.fillMaxSize())
            }
        }
        Box(Modifier.fillMaxWidth().height(6.dp))
        Box(Modifier.fillMaxWidth().weight(1f)) { OverlayBar(app) }
    }
}

/** The view CarPlay draws into; touches scaled to the iPhone's 1920 × 906 and sent back. */
@SuppressLint("ClickableViewAccessibility")
private fun carPlayView(c: Context, onSurface: (android.view.Surface?) -> Unit): android.view.TextureView {
    val v = android.view.TextureView(c)
    v.surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
        private var surface: android.view.Surface? = null
        override fun onSurfaceTextureAvailable(st: android.graphics.SurfaceTexture, w: Int, h: Int) {
            st.setDefaultBufferSize(CarPlayLink.VIDEO_W, CarPlayLink.VIDEO_H)
            surface = android.view.Surface(st).also { onSurface(it) }
        }
        override fun onSurfaceTextureSizeChanged(st: android.graphics.SurfaceTexture, w: Int, h: Int) {}
        override fun onSurfaceTextureDestroyed(st: android.graphics.SurfaceTexture): Boolean {
            onSurface(null)
            surface?.release(); surface = null
            return true
        }
        override fun onSurfaceTextureUpdated(st: android.graphics.SurfaceTexture) {}
    }
    v.setOnTouchListener { view, e ->
        if (view.width > 0 && view.height > 0) {
            val scaled = android.view.MotionEvent.obtain(e)
            scaled.transform(android.graphics.Matrix().apply { setScale(CarPlayLink.VIDEO_W / view.width.toFloat(), CarPlayLink.VIDEO_H / view.height.toFloat()) })
            CarPlayLink.touch(scaled)
            scaled.recycle()
        }
        true
    }
    return v
}
