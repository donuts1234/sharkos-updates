package com.sharkos.launcher.data

import android.content.Context

/** Everything SharkOS remembers: the dock and the inclinometer zero. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("sharkos", Context.MODE_PRIVATE)

    /** Packages pinned to the bottom bar, in order. Null = never edited, use the defaults. */
    var dock: List<String>?
        get() = sp.getString("dock", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("dock", v?.joinToString(",")).apply()

    /** Home screen: which cards, in order (HomeCard names), and the Shortcuts card's apps. */
    var homeCards: List<String>?
        get() = sp.getString("home_cards", null)?.split(',')?.filter { it.isNotBlank() }
        set(v) = sp.edit().putString("home_cards", v?.joinToString(",")).apply()
    var shortcuts: List<String>?
        get() = sp.getString("shortcuts", null)?.split(',')
        set(v) = sp.edit().putString("shortcuts", v?.joinToString(",")).apply()

    /** Inclinometer zero for a display rotation: up (3 floats) then right (3 floats), sensor frame. */
    fun inclinoMount(rotation: Int): FloatArray? =
        sp.getString("inclino_mount_$rotation", null)
            ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 6 }?.toFloatArray()

    fun setInclinoMount(rotation: Int, v: FloatArray) =
        sp.edit().putString("inclino_mount_$rotation", v.joinToString(",")).apply()

    /** CarPlay drawn by SharkOS (moved clear of the screen's top edge) rather than by BYD's screen. */
    var carPlayInSharkOs: Boolean
        get() = sp.getBoolean("carplay_in_sharkos", false)
        set(v) = sp.edit().putBoolean("carplay_in_sharkos", v).apply()

    /** Bring SharkOS's bar back whenever BYD's CarPlay is on screen (after reversing and so on). */
    var carPlayAlwaysBar: Boolean
        get() = sp.getBoolean("carplay_always_bar", true)
        set(v) = sp.edit().putBoolean("carplay_always_bar", v).apply()

    /** How far SharkOS draws CarPlay below the screen's top edge, in screen pixels. */
    var carPlayTopGap: Int
        get() = sp.getInt("carplay_top_gap", 12)
        set(v) = sp.edit().putInt("carplay_top_gap", v).apply()

    /** Screen pixels hidden under the bezel along the top: SharkOS starts below them. */
    var topEdge: Int
        get() = sp.getInt("top_edge", 4)
        set(v) = sp.edit().putInt("top_edge", v).apply()

    /** Night mode's main-screen look: GLOW (the glow line) or CLOCK (big clock). */
    var nightStyle: String
        get() = sp.getString("night_style", "GLOW") ?: "GLOW"
        set(v) = sp.edit().putString("night_style", v).apply()

    /** SharkOS on the instrument cluster: on at start-up, its style, and the cluster map slot it uses. */
    var clusterOn: Boolean
        get() = sp.getBoolean("cluster_on", true)
        set(v) = sp.edit().putBoolean("cluster_on", v).apply()
    var clusterStyle: String
        get() = sp.getString("cluster_style", "FADE") ?: "FADE"
        set(v) = sp.edit().putString("cluster_style", v).apply()
    var clusterSlot: Int
        get() = sp.getInt("cluster_slot", 1)
        set(v) = sp.edit().putInt("cluster_slot", v).apply()

    /** Tyre alerts and their limits (psi). */
    var tyreAlerts: Boolean
        get() = sp.getBoolean("tyre_alerts", true)
        set(v) = sp.edit().putBoolean("tyre_alerts", v).apply()
    var tyreLow: Int
        get() = sp.getInt("tyre_low", 30)
        set(v) = sp.edit().putInt("tyre_low", v).apply()
    var tyreHigh: Int
        get() = sp.getInt("tyre_high", 44)
        set(v) = sp.edit().putInt("tyre_high", v).apply()

    /** Open SharkOS automatically when the car starts. On by default. */
    var autoLaunch: Boolean
        get() = sp.getBoolean("auto_launch", true)
        set(v) = sp.edit().putBoolean("auto_launch", v).apply()

    /** Which HVAC Bar layout, by BarStyle name (so adding layouts never reshuffles a saved choice). */
    var barStyle: String?
        get() = sp.getString("bar_style_name", null)
        set(v) = sp.edit().putString("bar_style_name", v).apply()

    /** Background and corner style, by enum name. */
    /** Which side the HVAC Bar's temperature sits on, by TempSide name. */
    var tempSide: String?
        get() = sp.getString("temp_side", null)
        set(v) = sp.edit().putString("temp_side", v).apply()

    var wallpaper: String?
        get() = sp.getString("wallpaper", null)
        set(v) = sp.edit().putString("wallpaper", v).apply()

    var corners: String?
        get() = sp.getString("corners", null)
        set(v) = sp.edit().putString("corners", v).apply()

    var askedCarPermission: Boolean
        get() = sp.getBoolean("asked_car_perm", false)
        set(v) = sp.edit().putBoolean("asked_car_perm", v).apply()
}
