package com.sharkos.launcher.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import android.util.LruCache
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import com.sharkos.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** One launchable app. */
data class AppEntry(val label: String, val pkg: String, val activity: String) {
    val component: ComponentName get() = ComponentName(pkg, activity)
}

/**
 * The installed apps and the dock. Icons are decoded lazily, once, at the size they're drawn and
 * kept in a small memory cache, so opening the app list doesn't load every app's full-size artwork.
 */
class Apps(private val context: Context) {
    private val pm = context.packageManager
    private val prefs = Prefs(context)

    private val _all = MutableStateFlow<List<AppEntry>>(emptyList())
    val all: StateFlow<List<AppEntry>> = _all.asStateFlow()

    private val _dock = MutableStateFlow(prefs.dock?.take(MAX_DOCK) ?: emptyList())   // older versions allowed 5
    /** Pinned package names, in order. */
    val dock: StateFlow<List<String>> = _dock.asStateFlow()

    // ~48 icons at 128 px is ~3 MB: enough for the dock and a screen of the app list.
    private val icons = LruCache<String, Bitmap>(48)

    /** Re-read the installed apps (on start, and whenever the app list opens). */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = runCatching { pm.queryIntentActivities(intent, 0) }.getOrDefault(emptyList())
            .filter { it.activityInfo.packageName != context.packageName }
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
        _all.value = list
        if (prefs.dock == null) {
            // First run: pin the BYD apps people reach for most, where this unit has them.
            val installed = list.map { it.pkg }.toSet()
            _dock.value = DEFAULT_DOCK.filter { it in installed }
        }
    }

    // ---- The Home screen's Shortcuts card: four spots, "" for an empty one ----

    private val _shortcuts = MutableStateFlow(prefs.shortcuts?.let { fit(it) } ?: fit(DEFAULT_SHORTCUTS))
    val shortcuts: StateFlow<List<String>> = _shortcuts.asStateFlow()

    private fun fit(l: List<String>) = (l + List(SHORTCUTS) { "" }).take(SHORTCUTS)

    fun setShortcut(index: Int, pkg: String) {
        if (index !in 0 until SHORTCUTS) return
        _shortcuts.value = _shortcuts.value.toMutableList().also { it[index] = pkg }
        prefs.shortcuts = _shortcuts.value
    }

    fun find(pkg: String): AppEntry? = _all.value.firstOrNull { it.pkg == pkg }

    fun isPinned(pkg: String) = pkg in _dock.value

    /** Pin or unpin; the dock holds at most [MAX_DOCK]. Returns false if it was full. */
    fun togglePin(pkg: String): Boolean {
        val cur = _dock.value
        val next = when {
            pkg in cur -> cur - pkg
            cur.size >= MAX_DOCK -> return false
            else -> cur + pkg
        }
        _dock.value = next
        prefs.dock = next
        return true
    }

    /** Add [pkg] to the end of the bar if there's room and it isn't there already. */
    fun pin(pkg: String): Boolean {
        val cur = _dock.value
        if (pkg in cur) return true
        if (cur.size >= MAX_DOCK) return false
        _dock.value = cur + pkg
        prefs.dock = _dock.value
        return true
    }

    /** Back to the first-run bar: the BYD apps this unit has from [DEFAULT_DOCK]. */
    fun resetDock() {
        _dock.value = DEFAULT_DOCK.filter { isInstalled(it) }
        prefs.dock = _dock.value
    }

    fun unpin(pkg: String) {
        _dock.value = _dock.value - pkg
        prefs.dock = _dock.value
    }

    /** Icon at [px] square, decoded once and cached. Blocking: call from a background dispatcher. */
    fun icon(pkg: String, px: Int): Bitmap? {
        val key = "$pkg@$px"
        icons.get(key)?.let { return it }
        val bmp = runCatching { pm.getApplicationIcon(pkg).toBitmap(px, px) }.getOrNull() ?: return null
        icons.put(key, bmp)
        return bmp
    }

    fun launch(pkg: String) {
        runCatching {
            val i = find(pkg)?.let { Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(it.component) }
                ?: pm.getLaunchIntentForPackage(pkg)
                ?: error("not installed")
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            context.startActivity(i)
        }.onFailure {
            Log.w("SharkOS/Apps", "launch $pkg: ${it.message}")
            Toast.makeText(context, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    fun isInstalled(pkg: String) = runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    companion object {
        const val MAX_DOCK = 3

        // Shark 6 packages (Shark Hub's probe, SOC_260811_S), plus Google Maps if it's been added.
        const val PKG_MAPS = "com.google.android.apps.maps"
        const val PKG_MUSIC = "com.byd.localmusic"
        const val PKG_PHONE = "com.byd.bluetoothcall"
        const val PKG_CAMERA_360 = "com.byd.avm"
        const val PKG_CAR_SETTINGS = "com.byd.carsettings"
        /** Three BYD apps to start; the other two spots stay empty (+) for the driver to fill. */
        val DEFAULT_DOCK = listOf(PKG_MUSIC, PKG_PHONE, PKG_CAMERA_360)
        const val PKG_CARPLAY = "com.ts.carplay.app"
        const val SHORTCUTS = 4
        val DEFAULT_SHORTCUTS = listOf(PKG_CARPLAY, PKG_MUSIC, PKG_CAMERA_360, PKG_PHONE)
    }
}
