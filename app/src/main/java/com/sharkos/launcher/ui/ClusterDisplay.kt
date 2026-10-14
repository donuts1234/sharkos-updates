package com.sharkos.launcher.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import com.sharkos.launcher.car.Corner
import com.sharkos.launcher.car.Tyre
import com.sharkos.launcher.car.TyreLimits
import com.sharkos.launcher.car.TyreWarning
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.media.Track
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.sharkos.launcher.SharkOsApp
import com.sharkos.launcher.car.CarPlayLink
import com.sharkos.launcher.car.ClusterLink
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.media.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * SharkOS on the driver display (test): a SharkOS panel drawn on the cluster's Android display,
 * then the cluster asked to show its map area (where that display appears). See [ClusterLink].
 */
/** How the driver display shows the album cover. */
enum class ClusterStyle(val label: String) {
    CARD("Cover card"), FADE("Cover fade");
    companion object { fun parse(s: String?) = entries.firstOrNull { it.name == s } ?: FADE }
}

object ClusterDisplay {
    private const val TAG = "SharkOS/Cluster"
    private var view: View? = null
    private var owner: Owner? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    /** What happened, for the test page. */
    val status = mutableStateOf("Not started")

    val isShowing: Boolean get() = view != null
    /** Which map "slot" SharkOS claims on the cluster. 3 (Android Auto's) wakes BYD's Android Auto app,
     *  which then covers the cluster display in black; 1 is BYD's own navigation; 9 is one nobody uses. */
    val slot = mutableStateOf(1)
    val style = mutableStateOf(ClusterStyle.FADE)
    val enabled = mutableStateOf(true)
    private var claimed: Int? = null
    private var starting: kotlinx.coroutines.Job? = null
    private var loaded = false

    private fun load(app: Context) {
        if (loaded) return
        val p = Prefs(app)
        slot.value = p.clusterSlot; style.value = ClusterStyle.parse(p.clusterStyle); enabled.value = p.clusterOn
        loaded = true
    }

    /**
     * Put SharkOS on the cluster when SharkOS starts (boot or app start), if it's switched on. The
     * cluster's display and service can come up after SharkOS at boot, so this waits for both.
     */
    fun autoStart(ctx: Context) {
        val app = ctx.applicationContext
        load(app)
        if (!enabled.value || isShowing || starting?.isActive == true) return
        starting = scope.launch {
            status.value = "Waiting for the instrument cluster…"
            val ready = kotlinx.coroutines.withTimeoutOrNull(120_000) {
                while (ClusterLink.display(app) == null || !ClusterLink.connected) { ClusterLink.start(app); delay(1500) }
                true
            }
            if (ready == null) { status.value = "The instrument cluster didn't answer. Turn it off and on to retry."; return@launch }
            if (enabled.value && !isShowing) show(app, ClusterLink.STATE_FULL)
        }
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        val app = ctx.applicationContext
        load(app)
        enabled.value = on
        Prefs(app).clusterOn = on
        if (on) { starting?.cancel(); show(app, ClusterLink.STATE_FULL) } else { starting?.cancel(); hide(app) }
    }

    fun setStyle(ctx: Context, st: ClusterStyle) {
        style.value = st
        Prefs(ctx.applicationContext).clusterStyle = st.name
    }
    private var lastState = ClusterLink.STATE_FULL
    private var routeWatch: kotlinx.coroutines.Job? = null

    /** CarPlay takes the driver display for a route but never hands it back when the route ends
     *  (it keeps its map up until the phone disconnects), so SharkOS takes it back itself. */
    private fun watchRoutes(app: Context) {
        if (routeWatch?.isActive == true) return
        CarPlayLink.start(app)
        routeWatch = scope.launch {
            var wasRouting = CarPlayLink.routeActive.value
            CarPlayLink.routeActive.collect { routing ->
                if (wasRouting && !routing && isShowing) {
                    delay(1500)                                  // let CarPlay finish closing its map first
                    if (!CarPlayLink.routeActive.value && isShowing) reclaim(app, "CarPlay route ended")
                }
                wasRouting = routing
            }
        }
    }

