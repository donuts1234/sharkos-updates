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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.sharkos.launcher.car.CarPlayLink
import com.sharkos.launcher.car.CarPlaySong
import com.sharkos.launcher.car.Telemetry
import com.sharkos.launcher.car.Zone
import com.sharkos.launcher.media.NowPlaying
import com.sharkos.launcher.media.Track
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
    // These change every second or two, so only the Home screen reads them: reading them here would
    // redraw every screen, Settings included, on each tick.
    val telemetry = remember { car.telemetry() }.collectAsStateWithLifecycle(Telemetry())
    val now = remember { ticker() }.collectAsStateWithLifecycle(System.currentTimeMillis())
    val climate by car.climate.collectAsStateWithLifecycle()
    val link by car.link.collectAsStateWithLifecycle()
    val session = NowPlaying.track.collectAsStateWithLifecycle()
    val carPlay = CarPlayLink.song.collectAsStateWithLifecycle()
    val carPlayLinked by CarPlayLink.linked.collectAsStateWithLifecycle()
    val shownTrack = remember { derivedStateOf { chooseTrack(carPlay.value, session.value) } }
    val hasCarData by remember { derivedStateOf { telemetry.value.socPercent != null } }
    val allApps by apps.all.collectAsStateWithLifecycle()
    val dock by apps.dock.collectAsStateWithLifecycle()
    val updater = app.updater
    val update by updater.state.collectAsStateWithLifecycle()

    var mediaAccess by remember { mutableStateOf(NowPlaying.hasAccess(ctx)) }
    var isHome by remember { mutableStateOf(isDefaultHome(ctx)) }
    val prefs = remember { com.sharkos.launcher.data.Prefs(ctx) }
    var autoLaunch by remember { mutableStateOf(prefs.autoLaunch) }
    var wallpaper by remember { mutableStateOf(Wallpaper.entries.firstOrNull { it.name == prefs.wallpaper } ?: Wallpaper.BLACK) }
    var corners by remember { mutableStateOf(Corners.entries.firstOrNull { it.name == prefs.corners } ?: Corners.LIGHT) }
    var tempSide by remember { mutableStateOf(TempSide.entries.firstOrNull { it.name == prefs.tempSide } ?: TempSide.RIGHT) }
    var barStyle by remember { mutableStateOf(BarStyle.entries.firstOrNull { it.name == prefs.barStyle } ?: BarStyle.BALANCED) }
    var canOverlay by remember { mutableStateOf(com.sharkos.launcher.AutoLaunch.canLaunchFromBackground(ctx)) }
    var carData by remember { mutableStateOf(carDataAllowed(ctx)) }
    var canInstall by remember { mutableStateOf(app.updater.canInstall()) }
    var notice by remember { mutableStateOf<String?>(null) }
    val askCarData = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        carData = carDataAllowed(ctx)
        if (granted) car.reconnect()
        else notice = "The car didn't show the permission prompt: use the adb command in Settings → Car"
    }
    val askLights = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { r ->
        if (r.values.any { !it }) notice = "The car didn't allow it: use the adb command under Ambient lighting"
        else com.sharkos.launcher.lights.Lights.connect(ctx)
    }
    val lightsActions = remember {
        LightsActions(
            requestPermission = { askLights.launch(com.sharkos.launcher.lights.Lights.permissionsNeeded()) },
            enableBluetooth = {
                if (!openSettings(ctx, Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_ENABLE)) &&
                    !openSettings(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))) notice = "Turn Bluetooth on in the car's settings"
            },
        )
    }
    LifecycleResumeEffect(Unit) {
        mediaAccess = NowPlaying.hasAccess(ctx)
        isHome = isDefaultHome(ctx)
        canOverlay = com.sharkos.launcher.AutoLaunch.canLaunchFromBackground(ctx)
        canInstall = app.updater.canInstall()
        val nowCarData = carDataAllowed(ctx)
        if (nowCarData && !carData) car.reconnect()   // granted over adb while SharkOS was open
        carData = nowCarData
        if (mediaAccess) NowPlaying.attach(ctx)
        CarPlayLink.start(ctx)
        com.sharkos.launcher.lights.Lights.start(ctx)
        scope.launch { apps.refresh() }
        scope.launch { updater.checkDaily() }
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

    // The daily check found something: say so once, pointing at Settings.
    LaunchedEffect(update) {
        val u = update
        if (u is com.sharkos.launcher.update.Updater.State.Available && screen != Screen.SETTINGS) {
            notice = "SharkOS ${u.r.versionName} is available: Settings → Updates"
            delay(5000)
            notice = null
        }
    }

    BackHandler(screen != Screen.HOME) { onScreen(Screen.HOME) }

    val screenState = androidx.compose.runtime.rememberUpdatedState(screen)
    val dockApps = dock.mapNotNull { pkg -> apps.find(pkg)?.let { DockApp(it.pkg, it.label) } }
    val bar = remember(car, apps) {
        BarActions(
            nudgeCabin = car::nudgeCabin,
            setCabin = car::setCabinTemp,
            nudgeTemp = car::nudgeTemp,
            preset = car::applyPreset,
            nudgeFan = car::nudgeFan,
            cycleHeat = car::cycleSeatHeat,
            cycleVent = car::cycleSeatVent,
            toggleAc = { car.setAcOn(!car.climate.value.ac) },
            home = { onScreen(Screen.HOME) },
            apps = { onScreen(Screen.APPS) },
            climate = { onScreen(Screen.CLIMATE) },
            launch = apps::launch,
            addApp = { picking = true; onScreen(Screen.APPS) },
            removeApp = { d -> apps.unpin(d.pkg); notice = "Removed ${d.label} from the bar" },
            setFan = car::setFanSpeed,
            back = { if (screenState.value != Screen.HOME) onScreen(Screen.HOME) },
        )
    }

    CompositionLocalProvider(LocalIcons provides remember(apps) { apps.iconSource() }, LocalWallpaper provides wallpaper) {
        // Rounded outer corners: the screen's black shows in them
        Column(Modifier.fillMaxSize().background(Color.Black).clip(RoundedCornerShape(corners.radius.dp)).wallpaper(wallpaper)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(
                            now = now.value,
                            telemetry = telemetry.value,
                            link = link,
                            track = shownTrack.value?.let { t -> t.copy(positionMs = t.positionAt(SystemClock.elapsedRealtime())) },
                            mediaAccess = mediaAccess,
                            attitude = attitude,
                            carDataAllowed = carData || hasCarData,
                            a = remember {
                                HomeActions(
                                    // CarPlay takes its own media keys; app players take their session's controls
                                    playPause = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_PLAY_PAUSE) else NowPlaying.playPause() },
                                    next = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_NEXT) else NowPlaying.next() },
                                    previous = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_PREV) else NowPlaying.previous() },
                                    openMusic = { apps.launch(Apps.PKG_MUSIC) },
                                    grantMedia = { if (!openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) notice = HIDDEN_PAGE.replace("shown under it", "in Settings → Now playing") },
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
                            SettingsInfo(
                                version = BuildConfig.VERSION_NAME,
                                isDefaultHome = isHome,
                                mediaAccess = mediaAccess,
                                link = link,
                                backend = car.backendName,
                                inclinoZeroed = zeroed >= 0 && inclino.calibrated,
                                autoLaunch = autoLaunch,
                                canLaunchFromBackground = canOverlay,
                                update = update,
                                updatesConfigured = updater.configured,
                                canInstall = canInstall,
                                carDataAllowed = carData || hasCarData,
                                barStyle = barStyle,
                                tempSide = tempSide,
                                carPlayLinked = carPlayLinked,
                                wallpaper = wallpaper,
                                corners = corners,
                                climate = climate,
                                dock = dockApps,
                            ),
                            SettingsActions(
                                chooseHome = { if (!openSettings(ctx, Settings.ACTION_HOME_SETTINGS)) notice = HIDDEN_PAGE },
                                grantMedia = { if (!openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) notice = HIDDEN_PAGE },
                                zeroInclino = { if (inclino.calibrate()) zeroed++ else notice = "Zero needs the motion sensor: open Off-road first" },
                                setAutoLaunch = { on -> prefs.autoLaunch = on; autoLaunch = on },
                                checkUpdate = { scope.launch { updater.check() } },
                                installUpdate = { r -> scope.launch { updater.downloadAndInstall(r) } },
                                allowInstalls = { if (!openSettings(ctx, updater.allowInstallsIntent())) notice = HIDDEN_PAGE },
                                allowCarData = { askCarData.launch(CAR_DATA) },
                                setBarStyle = { st -> prefs.barStyle = st.name; barStyle = st },
                                setTempSide = { sd -> prefs.tempSide = sd.name; tempSide = sd },
                                setWallpaper = { w -> prefs.wallpaper = w.name; wallpaper = w },
                                setCorners = { c -> prefs.corners = c.name; corners = c },
                                allowOverlay = {
                                    val page = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                                    if (!openSettings(ctx, page) && !openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) notice = HIDDEN_PAGE
                                },
                                reconnectCar = { car.reconnect(); notice = "Reconnecting to the car…" },
                                resetBar = { apps.resetDock(); notice = "The bar's apps are back to the defaults" },
                                restart = { restartSharkOs(ctx) },
                                lights = lightsActions,
                                androidSettings = { if (!openSettings(ctx, Intent(Settings.ACTION_SETTINGS))) notice = "BYD hides Android's settings on this unit" },
                                back = { onScreen(Screen.HOME) },
                            ),
                            start = if (update is com.sharkos.launcher.update.Updater.State.Available) SettingsPage.UPDATES else SettingsPage.GENERAL,
                        )
                    }
                }
                NoticePill(notice, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
            }
            BottomBar(climate, dockApps, screen, bar, style = barStyle, tempSide = tempSide)
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

