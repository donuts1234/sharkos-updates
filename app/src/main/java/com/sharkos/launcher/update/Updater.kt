// Approach adapted from Shark Hub's OtaUpdater/ApkInstaller (https://github.com/Peacemaker105/SharkHub),
// Copyright (c) 2026 Chris Murray, MIT licence. Rewritten on HttpURLConnection to avoid an HTTP library.
package com.sharkos.launcher.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.sharkos.launcher.AutoLaunch
import com.sharkos.launcher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Over-the-air updates. Two files hosted anywhere over HTTPS (here: an updates-only GitHub repo):
 *
 *   latest.json  { "versionCode": 4, "versionName": "0.1.3", "apkUrl": "https://…/SharkOS-0.1.3.apk", "notes": "…" }
 *   the APK it points to, signed with the SharkOS key.
 *
 * Check → download to app storage → confirm it's SharkOS signed with the same key → hand it to
 * Android's installer. On the Shark's Android 11 the installer asks for one tap to confirm; the first
 * time it may also ask to allow installs from SharkOS. SharkOS reopens itself after the update
 * ([UpdatedReceiver]).
 */
class Updater(private val ctx: Context) {

    data class Release(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val r: Release) : State
        data class Downloading(val pct: Int) : State
        data object Installing : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val configured: Boolean get() = MANIFEST_URL.startsWith("https://") && "GITHUB_USER" !in MANIFEST_URL

    private val prefs = ctx.getSharedPreferences("sharkos_update", Context.MODE_PRIVATE)

    /** Look for a newer release. [quiet] = the daily background check: no error shown if offline. */
    suspend fun check(quiet: Boolean = false) {
        if (!configured || _state.value is State.Downloading || _state.value is State.Installing) return
        if (!quiet) _state.value = State.Checking
        val r = withContext(Dispatchers.IO) { runCatching { fetchManifest() } }
        prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
        _state.value = r.fold(
            onSuccess = { if (it.versionCode > BuildConfig.VERSION_CODE) State.Available(it) else State.UpToDate },
            onFailure = { if (quiet) _state.value else State.Failed("Couldn't check: ${it.message ?: "no connection"}") },
        )
    }

    /** At most once a day, silently; called when Home comes to the front. */
    suspend fun checkDaily() {
        if (System.currentTimeMillis() - prefs.getLong("last_check", 0) > DAY_MS) check(quiet = true)
    }

    suspend fun downloadAndInstall(r: Release) {
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            _state.value = State.Failed("Allow SharkOS to install updates first (button below)")
            return
        }
        _state.value = State.Downloading(0)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(ctx.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val apk = download(r.apkUrl, File(dir, "SharkOS-${r.versionCode}.apk")) { _state.value = State.Downloading(it) }
                verifyIsSharkOs(apk)
                _state.value = State.Installing
                install(apk)
            }
        }
        result.onFailure { _state.value = State.Failed(it.message ?: "Update failed") }
    }

    fun canInstall() = ctx.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true      // GitHub release downloads redirect to their CDN
            setRequestProperty("Cache-Control", "no-cache")
        }

    private fun fetchManifest(): Release {
        val c = open("$MANIFEST_URL?t=${System.currentTimeMillis()}")   // bust GitHub's raw-file cache
        try {
            if (c.responseCode != 200) error("HTTP ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            return Release(j.getInt("versionCode"), j.optString("versionName", "?"), j.getString("apkUrl"), j.optString("notes", ""))
        } finally { c.disconnect() }
    }

    private fun download(url: String, dest: File, progress: (Int) -> Unit): File {
        val c = open(url)
        try {
            if (c.responseCode != 200) error("download failed (HTTP ${c.responseCode})")
            val total = c.contentLengthLong.coerceAtLeast(1)
            var done = 0L
            var last = -1
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
            return dest
        } finally { c.disconnect() }
    }

    /**
     * Only ever install SharkOS signed with our own key. Android checks the key on update anyway;
     * checking first turns a vague installer error into a clear one and refuses anything else outright.
     */
    @Suppress("DEPRECATION")
    private fun verifyIsSharkOs(file: File) {
        val pm = ctx.packageManager
        val flags = PackageManager.GET_SIGNATURES or PackageManager.GET_SIGNING_CERTIFICATES
        val apk = pm.getPackageArchiveInfo(file.path, flags) ?: error("the download isn't a valid APK")
        if (apk.packageName != ctx.packageName) error("the download isn't SharkOS (${apk.packageName})")
        val theirs = signers(apk)
        val ours = signers(pm.getPackageInfo(ctx.packageName, flags))
        if (theirs.isNotEmpty() && ours.isNotEmpty() && theirs != ours) error("the download is signed with a different key")
    }

    @Suppress("DEPRECATION")
    private fun signers(pi: PackageInfo): Set<String> =
        (pi.signingInfo?.apkContentsSigners ?: pi.signatures).orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()

    private fun install(apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            try {
                s.openWrite("SharkOS.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out, 128 * 1024) }; s.fsync(out) }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val cb = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags)
                s.commit(cb.intentSender)
            } catch (e: Throwable) {
                runCatching { s.abandon() }
                throw e
            }
        }
    }

    companion object {
        /** Baked in at build time (sharkos.updateUrl in gradle.properties). */
        val MANIFEST_URL: String = BuildConfig.UPDATE_URL
        private const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

/** The installer's answer: show the confirm dialog when Android asks for one, or say what went wrong. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))?.let {
                    ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.w("SharkOS/Update", "installed")
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.w("SharkOS/Update", "install failed $status $msg")
                Toast.makeText(ctx.applicationContext, "Update didn't install ($status)", Toast.LENGTH_LONG).show()
            }
        }
    }
}

/** After an update replaces SharkOS, Android stops the old copy: open the new one straight away. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) AutoLaunch.bringToFront(ctx.applicationContext)
    }
}
