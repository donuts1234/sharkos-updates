// Adapted from Shark Hub (https://github.com/Peacemaker105/SharkHub), Copyright (c) 2026 Chris Murray, MIT licence.
package com.sharkos.launcher.car

enum class Zone(val label: String) { DRIVER("Driver"), PASSENGER("Passenger") }

/** Where the air goes. Front demist is a separate toggle (it overrides this). */
enum class Airflow(val label: String) {
    FACE("Face"), FACE_FEET("Face + feet"), FEET("Feet"), FEET_SCREEN("Feet + screen")
}

/** Front-seat heating and ventilation, each 0 (off) to [ClimateState.MAX_SEAT_LEVEL]; one at a time. */
data class SeatClimate(val heat: Int = 0, val vent: Int = 0)

/**
 * The climate system: updated the moment you tap (so the screen responds even with no car), then
 * overwritten by what the car reports, so changes made anywhere else show up here too.
 */
data class ClimateState(
    val power: Boolean = true,
    val auto: Boolean = false,
    val ac: Boolean = true,
    val dual: Boolean = false,
    val driverTemp: Float = 22f,
    val passengerTemp: Float = 22f,
    val fan: Int = 3,
    val airflow: Airflow = Airflow.FACE,
    val recirc: Boolean = false,
    val frontDefrost: Boolean = false,
    val rearDefrost: Boolean = false,
    val driverSeat: SeatClimate = SeatClimate(),
    val passengerSeat: SeatClimate = SeatClimate(),
) {
    fun temp(zone: Zone): Float = if (zone == Zone.DRIVER) driverTemp else passengerTemp
    fun seat(zone: Zone): SeatClimate = if (zone == Zone.DRIVER) driverSeat else passengerSeat
    fun withSeat(zone: Zone, s: SeatClimate): ClimateState =
        if (zone == Zone.DRIVER) copy(driverSeat = s) else copy(passengerSeat = s)

    companion object {
        // Shark 6 (BYDAutoAcDevice): whole degrees, AC_TEMP_IN_CELSIUS_MIN..MAX; fan 0..7.
        const val MIN_TEMP = 17f
        const val MAX_TEMP = 33f
        const val TEMP_STEP = 1f
        const val MAX_FAN = 7
        const val MAX_SEAT_LEVEL = 2
    }
}

/** One-tap climate scenes for the Presets HVAC Bar. */
enum class Preset(val label: String) {
    COOL("Cool"), COMFORT("Comfort"), WARM("Warm"), DEMIST("Demist");

    /** Whether the cabin is currently set the way this preset leaves it (lights its button). */
    fun matches(c: ClimateState): Boolean = c.power && when (this) {
        COOL -> !c.auto && c.ac && c.driverTemp <= 18f
        COMFORT -> c.auto && !c.frontDefrost
        WARM -> !c.auto && c.driverTemp >= 26f && !c.frontDefrost
        DEMIST -> c.frontDefrost
    }
}

/** Outcome of one command sent to the car. */
data class CommandResult(val label: String, val result: Result<Unit>)
