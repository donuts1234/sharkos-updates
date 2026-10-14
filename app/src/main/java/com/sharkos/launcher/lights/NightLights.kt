package com.sharkos.launcher.lights

import android.content.Context
import android.util.Log
import com.sharkos.launcher.SharkOsApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Night mode for the ambient lighting: when the headlights come on the lights go to [nightPercent]
 * (20% to start with), when they go off back to [dayPercent] (80%). Only on the change itself, so a
 * brightness set by hand holds until the headlights next switch. Checks every few seconds for the
 * whole process, so it works with CarPlay or any app in front.
 */
object NightLights {
    private const val TAG = "SharkOS/Lights"
    private fun prefs(c: Context) = c.getSharedPreferences("sharkos_lights", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(true)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()
    private val _night = MutableStateFlow(20)
    val nightPercent: StateFlow<Int> = _night.asStateFlow()
    private val _day = MutableStateFlow(80)
    val dayPercent: StateFlow<Int> = _day.asStateFlow()
    private val _headlights = MutableStateFlow<Boolean?>(null)
    /** What the car last said: on, off, or null when it hasn't answered. */
    val headlights: StateFlow<Boolean?> = _headlights.asStateFlow()

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun start(ctx: Context) {
        if (app != null) return
        val c = ctx.applicationContext
        app = c
        runCatching {
            val p = prefs(c)
            _enabled.value = p.getBoolean("night_dim", true)
            _night.value = p.getInt("night_pct", 20)
            _day.value = p.getInt("day_pct", 80)
        }
        val car = (c as? SharkOsApp)?.car ?: return
        scope.launch {
            while (true) {
                val on = withContext(Dispatchers.IO) { runCatching { car.headlightsOn() }.getOrNull() }
                if (on != null && on != _headlights.value) {
                    _headlights.value = on
                    Log.w(TAG, "headlights ${if (on) "on" else "off"}")
                    apply(c, on)
                }
                delay(3000)
            }
        }
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        _enabled.value = on
        prefs(ctx).edit().putBoolean("night_dim", on).apply()
        _headlights.value?.let { apply(ctx.applicationContext, it) }
    }

    fun setPercents(ctx: Context, night: Int, day: Int) {
        _night.value = night.coerceIn(1, 100)
        _day.value = day.coerceIn(1, 100)
        prefs(ctx).edit().putInt("night_pct", _night.value).putInt("day_pct", _day.value).apply()
        _headlights.value?.let { apply(ctx.applicationContext, it) }
    }

    private fun apply(c: Context, headlightsOn: Boolean) {
        if (!_enabled.value || Lights.address(c) == null) return
        Lights.setBrightness(c, null, if (headlightsOn) _night.value else _day.value)
    }
}