/**
 * Which "now playing" to show: whatever is playing, CarPlay first (it has no Android session, so the
 * car's CarPlay service is its only source); with nothing playing, the app session, then CarPlay.
 */
private fun chooseTrack(carPlay: CarPlaySong?, session: Track?): Track? {
    val cp = carPlay?.takeIf { it.title.isNotBlank() }?.let { s ->
        Track(
            title = s.title, artist = s.artist, source = "CarPlay", art = s.art, playing = s.playing,
            positionMs = s.elapsedMs.coerceAtLeast(0), durationMs = s.durationMs, sampledAt = s.sampledAt,
            carPlay = true, knowsPosition = s.elapsedMs >= 0,
        )
    }
    return when {
        cp?.playing == true -> cp
        session?.playing == true -> session
        else -> session ?: cp
    }
}

/** Close and reopen SharkOS in a fresh process. */
private fun restartSharkOs(ctx: Context) {
    val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.component ?: return
    ctx.startActivity(Intent.makeRestartActivityTask(launch))
    Runtime.getRuntime().exit(0)
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

/**
 * Open an Android settings page. BYD hides several of them on the Shark 6, so check the page exists
 * first and report false instead of silently doing nothing; the caller then points at the adb command.
 */
private fun openSettings(ctx: Context, intent: Intent): Boolean {
    val i = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (ctx.packageManager.resolveActivity(i, 0) == null) return false
    return runCatching { ctx.startActivity(i) }.isSuccess
}

private fun openSettings(ctx: Context, action: String): Boolean = openSettings(ctx, Intent(action))

private const val HIDDEN_PAGE = "This car hides that settings page: use the adb command shown under it"

private const val CAR_DATA = "android.permission.BYDAUTO_STATISTIC_COMMON"

private fun carDataAllowed(ctx: Context): Boolean =
    // A unit without BYD's permission (a bench) has nothing to grant: treat it as allowed.
    runCatching { ctx.packageManager.getPermissionInfo(CAR_DATA, 0) }.isFailure ||
        ctx.checkSelfPermission(CAR_DATA) == android.content.pm.PackageManager.PERMISSION_GRANTED
