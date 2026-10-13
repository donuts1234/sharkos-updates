// Adapted from Shark Hub (https://github.com/Peacemaker105/SharkHub), Copyright (c) 2026 Chris Murray, MIT licence.
package com.sharkos.launcher.car

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Whether the car API has been looked for yet, and what was found. */
enum class CarLink { SEARCHING, CONNECTED, OFFLINE }

/**
 * Climate commands and telemetry over the BYD API ([CarBackend]).
 *
 * Every command updates [climate] immediately (the UI never waits on the car and works on a bench),
 * then goes to the car on a background scope and reports on [commandResults]. Nothing here throws
 * at the UI: a missing device or method just becomes a failed result.
 *
 * Method names, argument order and codes are Shark Hub's mapping for the Shark 6 (SOC_260811_S),
 * read from BYD's own HVAC app. Driver temperature is verified on a car; the rest is wired the same
 * way but each control still needs a check on the car.
 */
class CarManager(private val appContext: Context) {

    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _link = MutableStateFlow(CarLink.SEARCHING)
    val link: StateFlow<CarLink> = _link.asStateFlow()

    @Volatile private var backend: CarBackend? = null
    val backendName: String get() = backend?.name ?: "none"

    private val pending = ConcurrentHashMap<String, Job>()
    private val _climate = MutableStateFlow(ClimateState())
    private val _results = MutableSharedFlow<CommandResult>(extraBufferCapacity = 16)

    val climate: StateFlow<ClimateState> = _climate.asStateFlow()
    val commandResults: SharedFlow<CommandResult> = _results.asSharedFlow()

    /** Desay's car service: battery / range / fuel without BYD's "dangerous" statistics permission. */
    private val ts = TsCarAdapter(appContext)

    init { connect(); ts.connect() }

    private fun connect() {
        io.launch {
            backend = CarBackend.discover(appContext)
            _link.value = if (backend != null) CarLink.CONNECTED else CarLink.OFFLINE
        }
    }

    /** Re-open BYD's devices, e.g. after the car-data permission is granted while SharkOS runs. */
    fun reconnect() = connect()

    // ---- Climate ----

    /**
     * Set a zone's temperature (whole degrees). Out of dual mode both zones follow the driver;
     * changing the passenger side switches dual on. Debounced: a run of taps sends only the last.
     */
    fun setZoneTemp(zone: Zone, celsius: Float) {
        val t = celsius.roundToInt().toFloat().coerceIn(ClimateState.MIN_TEMP, ClimateState.MAX_TEMP)
        command("temp-${zone.name}", "${zone.label} ${t.toInt()}°", debounceMs = 350, change = {
            when {
                zone == Zone.PASSENGER -> copy(passengerTemp = t, dual = true)
                dual -> copy(driverTemp = t)
                else -> copy(driverTemp = t, passengerTemp = t)
            }
        }) {
            val target = when {
                zone == Zone.PASSENGER -> Byd.ZONE_PASSENGER
                _climate.value.dual -> Byd.ZONE_DRIVER
                else -> Byd.ZONE_BOTH
            }
            if (zone == Zone.PASSENGER) {
                first(Call("setAcTemperatureControlMode", Byd.SOURCE_UI, Byd.ON, device = "ac"))
            }
            first(Call("setAcTemperature", target, t.roundToInt(), Byd.SOURCE_UI, Byd.UNIT_CELSIUS, device = "ac"))
        }
    }

    fun nudgeTemp(zone: Zone, delta: Int) = setZoneTemp(zone, _climate.value.temp(zone) + delta)

    /**
     * One temperature for the whole cabin (the HVAC Bar): both zones together, leaving dual mode if
     * it was on, so the bar can never leave the two sides at different settings. Debounced.
     */
    fun setCabinTemp(celsius: Float) {
        val t = celsius.roundToInt().toFloat().coerceIn(ClimateState.MIN_TEMP, ClimateState.MAX_TEMP)
        val wasDual = _climate.value.dual
        command("temp-cabin", "Cabin ${t.toInt()}°", debounceMs = 350,
            change = { copy(driverTemp = t, passengerTemp = t, dual = false) }) {
            if (wasDual) first(Call("setAcTemperatureControlMode", Byd.SOURCE_UI, Byd.OFF, device = "ac"))
            first(Call("setAcTemperature", Byd.ZONE_BOTH, t.roundToInt(), Byd.SOURCE_UI, Byd.UNIT_CELSIUS, device = "ac"))
        }
    }