    private suspend fun reclaim(app: Context, why: String) {
        val type = claimed ?: slot.value
        ClusterLink.requestMapFocus(type)
        delay(150)
        ClusterLink.naviState(true, type)
        delay(150)
        ClusterLink.presentation(lastState, type)
        delay(500)
        status.value = "$why · took the driver display back · map focus now ${ClusterLink.focusMap(app)}"
        Log.w(TAG, status.value)
    }

    fun setSlot(ctx: Context, s: Int) {
        if (s == slot.value) return
        val wasShowing = isShowing
        if (wasShowing) claimed?.let { old -> scope.launch { ClusterLink.presentation(ClusterLink.STATE_CLOSE, old); ClusterLink.naviState(false, old); ClusterLink.abandonMapFocus(old) } }
        claimed = null
        slot.value = s
        Prefs(ctx.applicationContext).clusterSlot = s
        if (wasShowing) show(ctx, ClusterLink.STATE_FULL)
    }

    fun show(ctx: Context, state: Int) {
        val app = ctx.applicationContext
        load(app)
        ClusterLink.start(app)
        scope.launch {
            val d = ClusterLink.display(app)
            if (d == null) { status.value = "No driver display found"; return@launch }
            if (view == null) {
                val dc = app.createDisplayContext(d)
                val wm = dc.getSystemService(WindowManager::class.java)
                val o = Owner()
                val cv = ComposeView(dc).apply {
                    setViewTreeLifecycleOwner(o); setViewTreeSavedStateRegistryOwner(o); setViewTreeViewModelStoreOwner(o)
                    setContent { ClusterPanel(app as SharkOsApp) }
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE,
                ).apply {
                    title = "SharkOS driver display"
                    if (Build.VERSION.SDK_INT >= 30) fitInsetsTypes = 0
                }
                val added = runCatching { wm.addView(cv, lp) }
                if (added.isFailure) {
                    status.value = "Couldn't draw on ${ClusterLink.describe(d)}: ${added.exceptionOrNull()?.message}"
                    Log.w(TAG, status.value); o.destroy(); return@launch
                }
                view = cv; owner = o
            }
            // Give the service a moment to connect on first use
            repeat(20) { if (!ClusterLink.connected) delay(150) }
            if (!ClusterLink.connected) { status.value = "Drawing on ${ClusterLink.describe(d)}, but the cluster service didn't connect"; return@launch }
            val type = slot.value
            claimed = type
            lastState = state
            watchRoutes(app)
            if (CarPlayLink.routeActive.value) {
                status.value = "Ready · CarPlay directions have the cluster; SharkOS comes back when the route ends"
                return@launch
            }
            ClusterLink.requestMapFocus(type)
            delay(150)
            ClusterLink.naviState(true, type)
            delay(150)
            ClusterLink.presentation(state, type)
            delay(600)
            val focus = ClusterLink.focusMap(app)
            status.value = (if (focus == type) "Showing on the cluster" else "Asked the cluster, but it reports map focus $focus") +
                " · ${ClusterLink.describe(d)} · slot $type"
            Log.w(TAG, status.value)
        }
    }

