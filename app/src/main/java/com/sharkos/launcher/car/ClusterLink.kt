package com.sharkos.launcher.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.provider.Settings
import android.util.Log
import android.view.Display

/**
 * The driver display (instrument cluster), as far as Android sees it:
 *
 *  - It's Android display 1 (1920 × 720 on the Shark 6). Whatever is drawn there shows in the
 *    cluster's map area once the cluster is told to show it; that's how CarPlay puts its map there
 *    (a Presentation window on that display).
 *  - The cluster is told through Desay's cluster service (com.ts.appservice.cluster), the same
 *    commands BYD's instrument device sends: take the map "focus", say navigation has started,
 *    and ask for the small or full map view. The service is exported with no permission check,
 *    so SharkOS talks to it directly over Binder (like [CarPlayLink]).
 *
 * SharkOS uses map type 3 (the Android Auto slot, unused with an iPhone), so CarPlay's own type 2
 * keeps working: map focus is a stack, and giving ours up hands it straight back to CarPlay.
 */
object ClusterLink {
    private const val TAG = "SharkOS/Cluster"
    private val COMPONENT = ComponentName("com.ts.appservice.cluster", "com.ts.appservice.cluster.service.ClusterCoreService")
    private const val DESCRIPTOR = "com.ts.lib.cluster.common.IClusterCommService"
    private const val TX_SEND_COMMAND = 1
    private const val CMD_NAVI = 16

    const val MAP_TYPE_SHARKOS = 3          // BYDAutoInstrumentDevice.MAP_TYPE_AUTO
    const val STATE_CLOSE = 1               // NAV_CLOSE
    const val STATE_SMALL = 5               // NAV_SMALL
    const val STATE_FULL = 6                // NAV_FULL

    @Volatile private var service: IBinder? = null
    private var bound = false

    val connected: Boolean get() = service != null

    fun start(ctx: Context) {
        if (bound) return
        val app = ctx.applicationContext
        bound = runCatching {
            app.bindService(Intent().setComponent(COMPONENT), object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, b: IBinder?) { service = b; Log.w(TAG, "cluster service connected") }
                override fun onServiceDisconnected(name: ComponentName?) { service = null }
            }, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!bound) Log.w(TAG, "cluster service not found")
    }

    private fun send(data: Bundle): Boolean {
        val b = service ?: return false
        val p = Parcel.obtain(); val r = Parcel.obtain()
        return try {
            p.writeInterfaceToken(DESCRIPTOR)
            p.writeInt(CMD_NAVI)
            p.writeInt(1); data.writeToParcel(p, 0)
            b.transact(TX_SEND_COMMAND, p, r, 0)
            true
        } catch (e: Exception) { Log.w(TAG, "send: ${e.message}"); false } finally { p.recycle(); r.recycle() }
    }

    private fun bundle(dataType: Int, subtype: Int?, vararg extra: Pair<String, Int>) = Bundle().apply {
        putInt("msgType", 3)
        subtype?.let { putInt("subtype", it) }
        putInt("DataType", dataType)
        for ((k, v) in extra) putInt(k, v)
    }

    fun requestMapFocus(type: Int = MAP_TYPE_SHARKOS) = send(bundle(33, null, "mapType" to type))
    fun abandonMapFocus(type: Int = MAP_TYPE_SHARKOS) = send(bundle(34, null, "mapType" to type))
    fun naviState(started: Boolean, type: Int = MAP_TYPE_SHARKOS) = send(bundle(35, 2, "mapType" to type, "naviState" to if (started) 1 else 2))
    fun presentation(state: Int, type: Int = MAP_TYPE_SHARKOS) = send(bundle(31, 15, "mapType" to type, "state" to state))
    /** The cluster's navigation view setting: 2 simple, 3 small map, 4 full map. */
    fun setDisplayMode(mode: Int) = send(bundle(30, 3, "mode" to mode))

    fun displayMode(ctx: Context): Int = runCatching { Settings.Global.getInt(ctx.contentResolver, "moet_extension_navi_display_mode", -1) }.getOrDefault(-1)
    fun focusMap(ctx: Context): Int = runCatching { Settings.Global.getInt(ctx.contentResolver, "moet_extension_navi_focus_map", -1) }.getOrDefault(-1)

    /** The cluster's Android display: the first one that isn't the main screen. */
    fun display(ctx: Context): Display? {
        val dm = ctx.getSystemService(DisplayManager::class.java) ?: return null
        return dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
            ?: dm.displays.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
    }

    fun describe(d: Display?): String {
        if (d == null) return "no second display visible to SharkOS"
        val m = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        val flags = buildList {
            if (d.flags and Display.FLAG_PRESENTATION != 0) add("presentation")
            if (d.flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
            if (d.flags and Display.FLAG_SECURE != 0) add("secure")
        }.joinToString(", ").ifEmpty { "public" }
        return "display ${d.displayId} \"${d.name}\" ${m.widthPixels}×${m.heightPixels} ${m.densityDpi}dpi ($flags)"
    }
}
