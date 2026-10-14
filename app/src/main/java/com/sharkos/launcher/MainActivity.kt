package com.sharkos.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.sharkos.launcher.data.Prefs
import com.sharkos.launcher.ui.CarPlayOverlay
import com.sharkos.launcher.ui.Root
import com.sharkos.launcher.ui.Screen

class MainActivity : ComponentActivity() {

    private var screen by mutableStateOf(Screen.HOME)

    companion object {
        const val EXTRA_AUTO_LAUNCH = "sharkos.auto_launch"
        /** Open on this [Screen] (by name): used when the CarPlay bar hands back to SharkOS. */
        const val EXTRA_SCREEN = "sharkos.screen"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        val app = application as SharkOsApp
        setContent { Root(app, screen) { screen = it } }
        CarPlayOverlay.startAutoAttach(this)
        com.sharkos.launcher.ui.ClusterDisplay.autoStart(this)
        askForCarPermissionOnce()
    }

    /** Pressing Home while SharkOS is already in front goes back to its Home screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME) || intent.getBooleanExtra(EXTRA_AUTO_LAUNCH, false)) screen = Screen.HOME
        intent.getStringExtra(EXTRA_SCREEN)?.let { name -> Screen.entries.firstOrNull { it.name == name }?.let { screen = it } }
    }

    /** Back in SharkOS: the CarPlay bar isn't needed on top of it. */
    override fun onResume() {
        super.onResume()
        CarPlayOverlay.sharkOsInFront = true
        if (CarPlayOverlay.isOpen) CarPlayOverlay.close(this, then = null)
    }

    override fun onPause() {
        super.onPause()
        CarPlayOverlay.sharkOsInFront = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()   // a dialog or a swipe can bring the bars back; put them away again
    }

    /**
     * Full screen while SharkOS is in front: BYD's status bar along the top and its bar along the
     * bottom both go, and SharkOS's own bottom bar takes over climate and seats. A swipe from the
     * edge shows them briefly. On the Shark 6 the top bar is known to hide this way (Shark Hub);
     * whether BYD's bottom climate bar counts as the system navigation bar needs checking on the car.
     */
    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    /**
     * BYDAUTO_STATISTIC_COMMON (battery, fuel, range) is a "dangerous" permission on the Shark 6, so
     * it needs one yes from the driver. Asked once; on a unit without BYD's permissions it's a no-op.
     */
    private fun askForCarPermissionOnce() {
        val prefs = Prefs(this)
        if (prefs.askedCarPermission) return
        val p = "android.permission.BYDAUTO_STATISTIC_COMMON"
        prefs.askedCarPermission = true
        val defined = runCatching { packageManager.getPermissionInfo(p, 0) }.isSuccess
        if (defined && checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(p), 1)
    }
}