    fun hide(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            val type = claimed ?: slot.value
            ClusterLink.presentation(ClusterLink.STATE_CLOSE, type)
            delay(100)
            ClusterLink.naviState(false, type)
            delay(100)
            ClusterLink.abandonMapFocus(type)
            claimed = null
            view?.let { v -> runCatching { (v.context.getSystemService(WindowManager::class.java)).removeViewImmediate(v) } }
            view = null
            owner?.destroy(); owner = null
            routeWatch?.cancel(); routeWatch = null
            status.value = "Off · the cluster is back to BYD's view"
        }
    }

    fun setClusterView(ctx: Context, mode: Int) {
        ClusterLink.start(ctx)
        scope.launch {
            repeat(20) { if (!ClusterLink.connected) delay(150) }
            ClusterLink.setDisplayMode(mode)
            delay(500)
            val before = status.value.substringBefore(" ‖ ")
            status.value = "$before ‖ asked the cluster for view $mode · it now says ${ClusterLink.displayMode(ctx)}"
        }
    }

    private class Owner : SavedStateRegistryOwner, ViewModelStoreOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = saved.savedStateRegistry
        override val viewModelStore = ViewModelStore()
        init { saved.performAttach(); saved.performRestore(null); registry.currentState = Lifecycle.State.RESUMED }
        fun destroy() { registry.currentState = Lifecycle.State.DESTROYED; viewModelStore.clear() }
    }
}

/** Cluster pixels to dp: the panel is laid out in the display's own 1920 × 720 pixels. */
@Composable
private fun Int.cpx(): Dp = with(LocalDensity.current) { this@cpx.toDp() }

@Composable
private fun Int.csp(): TextUnit = with(LocalDensity.current) { this@csp.toSp() }

private val ORANGE = Color(0xFFFF9F0A)
/** The part of the 1920 × 720 picture the cluster actually shows, measured from photos of the car. */
private const val WIN_TOP = 175
private const val WIN_H = 425
private val GREY = Color(0xFF8E8E93)

/**
 * The driver display: album art across the left third (fading into black so it melts into the
 * gauges), what's playing in the middle, tyre pressures on the right. Everything sits in the band
 * between the car's own top row (speed, gear) and bottom strip (range, trip), which it draws on top.
 */
@Composable
internal fun ClusterPanel(app: SharkOsApp) {
    val now by remember { flow { while (true) { emit(SystemClock.elapsedRealtime()); delay(1000) } } }.collectAsStateWithLifecycle(SystemClock.elapsedRealtime())
    val t by remember { app.car.telemetry(5000) }.collectAsStateWithLifecycle(Telemetry())
    val cp by CarPlayLink.song.collectAsStateWithLifecycle()
    val session by NowPlaying.track.collectAsStateWithLifecycle()
    val limits = remember { Prefs(app).let { TyreLimits(it.tyreLow, it.tyreHigh) } }
    ClusterLayout(chooseTrack(cp, session), now, t.tyres, limits, ClusterDisplay.style.value)
}