    fun nudgeCabin(delta: Int) = setCabinTemp(_climate.value.driverTemp + delta)


    /** Fan arrows on the bar: 1..7 (off is the climate power switch, not the fan). */
    fun nudgeFan(delta: Int) = setFanSpeed((_climate.value.fan + delta).coerceIn(1, ClimateState.MAX_FAN))

    fun setDualZone(on: Boolean) = command("dual", if (on) "Dual zone on" else "Zones synced",
        change = { if (on) copy(dual = true) else copy(dual = false, passengerTemp = driverTemp) }) {
        first(Call("setAcTemperatureControlMode", Byd.SOURCE_UI, if (on) Byd.ON else Byd.OFF, device = "ac"))
    }

    /** Fan speed 0..7; a manual fan change leaves Auto, as the car itself does. */
    fun setFanSpeed(level: Int) {
        val l = level.coerceIn(0, ClimateState.MAX_FAN)
        command("fan", "Fan $l", change = { copy(fan = l, auto = false) }) {
            first(Call("setAcWindLevel", Byd.SOURCE_UI, l, device = "ac"))
        }
    }

    /** The compressor (cooling), not the whole system: that's [setClimatePower]. */
    fun setAcOn(on: Boolean) = command("ac", "A/C ${onOff(on)}", change = { copy(ac = on) }) {
        first(Call("setAcCompressorMode", Byd.SOURCE_UI, if (on) Byd.ON else Byd.OFF, device = "ac"))
    }

    fun setAuto(on: Boolean) = command("auto", "Auto ${onOff(on)}", change = { copy(auto = on) }) {
        first(Call("setAcControlMode", Byd.SOURCE_UI, if (on) Byd.CTRL_AUTO else Byd.CTRL_MANUAL, device = "ac"))
    }

    fun setRecirculation(on: Boolean) = command("recirc", if (on) "Recirculating" else "Fresh air",
        change = { copy(recirc = on) }) {
        first(Call("setAcCycleMode", Byd.SOURCE_UI, if (on) Byd.CYCLE_RECIRCULATE else Byd.CYCLE_FRESH, device = "ac"))
    }

    fun setAirflow(mode: Airflow) = command("airflow", "Airflow: ${mode.label}", change = { copy(airflow = mode) }) {
        first(Call("setAcWindMode", Byd.SOURCE_UI, Byd.windMode(mode), device = "ac"))
    }

    fun setFrontDefrost(on: Boolean) = command("defrost-front", "Front demist ${onOff(on)}",
        change = { copy(frontDefrost = on) }) {
        first(Call("setAcDefrostState", Byd.SOURCE_UI, Byd.DEFROST_FRONT, if (on) Byd.ON else Byd.OFF, device = "ac"))
    }

    fun setRearDefrost(on: Boolean) = command("defrost-rear", "Rear demist ${onOff(on)}",
        change = { copy(rearDefrost = on) }) {
        first(Call("setAcDefrostState", Byd.SOURCE_UI, Byd.DEFROST_REAR, if (on) Byd.ON else Byd.OFF, device = "ac"))
    }

    fun setClimatePower(on: Boolean) = command("power", "Climate ${onOff(on)}", change = { copy(power = on) }) {
        first(Call(if (on) "start" else "stop", Byd.SOURCE_UI, device = "ac"))
    }

