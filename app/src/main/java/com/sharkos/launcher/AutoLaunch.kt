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

    /** Long enough for BYD's launcher to finish appearing, so SharkOS lands on top of it, not under. */
    private const val START_DELAY_MS = 4_000L
}

/** Start-up signal. QUICKBOOT_POWERON is what some head units send instead of BOOT_COMPLETED. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w("SharkOS/AutoLaunch", "start-up broadcast: ${intent.action}")
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
