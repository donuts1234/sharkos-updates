package com.sharkos.launcher.car

import android.content.Context

/** One of the car's trip meters. Fields the car doesn't report stay null and aren't shown. */
data class TripMeter(
    val km: Double? = null,
    val minutes: Int? = null,
    val avgKph: Double? = null,
    val kwhPer100: Double? = null,
    val litresPer100: Double? = null,
)

/** This drive, counted by SharkOS from start-up. */
data class Drive(
    val km: Double = 0.0,
    val startedAt: Long = 0L,
    val minutes: Int = 0,
    val avgKph: Double? = null,
    val batteryPct: Double? = null,
    val batteryKwh: Double? = null,
    /** Share of this drive's km done on electricity (0–1), when the car's EV/fuel counters move. */
    val evShare: Double? = null,
)

data class Lifetime(
    val km: Double? = null,
    val evKm: Double? = null,
    val fuelKm: Double? = null,
    val kwhPer100: Double? = null,
    val litresPer100: Double? = null,
)

data class Trips(
    val drive: Drive = Drive(),
    val trip1: TripMeter = TripMeter(),
    val trip2: TripMeter = TripMeter(),
    val lifetime: Lifetime = Lifetime(),
)

/** What the car's counters read at one moment: the raw material for [DriveTracker]. */
data class Counters(
    val odometerKm: Double?,
    val evKm: Double?,
    val fuelKm: Double?,
    val socPercent: Double?,
    /** The pack's usable size in kWh, if the car says. */
    val packKwh: Double?,
    /** The car's own current-trip time in minutes, if it reports one. */
    val tripMinutes: Int?,
)

/**
 * "This drive": the car's counters at start-up, compared with now. Start-up is marked by the boot
 * broadcast (the head unit starts with the car); the first reading after it becomes the baseline.
 * Snapshots rather than polling, so it stays right while CarPlay or another app is in front.
 */
object DriveTracker {
    private fun prefs(c: Context) = c.getSharedPreferences("sharkos_drive", Context.MODE_PRIVATE)
    /** The Shark 6's usable battery, used when the car doesn't report its pack size. */
    private const val SHARK6_PACK_KWH = 29.58

    /** The car just started: the next reading begins a new drive. */
    fun markNewDrive(ctx: Context) = runCatching { prefs(ctx).edit().putBoolean("new", true).apply() }

    fun update(ctx: Context, now: Counters): Drive {
        val p = prefs(ctx)
        val odo = now.odometerKm ?: return Drive()
        if (p.getBoolean("new", true) || !p.contains("odo")) {
            p.edit().putBoolean("new", false)
                .putFloat("odo", odo.toFloat())
                .putFloat("ev", (now.evKm ?: -1.0).toFloat())
                .putFloat("fuel", (now.fuelKm ?: -1.0).toFloat())
                .putFloat("soc", (now.socPercent ?: -1.0).toFloat())
                .putLong("at", System.currentTimeMillis())
                .apply()
        }
        val km = (odo - p.getFloat("odo", odo.toFloat())).coerceAtLeast(0.0)
        val at = p.getLong("at", System.currentTimeMillis())
        val minutes = now.tripMinutes?.takeIf { it > 0 } ?: ((System.currentTimeMillis() - at) / 60_000).toInt()
        val socStart = p.getFloat("soc", -1f).toDouble()
        val used = now.socPercent?.let { s -> if (socStart >= 0) (socStart - s).coerceAtLeast(0.0) else null }
        val pack = now.packKwh?.takeIf { it in 10.0..150.0 } ?: SHARK6_PACK_KWH
        val evStart = p.getFloat("ev", -1f).toDouble()
        val fuelStart = p.getFloat("fuel", -1f).toDouble()
        val evDelta = now.evKm?.let { if (evStart >= 0) it - evStart else null }
        val fuelDelta = now.fuelKm?.let { if (fuelStart >= 0) it - fuelStart else null }
        val share = if (evDelta != null && fuelDelta != null && evDelta + fuelDelta >= 0.5) evDelta / (evDelta + fuelDelta) else null
        return Drive(
            km = km,
            startedAt = at,
            minutes = minutes,
            avgKph = if (minutes >= 1 && km >= 0.1) km / (minutes / 60.0) else null,
            batteryPct = used,
            batteryKwh = used?.let { it / 100.0 * pack },
            evShare = share?.coerceIn(0.0, 1.0),
        )
    }
}
