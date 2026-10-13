package com.sharkos.launcher.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sharkos.launcher.BuildConfig
import com.sharkos.launcher.SharkOsApp
import com.sharkos.launcher.apps.Apps
import com.sharkos.launcher.car.CarLink
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.car.Zone
import com.sharkos.launcher.media.NowPlaying
import com.sharkos.launcher.sensors.Attitude
import com.sharkos.launcher.sensors.Inclinometer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

enum class Screen { HOME, CLIMATE, APPS, OFFROAD, SETTINGS }

/** The whole launcher: one screen at a time above the persistent bottom bar. */
@Composable
fun Root(app: SharkOsApp, screen: Screen, onScreen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val car = app.car
    val apps = app.apps
    val scope = rememberCoroutineScope()

    // Polling and the clock run only while SharkOS is on screen (collectAsStateWithLifecycle).
    val telemetry by remember { car.telemetry() }.collectAsStateWithLifecycle(Telemetry())
    val climate by car.climate.collectAsStateWithLifecycle()
    val link by car.link.collectAsStateWithLifecycle()
    val now by remember { ticker() }.collectAsStateWithLifecycle(System.currentTimeMillis())
    val track by NowPlaying.track.collectAsStateWithLifecycle()
    val allApps by apps.all.collectAsStateWithLifecycle()
    val dock by apps.dock.collectAsStateWithLifecycle()

    var mediaAccess by remember { mutableStateOf(NowPlaying.hasAccess(ctx)) }
    var isHome by remember { mutableStateOf(isDefaultHome(ctx)) }
    val prefs = remember { com.sharkos.launcher.data.Prefs(ctx) }
    var autoLaunch by remember { mutableStateOf(prefs.autoLaunch) }
    var canOverlay by remember { mutableStateOf(com.sharkos.launcher.AutoLaunch.canLaunchFromBackground(ctx)) }
    LifecycleResumeEffect(Unit) {
        mediaAccess = NowPlaying.hasAccess(ctx)
        isHome = isDefaultHome(ctx)
        canOverlay = com.sharkos.launcher.AutoLaunch.canLaunchFromBackground(ctx)
        if (mediaAccess) NowPlaying.attach(ctx)
        scope.launch { apps.refresh() }
        onPauseOrDispose { }
    }

    // The motion sensor runs only on the screens that show it, and only while on screen.
    val inclino = remember { Inclinometer(ctx.applicationContext) }
    var attitude by remember { mutableStateOf<Attitude?>(null) }
    var zeroed by remember { mutableIntStateOf(0) }    // bumps after a zero so the label updates
    val wantsSensor = screen == Screen.HOME || screen == Screen.OFFROAD
    if (wantsSensor && inclino.available) {
        LifecycleResumeEffect(screen) {
            inclino.onChange = { attitude = it }
            inclino.start()
            onPauseOrDispose { inclino.stop(); inclino.onChange = null }
        }
    }

    // Tell the driver when the car turns a command down; quiet on a bench with no car.
    var notice by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(screen) { if (screen != Screen.APPS) picking = false }
    LaunchedEffect(Unit) {
        car.commandResults.collect { r ->
            if (r.result.isFailure && car.link.value == CarLink.CONNECTED) {
                notice = "${r.label}: ${r.result.exceptionOrNull()?.message ?: "didn't go through"}"
                delay(3500)
                notice = null
            }
        }
    }

    BackHandler(screen != Screen.HOME) { onScreen(Screen.HOME) }

    val dockApps = dock.mapNotNull { pkg -> apps.find(pkg)?.let { DockApp(it.pkg, it.label) } }
    val bar = remember(car, apps) {
        BarActions(
            nudgeTemp = car::nudgeTemp,
            cycleHeat = car::cycleSeatHeat,
            cycleVent = car::cycleSeatVent,
            toggleAc = { car.setAcOn(!car.climate.value.ac) },
            home = { onScreen(Screen.HOME) },
            apps = { onScreen(Screen.APPS) },
            climate = { onScreen(Screen.CLIMATE) },
            launch = apps::launch,
            addApp = { picking = true; onScreen(Screen.APPS) },
            removeApp = { d -> apps.unpin(d.pkg); notice = "Removed ${d.label} from the bar" },
        )
    }

    CompositionLocalProvider(LocalIcons provides remember(apps) { apps.iconSource() }) {
        Column(Modifier.fillMaxSize().background(Sh.bg)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(
                            now = now,
                            telemetry = telemetry,
                            link = link,
                            track = track?.let { t ->
                                if (t.playing) t.copy(positionMs = t.positionMs + (SystemClock.elapsedRealtime() - t.sampledAt)) else t
                            },
                            mediaAccess = mediaAccess,
                            attitude = attitude,
                            a = remember {
                                HomeActions(
                                    playPause = NowPlaying::playPause,
                                    next = NowPlaying::next,
                                    previous = NowPlaying::previous,
                                    openMusic = { apps.launch(Apps.PKG_MUSIC) },
                                    grantMedia = { openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                                    offroad = { onScreen(Screen.OFFROAD) },
                                    settings = { onScreen(Screen.SETTINGS) },
                                )
                            },
                        )
                        Screen.CLIMATE -> ClimateScreen(climate, remember {
                            ClimateActions(
                                power = car::setClimatePower,
                                temp = car::nudgeTemp,
                                fan = car::setFanSpeed,
                                airflow = car::setAirflow,
                                auto = car::setAuto,
                                ac = car::setAcOn,
                                recirc = car::setRecirculation,
                                frontDemist = car::setFrontDefrost,
                                rearDemist = car::setRearDefrost,
                                dual = car::setDualZone,
                                seatHeat = car::setSeatHeat,
                                seatVent = car::setSeatVent,
                                back = { onScreen(Screen.HOME) },
                            )
                        })
                        Screen.APPS -> AppsScreen(
                            allApps, dock,
                            onOpen = apps::launch,
                            onTogglePin = { pkg ->
                                if (picking) {
                                    if (apps.pin(pkg)) onScreen(Screen.HOME)
                                    else notice = "The bar holds ${Apps.MAX_DOCK} apps: hold one on the bar to remove it"
                                } else if (!apps.togglePin(pkg)) {
                                    notice = "The bar holds ${Apps.MAX_DOCK} apps: hold one on the bar to remove it"
                                }
                            },
                            onSettings = { onScreen(Screen.SETTINGS) },
                            picking = picking,
                            onCancelPick = { onScreen(Screen.HOME) },
                        )
                        Screen.OFFROAD -> OffroadScreen(
                            attitude.takeIf { inclino.available },
                            calibrated = zeroed >= 0 && inclino.calibrated,
                            onZero = { if (inclino.calibrate()) zeroed++ },
                            onBack = { onScreen(Screen.HOME) },
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            SettingsInfo(BuildConfig.VERSION_NAME, isHome, mediaAccess, link, car.backendName, zeroed >= 0 && inclino.calibrated, autoLaunch, canOverlay),
                            SettingsActions(
                                chooseHome = { openSettings(ctx, Settings.ACTION_HOME_SETTINGS) },
                                grantMedia = { openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS) },
                                zeroInclino = { if (inclino.calibrate()) zeroed++ else notice = "Zero needs the motion sensor: open Off-road first" },
                                setAutoLaunch = { on -> prefs.autoLaunch = on; autoLaunch = on },
                                allowOverlay = {
                                    runCatching {
                                        ctx.startActivity(
                                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                        )
                                    }.onFailure { openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION) }
                                },
                                back = { onScreen(Screen.HOME) },
                            ),
                        )
                    }
                }
                NoticePill(notice, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            }
            BottomBar(climate, dockApps, screen, bar)
        }
    }
}

@Composable
private fun NoticePill(notice: String?, modifier: Modifier) =
    AnimatedVisibility(visible = notice != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Txt(
            notice.orEmpty(),
            Sh.text(16.sp, FontWeight.SemiBold),
            Modifier.clip(RoundedCornerShape(22.dp)).background(Sh.raised).padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

private fun ticker() = flow {
    while (true) {
        val t = System.currentTimeMillis()
        emit(t)
        delay(1000 - t % 1000)
    }
}

private fun isDefaultHome(ctx: Context): Boolean = runCatching {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    ctx.packageManager.resolveActivity(i, 0)?.activityInfo?.packageName == ctx.packageName
}.getOrDefault(false)

private fun openSettings(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .recoverCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
