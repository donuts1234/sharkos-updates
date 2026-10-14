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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
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

enum class Screen { HOME, CLIMATE, APPS, OFFROAD, SETTINGS, MUSIC, TRIPS }

/** The whole launcher: one screen at a time above the persistent bottom bar. */
@Composable
fun Root(app: SharkOsApp, screen: Screen, onScreen: (Screen) -> Unit) {
    val ctx = LocalContext.current
    remember { NightMode.load(ctx); true }
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
    var topEdge by remember { mutableStateOf(prefs.topEdge) }
    var corners by remember { mutableStateOf(Corners.entries.firstOrNull { it.name == prefs.corners } ?: Corners.LIGHT) }
    var tempSide by remember { mutableStateOf(TempSide.entries.firstOrNull { it.name == prefs.tempSide } ?: TempSide.RIGHT) }
    var carPlayInSharkOs by remember { mutableStateOf(prefs.carPlayInSharkOs) }
    var carPlayTopGap by remember { mutableStateOf(prefs.carPlayTopGap) }
    var carPlayAlwaysBar by remember { mutableStateOf(prefs.carPlayAlwaysBar) }
    var tyreAlerts by remember { mutableStateOf(prefs.tyreAlerts) }
    var tyreLimits by remember { mutableStateOf(com.sharkos.launcher.car.TyreLimits(prefs.tyreLow, prefs.tyreHigh)) }
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

    // Every notice goes by itself after a few seconds (longer ones get longer), or straight away when tapped.
    // One timer for all of them: each new notice restarts it, so a newer message is never cut short.
    LaunchedEffect(notice) {
        val n = notice ?: return@LaunchedEffect
        delay(if (n.length > 60) 7000 else 4000)
        notice = null
    }

    // Tell the driver when the car turns a command down; quiet on a bench with no car.
    var picking by remember { mutableStateOf(false) }
    /** Picking an app for a Shortcuts spot on Home (its index), rather than for the bar. */
    var pickShortcut by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(screen) { if (screen != Screen.APPS) { picking = false; pickShortcut = null } }
    var homeCards by remember { mutableStateOf(HomeCard.parse(prefs.homeCards)) }
    var settingsStart by remember { mutableStateOf<SettingsPage?>(null) }
    LaunchedEffect(screen) { if (screen != Screen.SETTINGS) settingsStart = null }
    LaunchedEffect(Unit) {
        car.commandResults.collect { r ->
            if (r.result.isFailure && car.link.value == CarLink.CONNECTED) {
                notice = "${r.label}: ${r.result.exceptionOrNull()?.message ?: "didn't go through"}"
            }
        }
    }

    // The daily check found something: say so once, pointing at Settings.
    LaunchedEffect(update) {
        val u = update
        if (u is com.sharkos.launcher.update.Updater.State.Available && screen != Screen.SETTINGS) {
            notice = "SharkOS ${u.r.versionName} is available: Settings → Updates"
        }
    }

    // Tyre alerts: shown until tapped; a dismissed one comes back only if the tyre recovers and drops again
    val tyreWarnings by remember { derivedStateOf { if (tyreAlerts) tyreLimits.check(telemetry.value.tyres) else emptyList() } }
    var dismissedTyres by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(tyreWarnings) { dismissedTyres = dismissedTyres intersect tyreWarnings.map { it.key }.toSet() }
    val tyreAlert = tyreWarnings.filter { it.key !in dismissedTyres }

    BackHandler(screen != Screen.HOME) { onScreen(Screen.HOME) }

    val screenState = androidx.compose.runtime.rememberUpdatedState(screen)
    // Ambient lighting on the bar: both zones as one; the bar shows the door trims' colour
    val lightZones by com.sharkos.launcher.lights.Lights.zones.collectAsStateWithLifecycle()
    val lightLink by com.sharkos.launcher.lights.Lights.link.collectAsStateWithLifecycle()
    val followArt by com.sharkos.launcher.lights.AutoLights.followArt.collectAsStateWithLifecycle()
    val barLights = remember(lightZones, lightLink) {
        com.sharkos.launcher.lights.Lights.address(ctx)?.let {
            val z = lightZones.values
            val first = lightZones.getValue(com.sharkos.launcher.lights.Lights.Zone.TRIMS)
            BarLights(lightLink == com.sharkos.launcher.lights.Lights.Link.CONNECTED, z.any { it.on }, first.brightness, first.hue)
        }
    }
    // BYD's CarPlay opens with SharkOS's HVAC Bar on top (falls back to plain BYD CarPlay without the overlay permission)
    val openApp: (String) -> Unit = remember(apps) {
        { pkg -> if (pkg != CarPlayOverlay.BYD_CARPLAY || CarPlayOverlay.open(ctx) != null) apps.launch(pkg) }
    }
    val dockApps = dock.mapNotNull { pkg -> apps.find(pkg)?.let { DockApp(it.pkg, it.label) } }
    val bar = remember(car, apps, openApp) {
        BarActions(
            nudgeCabin = car::nudgeCabin,
            setCabin = car::setCabinTemp,
            nudgeFan = car::nudgeFan,
            cycleHeat = car::cycleSeatHeat,
            cycleVent = car::cycleSeatVent,
            toggleAc = { car.setAcOn(!car.climate.value.ac) },
            home = { onScreen(Screen.HOME) },
            apps = { onScreen(Screen.APPS) },
            climate = { onScreen(Screen.CLIMATE) },
            launch = openApp,
            addApp = { picking = true; onScreen(Screen.APPS) },
            removeApp = { d -> apps.unpin(d.pkg); notice = "Removed ${d.label} from the bar" },
            setFan = car::setFanSpeed,
            lightsOn = { com.sharkos.launcher.lights.Lights.setOn(ctx, null, it) },
            lightsBrightness = { com.sharkos.launcher.lights.Lights.setBrightness(ctx, null, it) },
            lightsHue = { com.sharkos.launcher.lights.Lights.setHue(ctx, null, it) },
            back = { if (screenState.value != Screen.HOME) onScreen(Screen.HOME) },
        )
    }

    val homeActions = remember {
        HomeActions(
            // CarPlay takes its own media keys; app players take their session's controls
            playPause = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_PLAY_PAUSE) else NowPlaying.playPause() },
            next = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_NEXT) else NowPlaying.next() },
            previous = { if (shownTrack.value?.carPlay == true) CarPlayLink.key(CarPlayLink.KEY_PREV) else NowPlaying.previous() },
            openMusic = { apps.launch(Apps.PKG_MUSIC) },
            grantMedia = { if (!openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) notice = HIDDEN_PAGE.replace("shown under it", "in Settings → Now playing") },
            offroad = { onScreen(Screen.OFFROAD) },
            settings = { onScreen(Screen.SETTINGS) },
            openNowPlaying = { onScreen(Screen.MUSIC) },
            openTrips = { onScreen(Screen.TRIPS) },
            openClimate = { onScreen(Screen.CLIMATE) },
            toggleAc = { car.setAcOn(!car.climate.value.ac) },
            toggleRecirc = { car.setRecirculation(!car.climate.value.recirc) },
            toggleDemist = { car.setFrontDefrost(!car.climate.value.frontDefrost) },
            lightsOn = { com.sharkos.launcher.lights.Lights.setOn(ctx, null, it) },
            lightsBrightness = { com.sharkos.launcher.lights.Lights.setBrightness(ctx, null, it) },
            lightsHue = { com.sharkos.launcher.lights.Lights.setHue(ctx, null, it) },
            openLights = { settingsStart = SettingsPage.AMBIENT; onScreen(Screen.SETTINGS) },
            launch = openApp,
            addShortcut = { i -> pickShortcut = i; picking = true; onScreen(Screen.APPS) },
            removeShortcut = { i -> apps.setShortcut(i, ""); notice = "Shortcut removed" },
        )
    }

    // What the optional Home cards need, read only for the cards that are showing
    val shortcutPkgs by apps.shortcuts.collectAsStateWithLifecycle()
    val followWall by com.sharkos.launcher.lights.AutoLights.followWallpaper.collectAsStateWithLifecycle()
    @Composable
    fun homeExtras(cards: List<HomeCard>): HomeExtras {
        val drive = if (HomeCard.DRIVE in cards) remember { car.trips(5000) }.collectAsStateWithLifecycle(com.sharkos.launcher.car.Trips()).value.drive else null
        return HomeExtras(
            drive = drive,
            climate = climate,
            lights = barLights,
            lightsNote = when { followArt -> "Matching album art"; followWall -> "Matching the wallpaper"; else -> null },
            shortcuts = shortcutPkgs.map { pkg -> pkg.takeIf { it.isNotBlank() }?.let { p -> apps.find(p)?.let { DockApp(it.pkg, it.label) } } },
        )
    }

    // The music page tints the HVAC Bar with the album's colours
    val musicArt = shownTrack.value?.art
    val barArt = if (screen == Screen.MUSIC && musicArt != null) remember(musicArt) { artColors(musicArt) } else null

    CompositionLocalProvider(LocalIcons provides remember(apps) { apps.iconSource() }, LocalWallpaper provides wallpaper) {
        // Rounded outer corners: the screen's black shows in them. The top few pixels of the panel sit
        // under the bezel, so SharkOS starts below them (or its top corners would look cut off).
        val edge = with(androidx.compose.ui.platform.LocalDensity.current) { topEdge.toDp() }
        Column(Modifier.fillMaxSize().background(Color.Black).padding(top = edge).clip(RoundedCornerShape(corners.radius.dp)).wallpaper(wallpaper)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> if (NightMode.active.value) NightHome(
                            now = now.value, t = telemetry.value,
                            track = shownTrack.value?.let { t -> t.copy(positionMs = t.positionAt(SystemClock.elapsedRealtime())) },
                            a = homeActions, limits = tyreLimits, style = NightMode.style.value,
                        ) else HomeScreen(
                            now = now.value,
                            telemetry = telemetry.value,
                            link = link,
                            track = shownTrack.value?.let { t -> t.copy(positionMs = t.positionAt(SystemClock.elapsedRealtime())) },
                            mediaAccess = mediaAccess,
                            attitude = attitude,
                            carDataAllowed = carData || hasCarData,
                            a = homeActions,
                            tyreLimits = tyreLimits,
                            cards = homeCards,
                            extras = homeExtras(homeCards),
                        )
                        Screen.CLIMATE -> ClimateScreen(climate, outsideC = telemetry.value.outsideTempC, a = remember {
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
                                setTemp = car::setZoneTemp,
                            )
                        })
                        Screen.APPS -> AppsScreen(
                            allApps, dock,
                            onOpen = openApp,
                            onTogglePin = { pkg ->
                                val spot = pickShortcut
                                if (spot != null) {
                                    apps.setShortcut(spot, pkg); onScreen(Screen.HOME)
                                } else if (picking) {
                                    if (apps.pin(pkg)) onScreen(Screen.HOME)
                                    else notice = "The bar holds ${Apps.MAX_DOCK} apps: hold one on the bar to remove it"
                                } else if (!apps.togglePin(pkg)) {
                                    notice = "The bar holds ${Apps.MAX_DOCK} apps: hold one on the bar to remove it"
                                }
                            },
                            onSettings = { onScreen(Screen.SETTINGS) },
                            picking = picking,
                            onCancelPick = { onScreen(Screen.HOME) },
                            pickTitle = if (pickShortcut != null) "Add a shortcut" else "Add to the bar",
                        )
                        Screen.OFFROAD -> OffroadScreen(
                            attitude.takeIf { inclino.available },
                            calibrated = zeroed >= 0 && inclino.calibrated,
                            onZero = { if (inclino.calibrate()) zeroed++ },
                            onBack = { onScreen(Screen.HOME) },
                        )
                        Screen.MUSIC -> MusicScreen(
                            shownTrack.value?.takeIf { now.value > 0 }?.let { t -> t.copy(positionMs = t.positionAt(SystemClock.elapsedRealtime())) },
                            homeActions,
                            onHome = { onScreen(Screen.HOME) },
                            lightsFollowArt = if (com.sharkos.launcher.lights.Lights.address(ctx) != null) followArt else null,
                            onLightsFollowArt = { com.sharkos.launcher.lights.AutoLights.setFollowArt(ctx, it) },
                        )
                        Screen.TRIPS -> {
                            // Read only while the page is open
                            val trips by remember { car.trips() }.collectAsStateWithLifecycle(com.sharkos.launcher.car.Trips())
                            TripsScreen(trips, onHome = { onScreen(Screen.HOME) })
                        }
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
                                tyreAlerts = tyreAlerts,
                                homeCards = homeCards,
                                carPlayInSharkOs = carPlayInSharkOs,
                                carPlayTopGap = carPlayTopGap,
                                carPlayAlwaysBar = carPlayAlwaysBar,
                                tyreLimits = tyreLimits,
                                carPlayLinked = carPlayLinked,
                                wallpaper = wallpaper,
                                corners = corners,
                                topEdge = topEdge,
                                climate = climate,
                                dock = dockApps,
                            ),
                            SettingsActions(
                                chooseHome = { if (!openSettings(ctx, Settings.ACTION_HOME_SETTINGS)) notice = HIDDEN_PAGE },
                                grantMedia = { if (!openSettings(ctx, Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) notice = HIDDEN_PAGE },
                                zeroInclino = { if (inclino.calibrate()) zeroed++ else notice = "Zero needs the motion sensor: open Off-road first" },
                                setAutoLaunch = { on -> prefs.autoLaunch = on; autoLaunch = on },
                                setTyreAlerts = { on -> prefs.tyreAlerts = on; tyreAlerts = on },
                                setCarPlayInSharkOs = { on -> prefs.carPlayInSharkOs = on; carPlayInSharkOs = on },
                                setCarPlayTopGap = { g -> prefs.carPlayTopGap = g; carPlayTopGap = g },
                                setCarPlayAlwaysBar = { on -> prefs.carPlayAlwaysBar = on; carPlayAlwaysBar = on },
                                setHomeCards = { l -> prefs.homeCards = l.map { it.name }; homeCards = l },
                                setTyreLimits = { l -> prefs.tyreLow = l.lowPsi; prefs.tyreHigh = l.highPsi; tyreLimits = l },
                                checkUpdate = { scope.launch { updater.check() } },
                                installUpdate = { r -> scope.launch { updater.downloadAndInstall(r) } },
                                allowInstalls = { if (!openSettings(ctx, updater.allowInstallsIntent())) notice = HIDDEN_PAGE },
                                allowCarData = { askCarData.launch(CAR_DATA) },
                                setBarStyle = { st -> prefs.barStyle = st.name; barStyle = st },
                                setTempSide = { sd -> prefs.tempSide = sd.name; tempSide = sd },
                                setWallpaper = { w -> prefs.wallpaper = w.name; wallpaper = w; com.sharkos.launcher.lights.AutoLights.wallpaperChanged(ctx, w) },
                                setCorners = { c -> prefs.corners = c.name; corners = c },
                                setTopEdge = { e -> prefs.topEdge = e; topEdge = e },
                                allowOverlay = {
                                    val page = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${ctx.packageName}"))
                                    if (!openSettings(ctx, page) && !openSettings(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)) notice = HIDDEN_PAGE
                                },
                                reconnectCar = { car.reconnect(); notice = "Reconnecting to the car…" },
                                resetBar = { apps.resetDock(); notice = "The bar's apps are back to the defaults" },
                                restart = { restartSharkOs(ctx) },
                                lights = lightsActions,
                                openCarPlay = { CarPlayOverlay.open(ctx)?.let { notice = it } },
                                androidSettings = { if (!openSettings(ctx, Intent(Settings.ACTION_SETTINGS))) notice = "BYD hides Android's settings on this unit" },
                                back = { onScreen(Screen.HOME) },
                            ),
                            start = settingsStart ?: if (update is com.sharkos.launcher.update.Updater.State.Available) SettingsPage.UPDATES else SettingsPage.GENERAL,
                        )
                    }
                }
                // The cluster design bubble: in SharkOS's own window, so BYD's bars stay hidden
                LaunchedEffect(screen) { if (screen != Screen.HOME) ClusterDisplay.bubbleOpen.value = false }
                if (ClusterDisplay.bubbleOpen.value && screen == Screen.HOME) {
                    val none = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Box(Modifier.fillMaxSize().clickable(interactionSource = none, indication = null) { ClusterDisplay.bubbleOpen.value = false })
                    Box(Modifier.align(Alignment.TopEnd).padding(top = 90.dp, end = 80.dp)
                        .clickable(interactionSource = none, indication = null) {}) { ClusterBubble() }
                }
                NoticePill(notice, { notice = null }, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
                TyreAlert(tyreAlert, { dismissedTyres = dismissedTyres + tyreAlert.map { it.key } }, Modifier.align(Alignment.TopCenter).padding(top = if (notice != null) 76.dp else 16.dp))
            }
            // At night the HVAC Bar stays usable, just dimmed
            val night = NightMode.active.value
            // (on solid black, so the wallpaper doesn't show through the dimmed bar)
            Box(if (night) Modifier.background(Color.Black) else Modifier) {
                Box(if (night) Modifier.alpha(0.5f) else Modifier) {
                    BottomBar(climate, dockApps, screen, bar, style = barStyle, tempSide = tempSide, lights = barLights,
                        art = if (night) null else barArt, plain = if (night) Color.Black else null)
                }
            }
        }
    }
}

