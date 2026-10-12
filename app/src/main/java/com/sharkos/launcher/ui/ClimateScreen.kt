package com.sharkos.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.Airflow
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.Zone

class ClimateActions(
    val power: (Boolean) -> Unit,
    val temp: (Zone, Int) -> Unit,
    val fan: (Int) -> Unit,
    val airflow: (Airflow) -> Unit,
    val auto: (Boolean) -> Unit,
    val ac: (Boolean) -> Unit,
    val recirc: (Boolean) -> Unit,
    val frontDemist: (Boolean) -> Unit,
    val rearDemist: (Boolean) -> Unit,
    val dual: (Boolean) -> Unit,
    val seatHeat: (Zone, Int) -> Unit,
    val seatVent: (Zone, Int) -> Unit,
    val back: () -> Unit,
)

/** The full climate page: passenger on the left, cabin in the middle, driver on the right (RHD). */
@Composable
fun ClimateScreen(c: ClimateState, a: ClimateActions) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).press(a.back, label = "Back to Home"), verticalAlignment = Alignment.CenterVertically) {
                Glyph(ShIcons.back, Sh.blue, 22.dp)
                Txt("Home", Sh.text(19.sp, FontWeight.Medium, Sh.blue))
            }
            Txt("Climate", Sh.text(24.sp, FontWeight.Bold, spacing = (-0.3).sp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Txt(if (c.power) "On" else "Off", Sh.text(17.sp, FontWeight.Medium, Sh.dim))
                Gap(12.dp)
                Switch(c.power, a.power, "Climate power")
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SeatColumn("Passenger", Zone.PASSENGER, c, a, Modifier.width(260.dp).fillMaxHeight()) {
                ToggleTile("Sync to driver", !c.dual, { a.dual(!c.dual) }, Modifier.fillMaxWidth(), height = 56.dp)
            }
            CabinColumn(c, a, Modifier.weight(1f).fillMaxHeight())
            SeatColumn("Driver", Zone.DRIVER, c, a, Modifier.width(260.dp).fillMaxHeight(), alignEnd = true) {
                ToggleTile("Rear demist", c.rearDefrost, { a.rearDemist(!c.rearDefrost) }, Modifier.fillMaxWidth(), ShIcons.demistRear, height = 56.dp)
            }
        }
    }
}

@Composable
private fun SeatColumn(
    title: String,
    zone: Zone,
    c: ClimateState,
    a: ClimateActions,
    modifier: Modifier,
    alignEnd: Boolean = false,
    footer: @Composable () -> Unit,
) = Panel(modifier, padding = 22.dp) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start) { SectionLabel(title) }
    val seat = c.seat(zone)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(ShIcons.flame, Sh.orange, 18.dp); Gap(8.dp); Txt("Seat heat", Sh.text(16.sp, FontWeight.SemiBold))
        }
        Segmented(listOf("Off", "Low", "High"), seat.heat, { a.seatHeat(zone, it) }, Modifier.fillMaxWidth(), accent = Sh.orange, onAccent = Color(0xFF2B1700))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph(ShIcons.vent, Sh.cyan, 18.dp); Gap(8.dp); Txt("Seat cooling", Sh.text(16.sp, FontWeight.SemiBold))
        }
        Segmented(listOf("Off", "Low", "High"), seat.vent, { a.seatVent(zone, it) }, Modifier.fillMaxWidth(), accent = Sh.cyan, onAccent = Color(0xFF00293D))
    }
    Box(Modifier.weight(1f))
    footer()
}

@Composable
private fun CabinColumn(c: ClimateState, a: ClimateActions, modifier: Modifier) = Panel(modifier, padding = 26.dp) {
    Row(Modifier.fillMaxWidth()) {
        BigTemp("Passenger", c, Zone.PASSENGER, a, Modifier.weight(1f))
        BigTemp("Driver", c, Zone.DRIVER, a, Modifier.weight(1f))
    }
    // Fan: eight steps 0..7, tap a bar to jump straight to it
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Txt("Fan", Sh.text(15.sp, color = Sh.dim), Modifier.weight(1f))
            Txt(when { !c.power -> "Off"; c.auto -> "Auto · ${c.fan}"; else -> "${c.fan}" }, Sh.text(15.sp, FontWeight.SemiBold))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RoundButton(ShIcons.minus, "Fan down", { a.fan(c.fan - 1) }, size = 48.dp)
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (n in 1..ClimateState.MAX_FAN) {
                    val lit = c.power && n <= c.fan
                    val bg by animateColorAsState(if (lit) Sh.blue else Sh.line, tween(150), label = "fan$n")
                    Box(Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(10.dp)).background(bg).press({ a.fan(n) }, label = "Fan $n"))
                }
            }
            RoundButton(ShIcons.plus, "Fan up", { a.fan(c.fan + 1) }, size = 48.dp)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Txt("Airflow", Sh.text(15.sp, color = Sh.dim))
        Segmented(Airflow.entries.map { it.label }, c.airflow.ordinal, { a.airflow(Airflow.entries[it]) }, Modifier.fillMaxWidth(), greyFirst = false)
    }
    Box(Modifier.weight(1f))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleTile("Auto", c.auto, { a.auto(!c.auto) }, Modifier.weight(1f))
        ToggleTile("A/C", c.ac, { a.ac(!c.ac) }, Modifier.weight(1f), ShIcons.snow)
        ToggleTile("Recirc", c.recirc, { a.recirc(!c.recirc) }, Modifier.weight(1f), ShIcons.recirc)
        ToggleTile("Demist", c.frontDefrost, { a.frontDemist(!c.frontDefrost) }, Modifier.weight(1f), ShIcons.demistFront)
    }
}

@Composable
private fun BigTemp(label: String, c: ClimateState, zone: Zone, a: ClimateActions, modifier: Modifier) =
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt(label, Sh.text(15.sp, color = Sh.dim))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            RoundButton(ShIcons.minus, "$label cooler", { a.temp(zone, -1) }, size = 52.dp, tint = Sh.cyan)
            Txt(
                if (c.power) "${c.temp(zone).toInt()}°" else "—",
                Sh.text(72.sp, FontWeight.SemiBold, if (c.power) Sh.ink else Sh.faint, (-2.5).sp).copy(textAlign = TextAlign.Center),
                Modifier.width(120.dp),
            )
            RoundButton(ShIcons.plus, "$label warmer", { a.temp(zone, 1) }, size = 52.dp, tint = Sh.orange)
        }
    }