    /** Seat heating 0..2; turning it on switches that seat's ventilation off. */
    fun setSeatHeat(zone: Zone, level: Int) {
        val l = level.coerceIn(0, ClimateState.MAX_SEAT_LEVEL)
        command("seat-heat-${zone.name}", "${zone.label} seat heat ${levelLabel(l)}",
            change = { withSeat(zone, SeatClimate(heat = l, vent = if (l > 0) 0 else seat(zone).vent)) }) {
            first(Call("setSeatHeatingState", Byd.seat(zone), Byd.seatLevel(l), device = "setting"))
        }
    }

    /** Seat ventilation 0..2; turning it on switches that seat's heating off. */
    fun setSeatVent(zone: Zone, level: Int) {
        val l = level.coerceIn(0, ClimateState.MAX_SEAT_LEVEL)
        command("seat-vent-${zone.name}", "${zone.label} seat cooling ${levelLabel(l)}",
            change = { withSeat(zone, SeatClimate(heat = if (l > 0) 0 else seat(zone).heat, vent = l)) }) {
            first(Call("setSeatVentilatingState", Byd.seat(zone), Byd.seatLevel(l), device = "setting"))
        }
    }

    /** One tap on a bottom-bar seat button: off → low → high → off. */
    fun cycleSeatHeat(zone: Zone) = setSeatHeat(zone, (_climate.value.seat(zone).heat + 1) % (ClimateState.MAX_SEAT_LEVEL + 1))
    fun cycleSeatVent(zone: Zone) = setSeatVent(zone, (_climate.value.seat(zone).vent + 1) % (ClimateState.MAX_SEAT_LEVEL + 1))

    /**
     * Read the car's climate state into [climate]. Skipped for a moment after a tap, so a read taken
     * before the car applied the command can't bounce the screen back. Blocking.
     */
    private fun refreshClimate() {
        val b = backend ?: return
        val startedAt = lastCommandAt
        if (System.currentTimeMillis() - startedAt < SETTLE_MS) return
        fun int(c: Call): Int? = if (b.has(c)) runCatching { b.invoke(c) as? Int }.getOrNull() else null
        val driverTemp = int(Call("getTemprature", Byd.ZONE_DRIVER, device = "ac"))?.takeIf { it in Byd.TEMP_RANGE }
        val passengerTemp = int(Call("getTemprature", Byd.ZONE_PASSENGER, device = "ac"))?.takeIf { it in Byd.TEMP_RANGE }
        val power = int(Call("getAcStartState", device = "ac"))
        val fan = int(Call("getAcWindLevel", device = "ac"))?.takeIf { it in 0..ClimateState.MAX_FAN }
        val airflow = int(Call("getAcWindMode", device = "ac"))?.let { Byd.airflow(it) }
        val recirc = int(Call("getAcCycleMode", device = "ac"))
        val control = int(Call("getAcControlMode", device = "ac"))
        val compressor = int(Call("getAcCompressorMode", device = "ac"))
        val split = int(Call("getAcTemperatureControlMode", device = "ac"))
        val front = int(Call("getAcDefrostState", Byd.DEFROST_FRONT, device = "ac"))
        val rear = int(Call("getAcDefrostState", Byd.DEFROST_REAR, device = "ac"))
        fun seatLevel(getter: String, zone: Zone) =
            int(Call(getter, Byd.seat(zone), device = "setting"))?.let { Byd.uiSeatLevel(it) }
        val seats = Zone.entries.associateWith { z ->
            seatLevel("getSeatHeatingState", z) to seatLevel("getSeatVentilatingState", z)
        }
        if (lastCommandAt != startedAt) return          // a tap landed mid-read: its value wins
        _climate.update { s ->
            var n = s.copy(
                power = power?.let { it == Byd.ON } ?: s.power,
                auto = control?.let { it == Byd.CTRL_AUTO } ?: s.auto,
                ac = compressor?.let { it == Byd.ON } ?: s.ac,
                dual = split?.let { it == Byd.ON } ?: s.dual,
                driverTemp = driverTemp?.toFloat() ?: s.driverTemp,
                passengerTemp = passengerTemp?.toFloat() ?: s.passengerTemp,
                fan = fan ?: s.fan,
                airflow = airflow ?: s.airflow,
                recirc = recirc?.let { it == Byd.CYCLE_RECIRCULATE } ?: s.recirc,
                frontDefrost = front?.let { it == Byd.ON } ?: s.frontDefrost,
                rearDefrost = rear?.let { it == Byd.ON } ?: s.rearDefrost,
            )
            for ((z, levels) in seats) {
                val old = n.seat(z)
                n = n.withSeat(z, SeatClimate(heat = levels.first ?: old.heat, vent = levels.second ?: old.vent))
            }
            n
        }
    }