@Composable
internal fun ClusterLayout(track: Track?, nowElapsed: Long, tyres: List<Tyre?>, limits: TyreLimits, style: ClusterStyle = ClusterStyle.FADE) {
    val art = remember(track?.art) { track?.art?.asImageBitmap() }
    val colors = remember(track?.art) { track?.art?.let(::artColors) }
    val accent = colors?.accent ?: Sh.blue
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // The car blacks out its own top row (speed, gear) and bottom strip (range, trip): only the
        // band from WIN_TOP to WIN_TOP + WIN_H shows, so everything lives inside it.
        val card = style == ClusterStyle.CARD
        Box(Modifier.fillMaxSize().background(Brush.radialGradient(
            0f to accent.copy(alpha = if (card) 0.36f else 0.30f), 0.55f to (colors?.shade ?: Color.Black).copy(alpha = 0.15f), 1f to Color.Transparent,
            center = Offset(if (card) 320f else 200f, WIN_TOP + WIN_H / 2f), radius = if (card) 620f else 560f)))

        // 1/3: the whole cover, fitted to the window's height: a rounded card, or fading out to the
        // right; a clock when there's no art
        val coverArt: @Composable () -> Unit = {
            if (art != null) {
                Image(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (LocalInspectionMode.current) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFC46B), Color(0xFFFF6A4D), Color(0xFF7A1F5C)))))
            }
        }
        if (card) {
            if (art != null || LocalInspectionMode.current) {
                val r = RoundedCornerShape(24.cpx())
                Box(Modifier.padding(start = 120.cpx(), top = (WIN_TOP + 11).cpx()).size(404.cpx())
                    .shadow(28.cpx(), r).clip(r)) { coverArt() }
            }
        } else {
            Box(Modifier.padding(top = WIN_TOP.cpx()).size(WIN_H.cpx())
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                }) { coverArt() }
        }
        if (art == null && !LocalInspectionMode.current) {
            Column(Modifier.padding(start = 110.cpx(), top = 250.cpx())) {
                Txt(SimpleDateFormat("h:mm", Locale.getDefault()).format(Date()), Sh.text(120.csp(), FontWeight.SemiBold, Color.White, (-3).sp))
                Txt(SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date()), Sh.text(28.csp(), FontWeight.Medium, GREY))
            }
        }

        // 2/3: what's playing
        Column(Modifier.padding(start = (if (card) 600 else 560).cpx(), top = (WIN_TOP + 10).cpx()).width(600.cpx()).height((WIN_H - 15).cpx()),
            verticalArrangement = Arrangement.spacedBy(24.cpx(), Alignment.CenterVertically)) {
            if (track == null) {
                Txt("Nothing playing", Sh.text(44.csp(), FontWeight.Bold, GREY))
            } else {
                Box(Modifier.clip(RoundedCornerShape(15.cpx())).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 13.cpx(), vertical = 5.cpx())) {
                    Txt("${track.source.uppercase()} · ${if (track.playing) "NOW PLAYING" else "PAUSED"}", Sh.text(15.csp(), FontWeight.Bold, accent, 0.6.sp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.cpx())) {
                    Txt(track.title, Sh.text(60.csp(), FontWeight.ExtraBold, Color.White, (-1.4).sp), maxLines = 2)
                    if (track.artist.isNotBlank()) Txt(track.artist, Sh.text(30.csp(), FontWeight.Medium, Color(0xFFC7C7CC)), maxLines = 1)
                }
                if (track.knowsPosition && track.durationMs > 0) {
                    val pos = track.positionAt(nowElapsed)
                    Column(Modifier.width(520.cpx()), verticalArrangement = Arrangement.spacedBy(10.cpx())) {
                        Box(Modifier.fillMaxWidth().height(8.cpx()).clip(RoundedCornerShape(4.cpx())).background(Color.White.copy(alpha = 0.18f))) {
                            Box(Modifier.fillMaxWidth((pos.toFloat() / track.durationMs).coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.cpx())).background(accent))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Txt(clock(pos), Sh.text(20.csp(), FontWeight.Medium, GREY))
                            Txt("−" + clock((track.durationMs - pos).coerceAtLeast(0)), Sh.text(20.csp(), FontWeight.Medium, GREY))
                        }
                    }
                }
            }
        }

        // 3/3: tyres around a top-down ute
        // (starts below the car's parking-sensor icon, which sits at the window's top edge here)
        Column(Modifier.padding(start = 1360.cpx(), top = 240.cpx()).width(480.cpx())) {
            val warn = limits.check(tyres).associateBy { it.corner }
            Box(Modifier.width(480.cpx()).height(300.cpx())) {
                Ute(Modifier.padding(start = 165.cpx()).size(150.cpx(), 300.cpx()), lowCorners = warn.keys)
                TyreReading(Corner.LF, tyres, warn, Modifier.padding(top = 30.cpx()), end = false)
                TyreReading(Corner.RF, tyres, warn, Modifier.padding(start = 330.cpx(), top = 30.cpx()), end = true)
                TyreReading(Corner.LR, tyres, warn, Modifier.padding(top = 190.cpx()), end = false)
                TyreReading(Corner.RR, tyres, warn, Modifier.padding(start = 330.cpx(), top = 190.cpx()), end = true)
            }
        }
    }
}

