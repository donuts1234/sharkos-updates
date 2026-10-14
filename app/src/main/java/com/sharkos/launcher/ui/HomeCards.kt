package com.sharkos.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.sharkos.launcher.car.ClimateState
import com.sharkos.launcher.car.Drive
import kotlin.math.roundToInt

/** The cards Home can show, three at a time (or two). Off-road stays a page, reached from Tyres. */
enum class HomeCard(val label: String) {
    NOW_PLAYING("Now playing"), SHARK6("Shark 6"), TYRES("Tyres"), DRIVE("This drive"),
    CLIMATE("Climate"), LIGHTS("Lights"), SHORTCUTS("Shortcuts");

    companion object {
        val DEFAULT = listOf(NOW_PLAYING, SHARK6, TYRES)
        fun parse(names: List<String>?): List<HomeCard> =
            names?.mapNotNull { n -> entries.firstOrNull { it.name == n } }?.distinct()?.takeIf { it.size in 2..3 } ?: DEFAULT
    }
}

/** This drive, small: distance, time, battery used and the EV/fuel split. Tap for Trips. */
@Composable
internal fun DriveCard(d: Drive?, a: HomeActions, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(Color(0xFF13233F), Sh.card)))
        .press(a.openTrips, label = "Open trips").padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    SectionLabel("This drive", color = Color(0xFF8FB8FF))
    Row(verticalAlignment = Alignment.Bottom) {
        Txt(d?.let { "%.1f".format(it.km) } ?: "—", Sh.text(48.sp, FontWeight.ExtraBold, spacing = (-1.5).sp))
        Txt(" km", Sh.text(20.sp, FontWeight.Medium, Sh.dim), Modifier.padding(bottom = 8.dp))
    }
    Box(Modifier.weight(1f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniStat("Time", d?.let { "%d:%02d".format(it.minutes / 60, it.minutes % 60) } ?: "—", Modifier.weight(1f))
        MiniStat("Battery used", d?.batteryPct?.let { "${it.roundToInt()}%" } ?: "—", Modifier.weight(1f))
    }
    d?.evShare?.let { ev ->
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Sh.raised)) {
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

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) = Column(
    modifier.clip(RoundedCornerShape(18.dp)).background(Sh.raised).padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
) {
    Txt(label, Sh.text(13.sp, color = Sh.dim))
    Txt(value, Sh.text(24.sp, FontWeight.Bold))
}

/** Climate, small: both temperatures and three quick switches. Tap the temperatures for the Climate page. */
@Composable
internal fun ClimateCard(c: ClimateState, a: HomeActions, modifier: Modifier) = Panel(modifier) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SectionLabel("Climate", Modifier.weight(1f))
        Txt(when { !c.power -> "Off"; c.auto -> "On · Auto"; else -> "On · Fan ${c.fan}" },
            Sh.text(14.sp, FontWeight.SemiBold, if (c.power) Sh.green else Sh.faint))
    }
    Row(Modifier.weight(1f).fillMaxWidth().press(a.openClimate, label = "Open climate"), verticalAlignment = Alignment.CenterVertically) {
        for ((i, z) in listOf(com.sharkos.launcher.car.Zone.PASSENGER, com.sharkos.launcher.car.Zone.DRIVER).withIndex()) {
            if (i == 1) Box(Modifier.width(1.dp).height(64.dp).background(Sh.line))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(z.label, Sh.text(14.sp, color = Sh.dim))
                Txt(if (c.power) "${c.temp(z).toInt()}°" else "—", Sh.text(52.sp, FontWeight.Bold, spacing = (-2).sp))
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickToggle("A/C", c.ac, a.toggleAc, Modifier.weight(1f))
        QuickToggle("Recirc", c.recirc, a.toggleRecirc, Modifier.weight(1f))
        QuickToggle("Demist", c.frontDefrost, a.toggleDemist, Modifier.weight(1f), Sh.orange)
    }
}

@Composable
private fun QuickToggle(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier, color: Color = Sh.blue) = Box(
    modifier.height(52.dp).clip(RoundedCornerShape(16.dp)).background(if (on) color else Sh.raised).press(onClick, label = label),
    contentAlignment = Alignment.Center,
) { Txt(label, Sh.text(15.sp, FontWeight.SemiBold, if (on && color == Sh.orange) Color.Black else Sh.ink)) }

/** Ambient lighting, small: on/off, brightness, colour and swatches; both zones together. */
@Composable
internal fun LightsCard(l: BarLights?, note: String?, a: HomeActions, modifier: Modifier) {
    val tint = l?.takeIf { it.on }?.let { lightColor(it.hue) } ?: Sh.card
    Column(
        modifier.clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Sh.card, Sh.card, tint.copy(alpha = if (l?.on == true) 0.3f else 0f))))
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Lights", Modifier.weight(1f).press(a.openLights, label = "Ambient lighting settings"))
            if (l != null) Switch(l.on, a.lightsOn, "Lights on")
        }
        if (l == null) {
            Txt("Set up Ambient lighting in Settings.", Sh.text(16.sp, color = Sh.dim), maxLines = 2)
            PillButton("Set up", a.openLights)
            return@Column
        }
        Txt(if (!l.connected) "Connecting…" else if (l.on) "${l.brightness}%" else "Off", Sh.text(26.sp, FontWeight.Bold))
        GradientSlider(l.brightness / 100f, Brush.horizontalGradient(listOf(Color(0xFF2C2C2E), Color.White)), "Lights brightness") {
            a.lightsBrightness((it * 100).toInt().coerceAtLeast(1))
        }
        GradientSlider(if (l.hue < 0) 0f else l.hue, Brush.horizontalGradient(RAINBOW), "Lights colour", showKnob = l.hue >= 0) { a.lightsHue(it) }
        Box(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (h in listOf(PRESETS[0], PRESETS[1], PRESETS[2], PRESETS[4], PRESETS[5], PRESETS[6], PRESETS[7])) {
                val on = if (h < 0f) l.hue < 0f else l.hue >= 0f && kotlin.math.abs(l.hue - h) < 0.01f
                Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(swatch(h))
                    .then(if (on) Modifier.border(3.dp, Color.White, RoundedCornerShape(15.dp)) else Modifier)
                    .press({ a.lightsHue(h) }, label = "Lights ${presetName(h)}"))
            }
        }
        note?.let { Txt(it, Sh.text(13.sp, color = Sh.dim)) }
    }
}

/** Four app tiles of the driver's choosing; an empty one picks an app, holding one empties it. */
@Composable
internal fun ShortcutsCard(apps: List<DockApp?>, a: HomeActions, modifier: Modifier) = Panel(modifier) {
    SectionLabel("Shortcuts")
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (row in 0 until 2) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (col in 0 until 2) {
                val i = row * 2 + col
                val app = apps.getOrNull(i)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(22.dp)).background(Sh.raised)
                        .press(
                            onClick = { if (app != null) a.launch(app.pkg) else a.addShortcut(i) },
                            onLongClick = if (app != null) ({ a.removeShortcut(i) }) else null,
                            label = app?.label ?: "Add a shortcut",
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                ) {
                    if (app != null) {
                        AppIcon(app.pkg, app.label, 52.dp)
                        Txt(app.label, Sh.text(15.sp, FontWeight.SemiBold), Modifier.padding(horizontal = 8.dp))
                    } else {
                        Glyph(ShIcons.plus, Sh.faint, 30.dp)
                    }
                }
            }
        }
    }
}