    private fun command(
        key: String,
        label: String,
        debounceMs: Long = 0,
        change: ClimateState.() -> ClimateState,
        call: CarBackend.() -> Any?,
    ) {
        lastCommandAt = System.currentTimeMillis()
        _climate.update { it.change() }
        val job = io.launch {
            if (debounceMs > 0) delay(debounceMs)
            val result = runCatching {
                val b = backend ?: error("car not connected")
                checkResult(b.call())
            }
            lastCommandAt = System.currentTimeMillis()
            result.exceptionOrNull()?.let { Log.w(TAG, "$label failed: ${it.message}") }
            _results.emit(CommandResult(label, result.map { }))
        }
        pending.put(key, job)?.cancel()   // a newer tap on the same control supersedes the waiting one
    }

    /** BYD setters return a status code: negative means the car refused (busy, invalid value…). */
    private fun checkResult(r: Any?) {
        if (r is Int && r < 0) error("the car refused it (code $r)")
    }

    @Volatile private var lastCommandAt = 0L

    private fun onOff(on: Boolean) = if (on) "on" else "off"
    private fun levelLabel(level: Int) = if (level == 0) "off" else "$level"

    // ---- Telemetry ----

    /** One snapshot of what the car reports; each field is best-effort. Blocking. */
    private fun readTelemetry(): Telemetry {
        val b = backend
        if (b == null && !ts.connected) return Telemetry()
        fun d(c: Call): Double? = if (b != null && b.has(c)) (runCatching { b.invoke(c) }.getOrNull() as? Number)?.toDouble() else null
        /** Desay's "general" service first (no permission needed), then BYD's statistic device. */
        fun stat(getter: String): Double? = ts.number("general", getter) ?: d(Call(getter, device = "statistic"))
        fun tyre(corner: Corner): Tyre? {
            // getTyrePressureValueByType reads psi x10 on the Shark 6 (379 = 37.9 psi); fall back to kPa.
            val byType = d(Call("getTyrePressureValueByType", corner.code, device = "tyre"))
                ?: ts.number("body", "getTyrePressureValueByType" + TS_TYRE[corner.ordinal])
            val psi = when {
                byType != null && byType in 150.0..900.0 -> byType / 10.0
                else -> d(Call("getTyrePressureValue", corner.code, device = "tyre"))
                    ?.takeIf { it in 100.0..600.0 }?.times(KPA_TO_PSI)
            } ?: return null
            val state = d(Call("getTyrePressureState", corner.code, device = "tyre"))?.toInt() ?: 0
            return Tyre(psi, state)
        }
        return Telemetry(
            socPercent = stat("getElecPercentageValue")?.takeIf { it in 0.0..100.0 },
            evRangeKm = stat("getElecDrivingRangeValue")?.takeIf { it >= 0 },
            fuelPercent = stat("getFuelPercentageValue")?.takeIf { it in 0.0..100.0 },
            fuelRangeKm = stat("getFuelDrivingRangeValue")?.takeIf { it >= 0 },
            totalRangeKm = stat("getDrivingRangeAll")?.takeIf { it >= 0 },
            odometerKm = stat("getTotalMileageValue")?.takeIf { it > 0 },
            speedKph = d(Call("getCurrentSpeed", device = "speed"))?.takeIf { it in 0.0..300.0 },
            outsideTempC = d(Call("getTemprature", Byd.ZONE_OUTSIDE, device = "ac"))?.takeIf { it in -40.0..50.0 },
            tyres = Corner.entries.map { tyre(it) },
        )
    }

