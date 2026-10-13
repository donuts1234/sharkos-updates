package com.sharkos.launcher.car

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import dalvik.system.PathClassLoader
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The head-unit maker's own car service (Desay SV, `com.ts.appservice.caradapter`), present on the
 * Shark 6 (SOC_260811_S). It fronts the same vehicle data as BYD's `bydauto` devices, but it already
 * holds BYD's permissions itself, so reading battery / range / fuel through its "general" service needs
 * none of the "dangerous" BYDAUTO_STATISTIC_COMMON grant the car never prompts for.
 *
 * Bound once, then called by method name like [CarBackend]. The AIDL stubs live on the head unit; they
 * are loaded from the boot classpath, or failing that from the caradapter APK. Getter names are the
 * same as BYDAutoStatisticDevice's (getElecPercentageValue, getDrivingRangeAll…).
 */
class TsCarAdapter(private val ctx: Context) {
    private val services = ConcurrentHashMap<String, Any>()
    private val methods = ConcurrentHashMap<String, List<Method>>()
    private val worker = Executors.newSingleThreadExecutor()
    @Volatile private var bound = false

    val connected: Boolean get() = services.isNotEmpty()

    /** Desay services found by [PROBE], as "key=Interface"; empty until connected. */
    @Volatile var found: List<String> = emptyList()
        private set

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            runCatching {
                val loader = classLoader()
                val adapter = stub(loader, "ICarAdapterService", binder)
                val get = adapter.javaClass.getMethod("getCarAdapterService", String::class.java)
                for ((key, iface) in SERVICES) {
                    runCatching {
                        val b = get.invoke(adapter, key) as? IBinder ?: return@runCatching
                        services[key] = stub(loader, iface, b)
                    }.onFailure { Log.w(TAG, "service $key: ${it.message}") }
                }
                Log.w(TAG, "connected: ${services.keys}")
                // Which other services this unit offers, by their binder's interface name: where a
                // media / cluster feed might live. Diagnostics only.
                found = PROBE.mapNotNull { key ->
                    val b = runCatching { get.invoke(adapter, key) as? IBinder }.getOrNull() ?: return@mapNotNull null
                    "$key=" + (runCatching { b.interfaceDescriptor }.getOrNull()?.substringAfterLast('.') ?: "?")
                }
            }.onFailure { Log.w(TAG, "adapter: ${it.javaClass.simpleName}: ${it.message}") }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            services.clear(); methods.clear()
        }
    }

    fun connect() {
        if (bound) return
        val i = Intent(ACTION).setClassName(PKG, "$PKG.CarAdapterService")
        bound = runCatching { ctx.bindService(i, Context.BIND_AUTO_CREATE, worker, conn) }.getOrDefault(false)
        if (!bound) Log.w(TAG, "bind failed (not a Desay head unit?)")
    }

    /** Call [method] on [service] with [args]; null if the service or method isn't there. Blocking. */
    fun call(service: String, method: String, vararg args: Any?): Any? {
        val target = services[service] ?: return null
        val m = methods.getOrPut("$service.$method") {
            target.javaClass.methods.filter { it.name == method }
        }.firstOrNull { it.parameterTypes.size == args.size } ?: return null
        return runCatching { m.invoke(target, *args) }.getOrNull()
    }

    fun number(service: String, method: String, vararg args: Any?): Double? =
        (call(service, method, *args) as? Number)?.toDouble()?.takeIf { it.isFinite() }

    private fun classLoader(): ClassLoader {
        runCatching { Class.forName("$LIB.ICarAdapterService\$Stub"); return javaClass.classLoader!! }
        // Not on the boot classpath: take the stubs from the service's own APK.
        val apk = ctx.packageManager.getApplicationInfo(PKG, 0).sourceDir
        return PathClassLoader(apk, ctx.classLoader)
    }

    private fun stub(loader: ClassLoader, iface: String, b: IBinder): Any =
        loader.loadClass("$LIB.$iface\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, b)!!

    private companion object {
        const val TAG = "SharkOS/TsAdapter"
        const val PKG = "com.ts.appservice.caradapter"
        const val ACTION = "com.ts.lib.caradapter.ICarAdapterService"
        const val LIB = "com.ts.lib.caradapter"
        val SERVICES = mapOf("general" to "general.ICarGeneralService", "body" to "body.ICarBodyService")
        val PROBE = listOf("general", "body", "sensor", "cabin", "setting", "hvac", "cloud", "assist",
            "media", "multimedia", "music", "audio", "source", "cluster", "carplay", "projection", "radio", "instrument")
    }
}
