package com.sharkos.launcher

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.sharkos.launcher.data.Prefs

/**
 * "Open SharkOS when the car starts", for head units that won't let a third-party app be the
 * default Home. When the unit finishes booting, a short job waits for BYD's own launcher to come
 * up and then brings SharkOS to the front over it.
 *
 * Android blocks an app from opening itself in the background, except for an app allowed to
 * display over other apps; that's the one permission this needs (Settings shows its state).
 * Nothing stays running: the job fires once per start-up and ends.
 */
object AutoLaunch {
    private const val TAG = "SharkOS/AutoLaunch"
    private const val JOB_ID = 7101

    /** Whether Android will let SharkOS open itself from the background. */
    fun canLaunchFromBackground(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun schedule(ctx: Context, delayMs: Long = START_DELAY_MS) {
        val js = ctx.getSystemService(JobScheduler::class.java) ?: return
        val job = JobInfo.Builder(JOB_ID, ComponentName(ctx, AutoLaunchJob::class.java))
            .setMinimumLatency(delayMs)
            .setOverrideDeadline(delayMs + 5_000)
            .build()
        val r = js.schedule(job)
        Log.w(TAG, "scheduled in ${delayMs}ms: ${if (r == JobScheduler.RESULT_SUCCESS) "ok" else "failed"}")
    }

    fun bringToFront(ctx: Context) {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra(MainActivity.EXTRA_AUTO_LAUNCH, true)
        runCatching { ctx.startActivity(i) }
            .onSuccess { Log.w(TAG, "brought SharkOS to the front") }
            .onFailure { Log.w(TAG, "couldn't open SharkOS: ${it.message}") }
    }

    /**
     * The Shark 6 doesn't restart when the car is switched off: it sleeps (BYD's "STR") and wakes
     * where it was, so the start-up broadcast only comes after a real restart. Waking turns the
     * screen back on, which a running SharkOS hears here; it then does what start-up does.
     */
    fun watchWake(app: Context) {
        if (watching) return
        watching = true
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                Log.w(TAG, "wake: ${i.action}")
                if (i.action == Intent.ACTION_SCREEN_OFF) { asleepAt = android.os.SystemClock.elapsedRealtime(); return }
                onWake(app)
            }
        }
        runCatching {
            app.registerReceiver(r, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
            })
        }.onFailure { Log.w(TAG, "can't watch for wake: ${it.message}") }
    }

    private var watching = false
    private var asleepAt = 0L
    private var lastWake = 0L

    private fun onWake(app: Context) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastWake < 15_000) return            // SCREEN_ON and USER_PRESENT both arrive
        lastWake = now
        com.sharkos.launcher.lights.Lights.start(app)
        com.sharkos.launcher.car.DriveTracker.markNewDrive(app)
        com.sharkos.launcher.ui.ClusterDisplay.afterWake(app)
        if (!Prefs(app).autoLaunch) return
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Not over CarPlay (or anything else of SharkOS's own) that the driver had up
            if (com.sharkos.launcher.ui.CarPlayOverlay.sharkOsInFront) return@postDelayed
            if (com.sharkos.launcher.car.CarPlayLink.displayShowing() == true) return@postDelayed
            bringToFront(app)
        }, START_DELAY_MS)
    }

    /** Long enough for BYD's launcher to finish appearing, so SharkOS lands on top of it, not under. */
    private const val START_DELAY_MS = 4_000L
}

/** Start-up signal. QUICKBOOT_POWERON is what some head units send instead of BOOT_COMPLETED. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("SharkOS/AutoLaunch", "start-up broadcast: ${intent.action}")
        // Ambient lighting reconnects at start-up whether or not SharkOS opens itself
        com.sharkos.launcher.lights.Lights.start(context.applicationContext)
        // The car just started: "this drive" on the Trip page counts from here
        com.sharkos.launcher.car.DriveTracker.markNewDrive(context)
        com.sharkos.launcher.ui.CarPlayOverlay.startAutoAttach(context)
        com.sharkos.launcher.ui.ClusterDisplay.autoStart(context)
        if (!Prefs(context).autoLaunch) return
        AutoLaunch.schedule(context.applicationContext)
    }
}

class AutoLaunchJob : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        AutoLaunch.bringToFront(applicationContext)
        return false
    }

    override fun onStopJob(params: JobParameters) = false
}
