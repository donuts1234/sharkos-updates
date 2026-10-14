package com.sharkos.launcher.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharkos.launcher.car.Drive
import com.sharkos.launcher.car.Lifetime
import com.sharkos.launcher.car.TripMeter
import com.sharkos.launcher.car.Trips
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Trips: this drive (counted by SharkOS since start-up), the car's Trip 1 and Trip 2 meters (as on
 * the driver display) and the lifetime figures. Opened from the Shark 6 card on Home. Rows the car
 * doesn't report are left out rather than shown as zero.
 */
@Composable
fun TripsScreen(t: Trips, onHome: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(Sh.card).press(onHome, label = "Back Home")
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(ShIcons.chevronLeft, Sh.blue, 20.dp)
                Txt("Home", Sh.text(16.sp, FontWeight.SemiBold, Sh.blue))
            }
            Txt("Trips", Sh.text(32.sp, FontWeight.Bold, spacing = (-0.6).sp))
        }
        DriveBanner(t.drive)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MeterCard("Trip 1", "Same as the driver display", t.trip1, Modifier.weight(1f).fillMaxHeight())
            MeterCard("Trip 2", "Same as the driver display", t.trip2, Modifier.weight(1f).fillMaxHeight())
            LifetimeCard(t.lifetime, Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun DriveBanner(d: Drive) = Row(
    Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF13233F), Sh.card)))
        .padding(horizontal = 26.dp, vertical = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(22.dp),
) {
    Column(Modifier.weight(1.3f)) {
        Txt("THIS DRIVE", Sh.text(13.sp, FontWeight.SemiBold, Color(0xFF8FB8FF), 0.4.sp))
        Big(one(d.km), "km", 44.sp)
        Txt(if (d.startedAt > 0) "Since start-up at ${SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(d.startedAt))}" else "Waiting for the car",
            Sh.text(14.sp, color = Sh.dim))
    }
    Stat("Driving time", hm(d.minutes), "", Modifier.weight(1f))
    Stat("Average speed", d.avgKph?.roundToInt()?.toString() ?: "—", "km/h", Modifier.weight(1f))
    Stat("Battery used", d.batteryPct?.roundToInt()?.toString() ?: "—", "%", Modifier.weight(1f),
        sub = d.batteryKwh?.let { "≈ ${"%.1f".format(it)} kWh" })
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Txt("Electric vs fuel", Sh.text(14.sp, FontWeight.Medium, Sh.dim))
        val ev = d.evShare
        if (ev == null) Txt("—", Sh.text(26.sp, FontWeight.Bold)) else {
            Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Sh.raised)) {
                if (ev > 0) Box(Modifier.weight(ev.toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Sh.green))
                if (ev < 1) Box(Modifier.weight((1 - ev).toFloat().coerceAtLeast(0.001f)).fillMaxHeight().background(Sh.orange))
            }
            Row {
                Txt("${(ev * 100).roundToInt()}% EV", Sh.text(14.sp, FontWeight.SemiBold, Sh.green))
                Txt("  ·  ", Sh.text(14.sp, color = Sh.dim))
                Txt("${100 - (ev * 100).roundToInt()}% fuel", Sh.text(14.sp, FontWeight.SemiBold, Sh.orange))
            }
        }
    }
}

@Composable
private fun MeterCard(title: String, tag: String, m: TripMeter, modifier: Modifier) = Panel(modifier, padding = 22.dp) {
    Column {
        SectionLabel(title)
        Txt(tag, Sh.text(13.sp, color = Sh.faint))
    }
    Big(m.km?.let { one(it) } ?: "—", "km", 38.sp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Top) {
        m.minutes?.let { Line("Driving time", hm(it)) }
        m.avgKph?.let { Line("Average speed", "${it.roundToInt()} km/h") }
        m.kwhPer100?.let { Line("Electricity", "${"%.1f".format(it)} kWh/100 km") }
        m.litresPer100?.let { Line("Fuel", "${"%.1f".format(it)} L/100 km") }
    }
}

@Composable
private fun LifetimeCard(l: Lifetime, modifier: Modifier) = Panel(modifier, padding = 22.dp) {
    Column {
        SectionLabel("Since new")
        Txt("The car's lifetime figures", Sh.text(13.sp, color = Sh.faint))
    }
    Big(l.km?.let { "%,d".format(it.roundToInt()) } ?: "—", "km", 38.sp)
    Column(Modifier.weight(1f)) {
        l.evKm?.let { Line("Electric km", "%,d".format(it.roundToInt())) }
        l.fuelKm?.let { Line("Fuel km", "%,d".format(it.roundToInt())) }
        l.kwhPer100?.let { Line("Electricity", "${"%.1f".format(it)} kWh/100 km") }
        l.litresPer100?.let { Line("Fuel", "${"%.1f".format(it)} L/100 km") }
    }
}

@Composable
private fun Big(value: String, unit: String, size: androidx.compose.ui.unit.TextUnit) = Row(verticalAlignment = Alignment.Bottom) {
    Txt(value, Sh.text(size, FontWeight.ExtraBold, spacing = (-1).sp))
    Txt(" $unit", Sh.text(18.sp, FontWeight.Medium, Sh.dim), Modifier.padding(bottom = 6.dp))
}

@Composable
private fun Stat(label: String, value: String, unit: String, modifier: Modifier, sub: String? = null) = Column(modifier) {
    Txt(label, Sh.text(14.sp, FontWeight.Medium, Sh.dim))
    Row(verticalAlignment = Alignment.Bottom) {
        Txt(value, Sh.text(28.sp, FontWeight.Bold, spacing = (-0.5).sp))
        if (unit.isNotEmpty()) Txt(" $unit", Sh.text(15.sp, FontWeight.Medium, Sh.dim), Modifier.padding(bottom = 4.dp))
    }
    sub?.let { Txt(it, Sh.text(13.sp, color = Sh.faint)) }
}

@Composable
private fun Line(label: String, value: String) = Column {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Sh.line))
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Txt(label, Sh.text(16.sp, color = Sh.dim), Modifier.weight(1f))
        Txt(value, Sh.text(16.sp, FontWeight.SemiBold))
    }
}

private fun one(km: Double) = if (km >= 1000) "%,d".format(km.roundToInt()) else "%.1f".format(km)
private fun hm(minutes: Int) = "%d:%02d".format(minutes / 60, minutes % 60)
