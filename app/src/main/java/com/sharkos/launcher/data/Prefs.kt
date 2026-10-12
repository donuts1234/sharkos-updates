package com.sharkos.launcher.data

import android.content.Context

/** Everything SharkOS remembers: the dock and the inclinometer zero. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sharkos", Context.MODE_PRIVATE)

    /** Packages pinned to the bottom bar, in order. Null = never edited, use the defaults. */
    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("dock", v?.joinToString(",")).apply()

    /** Inclinometer zero for a display rotation: up (3 floats) then right (3 floats), sensor frame. */
    fun inclinoMount(rotation: Int): FloatArray? =
        sp.getString("inclino_mount_$rotation", null)
            ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 6 }?.toFloatArray()

    fun setInclinoMount(rotation: Int, v: FloatArray) =
        sp.edit().putString("inclino_mount_$rotation", v.joinToString(",")).apply()

    var askedCarPermission: Boolean
        get() = sp.getBoolean("asked_car_perm", false)
        set(v) = sp.edit().putBoolean("asked_car_perm", v).apply()
}