@Composable
private fun NoticePill(notice: String?, onDismiss: () -> Unit, modifier: Modifier) =
    AnimatedVisibility(visible = notice != null, modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        Txt(
            notice.orEmpty(),
            Sh.text(16.sp, FontWeight.SemiBold),
            Modifier.clip(RoundedCornerShape(22.dp)).background(Sh.raised).press(onDismiss, label = "Dismiss notice").padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }

/** The tyre warning: orange, at the top of every SharkOS screen until tapped. */
@Composable
internal fun TyreAlert(warnings: List<com.sharkos.launcher.car.TyreWarning>, onDismiss: () -> Unit, modifier: Modifier) =
    AnimatedVisibility(visible = warnings.isNotEmpty(), modifier = modifier, enter = fadeIn(), exit = fadeOut()) {
        androidx.compose.foundation.layout.Row(
            Modifier.clip(RoundedCornerShape(24.dp)).background(Sh.orange).press(onDismiss, label = "Dismiss tyre warning")
                .padding(start = 16.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            Glyph(ShIcons.info, Color.Black, 22.dp)
            Txt(warnings.joinToString("  ·  ") { it.message }, Sh.text(17.sp, FontWeight.Bold, Color.Black))
            Txt("Tap to dismiss", Sh.text(14.sp, FontWeight.Medium, Color.Black.copy(alpha = 0.6f)))
        }
    }

/**
 * Which "now playing" to show: whatever is playing, CarPlay first (it has no Android session, so the
 * car's CarPlay service is its only source); with nothing playing, the app session, then CarPlay.
 */
internal fun chooseTrack(carPlay: CarPlaySong?, session: Track?): Track? {
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
