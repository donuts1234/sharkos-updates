package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.Zone

data class DockApp(val pkg: String, val label: String)

/** What the bottom bar can do; wired to the car and the launcher in [Root]. */
class BarActions(
    val nudgeTemp: (Zone, Int) -> Unit,
    val cycleHeat: (Zone) -> Unit,
    val cycleVent: (Zone) -> Unit,
    val toggleAc: () -> Unit,
    val home: () -> Unit,
    val apps: () -> Unit,
    val climate: () -> Unit,
    val launch: (String) -> Unit,
    /** Tap on an empty + spot: pick an app for it. */
    val addApp: () -> Unit,
    /** Hold a pinned app: take it off the bar. */
    val removeApp: (DockApp) -> Unit,
)

/**
 * The bar that stays on every SharkOS screen, standing in for BYD's own climate bar:
 * passenger seat + temperature on the left, the dock in the middle, driver on the right
 * (the Shark 6 here is right-hand drive).
 */
@Composable
fun BottomBar(climate: ClimateState, dock: List<DockApp>, screen: Screen, a: BarActions, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(88.dp).background(Sh.bar).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Passenger
        SeatButton(ShIcons.flame, climate.passengerSeat.heat, Sh.orange, "Passenger seat heat") { a.cycleHeat(Zone.PASSENGER) }
        Gap(8.dp)
        SeatButton(ShIcons.vent, climate.passengerSeat.vent, Sh.cyan, "Passenger seat cooling") { a.cycleVent(Zone.PASSENGER) }
        Gap(12.dp)
        TempControl(climate, Zone.PASSENGER, a)

        // Dock
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarIcon(ShIcons.home, "Home", screen == Screen.HOME, onClick = a.home)
            for (app in dock) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp))
                        .press({ a.launch(app.pkg) }, onLongClick = { a.removeApp(app) }, label = app.label),
                    contentAlignment = Alignment.Center,
                ) { AppIcon(app.pkg, app.label, 46.dp) }
            }
            repeat((DOCK_SLOTS - dock.size).coerceAtLeast(0)) { AddSlot(a.addApp) }
            BarIcon(ShIcons.grid, "All apps", screen == Screen.APPS, onClick = a.apps)
            Box(Modifier.width(1.dp).height(36.dp).background(Sh.line))
            BarIcon(ShIcons.snow, "A/C", climate.ac && climate.power, onClick = a.toggleAc, onColor = Sh.blue)
            FanButton(climate, screen == Screen.CLIMATE, a.climate)
        }

        // Driver
        TempControl(climate, Zone.DRIVER, a)
        Gap(12.dp)
        SeatButton(ShIcons.vent, climate.driverSeat.vent, Sh.cyan, "Driver seat cooling") { a.cycleVent(Zone.DRIVER) }
        Gap(8.dp)
        SeatButton(ShIcons.flame, climate.driverSeat.heat, Sh.orange, "Driver seat heat") { a.cycleHeat(Zone.DRIVER) }
    }
}

const val DOCK_SLOTS = 5

/** An empty spot on the bar: a dashed-looking + that opens the app picker. */
@Composable
private fun AddSlot(onClick: () -> Unit) = Box(
    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).press(onClick, label = "Add an app to the bar"),
    contentAlignment = Alignment.Center,
) {
    Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Sh.card), contentAlignment = Alignment.Center) {
        Glyph(ShIcons.plus, Sh.faint, 20.dp)
    }
}

/** ‹ 22° › for one zone. */
@Composable
private fun TempControl(c: ClimateState, zone: Zone, a: BarActions) {
    val t = c.temp(zone)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp, 56.dp).press({ a.nudgeTemp(zone, -1) }, label = "${zone.label} cooler"), contentAlignment = Alignment.Center) {
            Glyph(ShIcons.chevronLeft, Sh.cyan, 22.dp)
        }
        Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(if (c.power) "${t.toInt()}°" else "Off", Sh.text(28.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint, (-0.5).sp))
            Txt(zone.label, Sh.text(11.sp, FontWeight.Medium, Sh.dim))
        }
        Box(Modifier.size(44.dp, 56.dp).press({ a.nudgeTemp(zone, 1) }, label = "${zone.label} warmer"), contentAlignment = Alignment.Center) {
            Glyph(ShIcons.chevronRight, Sh.orange, 22.dp)
        }
    }
}

/**
 * One tap steps a seat Off → Low → High → Off. The icon lights in its colour and two small bars
 * show the level, so it reads at a glance without words.
 */
@Composable
private fun SeatButton(icon: ImageVector, level: Int, color: Color, label: String, onClick: () -> Unit) {
    val on = level > 0
    val bg by animateColorAsState(if (on) color.copy(alpha = 0.18f) else Sh.raised, tween(180), label = "seat")
    Column(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg)
            .press(onClick, label = "$label ${listOf("off", "low", "high")[level.coerceIn(0, 2)]}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(icon, if (on) color else Sh.dim, 22.dp)
        Gap(5.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for (i in 1..2) Box(Modifier.size(10.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(if (level >= i) color else Sh.line))
        }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, onColor: Color = Sh.ink) {
    val bg by animateColorAsState(if (active) Sh.raised else Color.Transparent, tween(180), label = "bar")
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = label),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, if (active) onColor else Sh.dim, 26.dp) }
}

/** Opens the full climate page; shows the fan speed (or AUTO) so it doubles as a status. */
@Composable
private fun FanButton(c: ClimateState, active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Sh.raised else Color.Transparent, tween(180), label = "fanbg")
    Column(
        Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(bg).press(onClick, label = "Climate"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Glyph(ShIcons.fan, if (c.power) Sh.ink else Sh.dim, 22.dp)
        Txt(
            when { !c.power -> "OFF"; c.auto -> "AUTO"; else -> "${c.fan}" },
            Sh.text(11.sp, FontWeight.Bold, if (c.power) Sh.ink else Sh.faint, 0.3.sp).copy(textAlign = TextAlign.Center),
        )
    }
}