@Composable
private fun TyreReading(c: Corner, tyres: List<Tyre?>, warn: Map<Corner, TyreWarning>, modifier: Modifier, end: Boolean) {
    val psi = tyres.getOrNull(c.ordinal)?.psi?.takeIf { it >= 5 }
    val w = warn[c]
    Column(modifier.width(150.cpx()), horizontalAlignment = if (end) Alignment.End else Alignment.Start) {
        Txt(c.label.uppercase(), Sh.text(15.csp(), FontWeight.SemiBold, GREY, 0.6.sp))
        Txt(psi?.roundToInt()?.toString() ?: "—", Sh.text(52.csp(), FontWeight.Bold, if (w != null) ORANGE else Color(0xFFF5F5F7), (-1).sp))
        Txt(when { w == null -> "psi"; w.low -> "Low"; else -> "High" }, Sh.text(17.csp(), FontWeight.SemiBold, if (w != null) ORANGE else GREY))
    }
}

/** The Shark 6 from above: cab and tray, wheels lit orange when that tyre is out of range. Drawn in a 150 × 300 box. */
@Composable
private fun Ute(modifier: Modifier, lowCorners: Set<Corner>) = Canvas(modifier) {
    val k = size.width / 150f
    fun wheel(x: Float, y: Float, c: Corner) = drawRoundRect(if (c in lowCorners) ORANGE else Color(0xFF3A3A3C),
        Offset(x * k, y * k), Size(20 * k, 52 * k), CornerRadius(7 * k))
    wheel(10f, 30f, Corner.LF); wheel(120f, 30f, Corner.RF); wheel(10f, 210f, Corner.LR); wheel(120f, 210f, Corner.RR)
    val body = Color(0xFF1C1C1E); val line = Color(0xFF3A3A3C); val glass = Color(0xFF2C2C2E)
    withTransform({ scale(0.88f, 1f, Offset(75f * k, 0f)) }) {
        fun r(x: Float, y: Float, w: Float, h: Float, rad: Float, c: Color) = drawRoundRect(c, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(rad * k))
        fun rs(x: Float, y: Float, w: Float, h: Float, rad: Float, c: Color) = drawRoundRect(c, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(rad * k), style = Stroke(2 * k))
        r(4f, 84f, 14f, 10f, 4f, line); r(132f, 84f, 14f, 10f, 4f, line)                 // mirrors
        val cab = Path().apply {
            moveTo(30 * k, 14 * k); quadraticTo(75 * k, 2 * k, 120 * k, 14 * k); quadraticTo(130 * k, 18 * k, 130 * k, 34 * k)
            lineTo(130 * k, 160 * k); lineTo(20 * k, 160 * k); lineTo(20 * k, 34 * k); quadraticTo(20 * k, 18 * k, 30 * k, 14 * k); close()
        }
        drawPath(cab, body); drawPath(cab, line, style = Stroke(2 * k))
        drawPath(Path().apply { moveTo(34 * k, 50 * k); quadraticTo(75 * k, 38 * k, 116 * k, 50 * k); lineTo(110 * k, 82 * k); quadraticTo(75 * k, 76 * k, 40 * k, 82 * k); close() }, glass)
        drawPath(Path().apply { moveTo(40 * k, 136 * k); quadraticTo(75 * k, 132 * k, 110 * k, 136 * k); lineTo(112 * k, 152 * k); lineTo(38 * k, 152 * k); close() }, glass)
        r(20f, 164f, 110f, 126f, 10f, body); rs(20f, 164f, 110f, 126f, 10f, line)          // tray
        r(32f, 174f, 86f, 104f, 6f, Color(0xFF151517)); rs(32f, 174f, 86f, 104f, 6f, glass)
        for (x in listOf(54f, 75f, 96f)) drawLine(Color(0xFF232325), Offset(x * k, 178 * k), Offset(x * k, 274 * k), 3 * k)
        r(24f, 284f, 102f, 6f, 3f, glass)                                                     // tailgate
    }
}
