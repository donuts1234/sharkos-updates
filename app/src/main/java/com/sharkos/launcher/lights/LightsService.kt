package com.sharkos.launcher.lights

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import java.util.UUID

/**
 * Keeps SharkOS connected to the ambient-light box. A foreground service: a wake-up search in its own
 * process, then a direct connection to the saved address, retried until it holds, then held for good
 * and reconnected whenever it drops. (The Shark 6 never passes Bluetooth search results to apps, but
 * it does connect to a known address; this is the approach proven on the car.)
 *
 * Driven by [Lights] with intents: CONNECT address=…, SEND frames=hex,hex…, RELEASE.
 * Reports through [Lights.onState]; every step is in logcat under "SharkOS/Lights".
 */
@SuppressLint("MissingPermission")
class LightsService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var target: String? = null
    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var ready = false
    private var attempt = 0
    private var generation = 0
    /** Frames waiting to be written; a slider's frames (replaceable) give way to newer ones. */
    private val queue = ArrayDeque<Pair<ByteArray, Boolean>>()
    private var writing = false
    private var state = "IDLE"

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Ambient lighting", NotificationManager.IMPORTANCE_MIN))
        startForeground(NOTIFICATION_ID, Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("SharkOS").setContentText("Ambient lighting connected")
            .setOngoing(true).setShowWhen(false).build())
        log("Service started")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val addr = intent.getStringExtra(EXTRA_ADDRESS)?.uppercase()
                if (addr == null || !BluetoothAdapter.checkBluetoothAddress(addr)) { log("Bad address: $addr"); return START_NOT_STICKY }
                if (addr != target || (!ready && state == "IDLE")) { target = addr; restart("connect to $addr") } else publish()
            }
            ACTION_SEND -> {
                val frames = intent.getStringExtra(EXTRA_FRAMES).orEmpty().split(',').filter { it.isNotBlank() }.map(::unhex)
                val replaceable = intent.getBooleanExtra(EXTRA_REPLACEABLE, true)
                if (replaceable) queue.removeAll { it.second }
                queue.addAll(frames.map { it to replaceable })
                if (ready) flush() else if (target != null && state == "IDLE") restart("send")
            }
            ACTION_RELEASE -> { log("Disconnected by the driver"); target = null; teardown(); setState("IDLE"); stopSelf() }
            // Restarted by Android after being stopped: pick the saved lights up again
            null -> Lights.address(this)?.let { if (target == null) { target = it; restart("restarted by Android") } }
            else -> publish()
        }
        return START_STICKY
    }

    // ---- The connection loop ----

    private fun restart(why: String) {
        teardown()
        attempt = 0
        log("Starting: $why")
        main.post { beginAttempt() }
    }

    private fun beginAttempt() {
        val addr = target ?: return
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) { setState("WAITING"); log("Bluetooth is off; waiting"); main.postDelayed({ beginAttempt() }, 2000); return }
        val gen = ++generation
        attempt++
        setState("CONNECTING")
        log("Attempt $attempt: wake-up search")
        val acked = booleanArrayOf(false)
        val reply = object : ResultReceiver(main) {
            override fun onReceiveResult(code: Int, data: Bundle?) {
                if (gen != generation || acked[0]) return
                acked[0] = true
                if (code != 1) log("Wake-up search didn't start ($code); connecting anyway")
                main.postDelayed({ if (gen == generation) connect(adapter.getRemoteDevice(addr), gen) }, SETTLE_MS)
            }
        }
        runCatching { startService(Intent(this, PrimeScanService::class.java).putExtra(PrimeScanService.EXTRA_REPLY, reply).putExtra(PrimeScanService.EXTRA_TARGET, addr)) }
            .onFailure { log("Wake-up search error: ${it.message}") }
        // Connect even if the search never answers
        main.postDelayed({ if (gen == generation && !acked[0]) { acked[0] = true; connect(adapter.getRemoteDevice(addr), gen) } }, 1500)
    }

    private fun connect(device: BluetoothDevice, gen: Int) {
        log("Attempt $attempt: connecting to ${device.address}")
        gatt = runCatching { device.connectGatt(this, false, callback, BluetoothDevice.TRANSPORT_LE) }
            .onFailure { log("connectGatt refused: ${it.message}") }.getOrNull()
        main.postDelayed({
            if (gen == generation && !ready) {
                log("Attempt $attempt: no answer after ${ATTEMPT_MS / 1000}s")
                retire(gen)
            }
        }, ATTEMPT_MS)
    }

    private fun retire(gen: Int) {
        if (gen != generation) return
        val next = ++generation          // cancels this attempt's timeout
        closeGatt()
        val delay = if (attempt < 30) RETRY_MS else 15_000L      // back off after a while so we never hog the radio
        main.postDelayed({ if (next == generation && target != null) beginAttempt() }, delay)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) = main.post {
            if (g != gatt) return@post
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("Connected (status $status); reading services")
                main.postDelayed({ if (g == gatt) log("discoverServices=" + g.discoverServices()) }, 300)
            } else {
                log("Disconnected (status $status${statusName(status)})")
                val wasReady = ready
                ready = false
                writer = null
                if (wasReady) { attempt = 0; setState("CONNECTING") }
                retire(generation)
            }
        }.let { }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = main.post {
            if (g != gatt) return@post
            val ch = g.getService(SERVICE)?.getCharacteristic(WRITE)
            if (ch == null) {
                log("No FFF0/FFF3 on this box. Services: " + g.services.joinToString { it.uuid.toString().substring(4, 8) })
                retire(generation); return@post
            }
            writer = ch
            ready = true
            stopService(Intent(this@LightsService, PrimeScanService::class.java))
            setState("READY")
            log("Ready after $attempt attempt(s)")
            flush()
        }.let { }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = main.post {
            if (status != BluetoothGatt.GATT_SUCCESS) log("Write failed (status $status)")
            writing = false
            main.postDelayed({ flush() }, GAP_MS)
        }.let { }
    }

    private fun flush() {
        val g = gatt ?: return
        val ch = writer ?: return
        if (!ready || writing) return
        val frame = queue.removeFirstOrNull()?.first ?: return
        writing = true
        ch.writeType = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE else BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ch.value = frame
        val ok = runCatching { g.writeCharacteristic(ch) }.getOrDefault(false)
        log("→ " + frame.joinToString(" ") { "%02X".format(it) } + if (ok) "" else " (not sent)")
        if (!ok) writing = false
        main.postDelayed({ if (writing) { writing = false; flush() } }, 300)
    }

    // ---- Housekeeping ----

    private fun closeGatt() {
        runCatching { gatt?.disconnect(); gatt?.close() }
        gatt = null; writer = null; ready = false; writing = false
    }

    private fun teardown() {
        generation++
        main.removeCallbacksAndMessages(null)
        closeGatt()
        runCatching { stopService(Intent(this, PrimeScanService::class.java)) }
    }

    override fun onDestroy() { teardown(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun setState(s: String) { state = s; publish() }

    private fun publish() = Lights.onState(state)

    private fun log(line: String) { Log.w(PrimeScanService.TAG, line) }

    private fun statusName(s: Int) = when (s) {
        0 -> ""; 8 -> ", link timeout"; 19 -> ", box disconnected"; 22 -> ", car disconnected"
        62 -> ", couldn't establish"; 133 -> ", GATT error"; 147 -> ", connection timeout"; else -> ""
    }

    companion object {
        const val ACTION_CONNECT = "com.sharkos.launcher.lights.CONNECT"
        const val ACTION_SEND = "com.sharkos.launcher.lights.SEND"
        const val ACTION_RELEASE = "com.sharkos.launcher.lights.RELEASE"
        private const val NOTIFICATION_ID = 41
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_FRAMES = "frames"
        const val EXTRA_REPLACEABLE = "replaceable"
        private const val CHANNEL = "lights"
        private val SERVICE = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        private val WRITE = UUID.fromString("0000fff3-0000-1000-8000-00805f9b34fb")
        private const val SETTLE_MS = 250L
        private const val ATTEMPT_MS = 8_000L
        private const val RETRY_MS = 500L
        private const val GAP_MS = 60L


        private fun unhex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