    /**
     * Telemetry plus a climate readback every [periodMs]. Collect with collectAsStateWithLifecycle
     * so polling stops whenever SharkOS isn't on screen: no work in the background.
     */
    fun telemetry(periodMs: Long = 2000): Flow<Telemetry> = flow {
        while (true) {
            refreshClimate()
            emit(readTelemetry())
            delay(periodMs)
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val TAG = "SharkOS/Car"
        const val SETTLE_MS = 2500L
        const val KPA_TO_PSI = 0.1450377
        /** Desay's per-corner tyre getter suffixes, in [Corner] order (LF, RF, LR, RR). */
        val TS_TYRE = listOf("Lf", "Rf", "Lb", "Rb")
    }
}

/**
 * BYD codes from BYDAutoAcDevice / BYDAutoSettingDevice on the Shark 6 (SOC_260811_S), via Shark Hub.
 * Mind the inversions: Auto is control mode 0, and seat levels count OFF=1, LOW=2, HIGH=3.
 */
private object Byd {
    const val SOURCE_UI = 0                 // AC_CTRL_SOURCE_UI_KEY
    const val UNIT_CELSIUS = 1              // AC_TEMPERATURE_UNIT_OC
    const val ON = 1
    const val OFF = 0
    const val ZONE_BOTH = 0                 // AC_TEMPERATURE_MAIN_DEPUTY
    const val ZONE_DRIVER = 1               // AC_TEMPERATURE_MAIN
    const val ZONE_PASSENGER = 2            // AC_TEMPERATURE_DEPUTY
    const val ZONE_OUTSIDE = 4              // AC_TEMPERATURE_OUT
    val TEMP_RANGE = 17..33
    const val CTRL_AUTO = 0
    const val CTRL_MANUAL = 1
    const val CYCLE_FRESH = 0
    const val CYCLE_RECIRCULATE = 1
    const val DEFROST_FRONT = 1
    const val DEFROST_REAR = 2

    fun windMode(a: Airflow): Int = when (a) {
        Airflow.FACE -> 1
        Airflow.FACE_FEET -> 2
        Airflow.FEET -> 3
        Airflow.FEET_SCREEN -> 4
    }

    fun airflow(code: Int): Airflow? = when (code) {
        1 -> Airflow.FACE
        2 -> Airflow.FACE_FEET
        3 -> Airflow.FEET
        4, 6 -> Airflow.FEET_SCREEN
        else -> null
    }

    fun seat(z: Zone): Int = if (z == Zone.DRIVER) 1 else 2
    fun seatLevel(ui: Int): Int = ui + 1
    fun uiSeatLevel(code: Int): Int? = if (code in 1..3) code - 1 else null
}

/**
 * Invoke the first call this firmware has. Stops at the first *existing* method, not the first
 * non-null result: a void setter returns null on success.
 */
private fun CarBackend.first(vararg calls: Call): Any? {
    val c = calls.firstOrNull { has(it) } ?: error("not available on this firmware: ${calls.first().describe()}")
    return invoke(c)
}

/** What the car reports; null = not available. */
data class Telemetry(
    val socPercent: Double? = null,
    val evRangeKm: Double? = null,
    val fuelPercent: Double? = null,
    val fuelRangeKm: Double? = null,
    val totalRangeKm: Double? = null,
    val odometerKm: Double? = null,
    val speedKph: Double? = null,
    val outsideTempC: Double? = null,
    val tyres: List<Tyre?> = List(Corner.entries.size) { null },
)

/** One tyre reading. [state] is TYRE_PRESSURE_STATE_*: 0 normal, 1 over, 2 under. */
data class Tyre(val psi: Double, val state: Int) {
    val low: Boolean get() = state == 2
    val high: Boolean get() = state == 1
}

/** Tyre positions with their BYDAutoTyreDevice area codes. */
enum class Corner(val label: String, val short: String, val code: Int) {
    LF("Front left", "FL", 1), RF("Front right", "FR", 2), LR("Rear left", "RL", 3), RR("Rear right", "RR", 4)
}
