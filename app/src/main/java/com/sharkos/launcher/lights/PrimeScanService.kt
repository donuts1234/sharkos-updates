package com.sharkos.launcher.lights

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log

/**
 * A low-latency Bluetooth search in its own process, whose results are ignored: it only wakes the
 * car's radio so the direct connection that follows can find the box. The same approach as
 * Sharkware's working helper on this head unit. Stops itself after [MAX_MS].
 */
@SuppressLint("MissingPermission")
class PrimeScanService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var reply: ResultReceiver? = null
    private var target: String? = null
    private val expiry = Runnable { stopSelf() }

    private val cb = object : ScanCallback() {
        override fun onScanResult(type: Int, r: ScanResult) {
            if (r.device?.address.equals(target, ignoreCase = true)) Log.w(TAG, "TARGET_SEEN rssi=${r.rssi}")
        }
        override fun onScanFailed(code: Int) {
            Log.w(TAG, "PRIME_SCAN_FAILED=$code")
            reply?.send(-code.coerceAtLeast(1), Bundle.EMPTY)
            scanner = null
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        reply = intent?.getParcelableExtra(EXTRA_REPLY)
        target = intent?.getStringExtra(EXTRA_TARGET)
        val ok = runCatching {
            if (scanner == null) {
                val a = BluetoothAdapter.getDefaultAdapter() ?: error("no adapter")
                if (!a.isEnabled) error("bluetooth off")
                scanner = a.bluetoothLeScanner ?: error("no scanner")
                scanner!!.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
                Log.w(TAG, "PRIME_SCAN_ISSUED pid=${android.os.Process.myPid()}")
            }
        }.onFailure { Log.w(TAG, "PRIME_SCAN_ERROR ${it.message}") }.isSuccess
        reply?.send(if (ok) 1 else -1, Bundle.EMPTY)
        main.removeCallbacks(expiry)
        main.postDelayed(expiry, MAX_MS)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        main.removeCallbacks(expiry)
        runCatching { scanner?.stopScan(cb) }
        scanner = null
        Log.w(TAG, "PRIME_SCAN_STOP")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "SharkOS/Lights"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_TARGET = "target"
        const val MAX_MS = 45_000L
    }
}
