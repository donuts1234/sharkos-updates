package com.sharkos.launcher.car

/** A tyre outside the driver's range, or one the car itself flags. */
data class TyreWarning(val corner: Corner, val psi: Double, val low: Boolean) {
    /** Stable id, so a dismissed warning stays dismissed until the tyre recovers. */
    val key: String get() = "${corner.name}-${if (low) "low" else "high"}"
    val message: String get() = "${corner.label} tyre ${if (low) "low" else "high"}: ${"%.0f".format(psi)} psi"
}

/**
 * Tyre alerts: below [TyreLimits.lowPsi] or above [TyreLimits.highPsi], or flagged by the car's own
 * tyre monitor. Readings under 5 psi are a sensor that hasn't reported yet, not a flat.
 */
data class TyreLimits(val lowPsi: Int = 30, val highPsi: Int = 44) {
    fun check(tyres: List<Tyre?>): List<TyreWarning> = Corner.entries.mapNotNull { c ->
        val t = tyres.getOrNull(c.ordinal) ?: return@mapNotNull null
        if (t.psi < 5) return@mapNotNull null
        when {
            t.psi < lowPsi || t.low -> TyreWarning(c, t.psi, low = true)
            t.psi > highPsi || t.high -> TyreWarning(c, t.psi, low = false)
            else -> null
        }
    }
}
