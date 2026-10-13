// Adapted from Shark Hub (https://github.com/Peacemaker105/SharkHub), Copyright (c) 2026 Chris Murray, MIT licence.
package com.sharkos.launcher.car

import android.content.Context
import android.util.Log
import dalvik.system.PathClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * The BYD vehicle API, reached at runtime and called by method name.
 *
 * The `android.hardware.bydauto` client library isn't in the framework on the Shark 6: it ships inside
 * BYD's own climate app (/system/app/BydHvac/BydHvac.apk on SOC_260811_S), so it's loaded from that
 * APK with a PathClassLoader. Each device (ac, setting, statistic…) is a singleton from
 * `getInstance(Context)`, gated by the android.permission.BYDAUTO_* permissions in the manifest.
 * Calls go by name + argument count so a firmware that differs only in types still works.
 */
class CarBackend private constructor(val name: String, targets: Map<String, Any>) {

    // getMethods() copies the whole array per call and BYD devices expose hundreds of methods, so
    // index once; the poll probes a dozen names every couple of seconds.
    private class Target(val key: String, val obj: Any) {
        val methodsByName: Map<String, List<Method>> =
            obj.javaClass.methods.filter { it.declaringClass != Any::class.java }.groupBy { it.name }
    }

    private val targets: List<Target> = targets.map { (k, v) -> Target(k, v) }

    val deviceKeys: Set<String> get() = targets.map { it.key }.toSet()

    /** Whether the call's device has a method of that name taking that many arguments. */
    fun has(call: Call): Boolean = find(call) != null

    /**
     * Invoke [call]; null for a void method (which is why existence is a separate question, [has]).
     * The method's own exception is rethrown unwrapped so a SecurityException reaches the caller.
     */
    fun invoke(call: Call): Any? {
        val (target, m) = find(call) ?: throw NoSuchMethodException("${call.describe()} on $name")
        return try {
            m.invoke(target.obj, *boxArgs(m, call.args))
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun find(call: Call): Pair<Target, Method>? {
        for (t in targets) {
            if (call.device != null && t.key != call.device) continue
            val candidates = t.methodsByName[call.method].orEmpty().filter { it.parameterTypes.size == call.args.size }
            if (candidates.isEmpty()) continue
            val m = candidates.firstOrNull { m ->
                m.parameterTypes.withIndex().all { (i, pt) -> assignable(pt, call.args[i]) }
            } ?: candidates.first()
            return t to m
        }
        return null
    }

    private fun assignable(paramType: Class<*>, arg: Any?): Boolean {
        if (arg == null) return !paramType.isPrimitive
        val boxed = box(paramType)
        return boxed.isInstance(arg) || (Number::class.java.isAssignableFrom(boxed) && arg is Number)
    }

    private fun boxArgs(m: Method, args: Array<out Any?>): Array<Any?> =
        m.parameterTypes.mapIndexed { i, pt ->
            val a = args[i]
            when {
                a is Number && (pt == Int::class.javaPrimitiveType || pt == Integer::class.java) -> a.toInt()
                a is Number && (pt == Float::class.javaPrimitiveType || pt == java.lang.Float::class.java) -> a.toFloat()
                a is Number && (pt == Double::class.javaPrimitiveType || pt == java.lang.Double::class.java) -> a.toDouble()
                a is Number && (pt == Long::class.javaPrimitiveType || pt == java.lang.Long::class.java) -> a.toLong()
                else -> a
            }
        }.toTypedArray()

    private fun box(c: Class<*>): Class<*> = when (c) {
        Int::class.javaPrimitiveType -> Integer::class.java
        Float::class.javaPrimitiveType -> java.lang.Float::class.java
        Double::class.javaPrimitiveType -> java.lang.Double::class.java
        Long::class.javaPrimitiveType -> java.lang.Long::class.java
        Boolean::class.javaPrimitiveType -> java.lang.Boolean::class.java
        else -> c
    }

    companion object {
        private const val TAG = "SharkOS/Car"

        /**
         * BYD apps that bundle the bydauto client library. Each carries the devices its app needs, so a
         * device missing from the climate app (multimedia, say) is looked for in the others in turn.
         */
        private val BYD_CLIENT_PACKAGES = listOf("com.byd.hvac", "com.byd.autoservice", "com.byd.localmusic", "com.byd.mycar", "com.byd.carsettings")

        /** Why each device that failed to open failed, per package tried; shown in Media diagnostics. */
        @Volatile var openErrors: Map<String, String> = emptyMap()
            private set

        /** Only what SharkOS reads or sets: fewer devices, fewer binder connections held open. */
        private val BYD_DEVICES = linkedMapOf(
            "ac" to "android.hardware.bydauto.ac.BYDAutoAcDevice",
            "setting" to "android.hardware.bydauto.setting.BYDAutoSettingDevice",
            "statistic" to "android.hardware.bydauto.statistic.BYDAutoStatisticDevice",
            "speed" to "android.hardware.bydauto.speed.BYDAutoSpeedDevice",
            "tyre" to "android.hardware.bydauto.tyre.BYDAutoTyreDevice",
            // What's playing, from any source including CarPlay (BYD's own widget and cluster read it)
            "multimedia" to "android.hardware.bydauto.multimedia.BYDAutoMultimediaDevice",
            // The cluster's music feed: a second place the head unit puts the current song
            "instrument" to "android.hardware.bydauto.instrument.BYDAutoInstrumentDevice",
        )

        /**
         * Open the BYD devices, or null on a unit without them (a bench, an emulator, the preview
         * renderer). Never throws. Blocking: loads classes and makes binder calls, keep it off the
         * main thread.
         */
        fun discover(ctx: Context): CarBackend? = runCatching {
            val apks = BYD_CLIENT_PACKAGES.mapNotNull { pkg ->
                runCatching { pkg to ctx.packageManager.getApplicationInfo(pkg, 0).sourceDir }.getOrNull()
            }
            if (apks.isEmpty()) return null
            val loaders = apks.map { (pkg, apk) -> pkg to PathClassLoader(apk, ctx.classLoader) }
            val devices = LinkedHashMap<String, Any>()
            val errors = LinkedHashMap<String, String>()
            for ((key, cls) in BYD_DEVICES) {
                val tried = mutableListOf<String>()
                for ((pkg, loader) in loaders) {
                    val r = runCatching { instantiate(loader.loadClass(cls), ctx) }
                    val dev = r.getOrNull()
                    if (dev != null) { devices[key] = dev; break }
                    val e = r.exceptionOrNull()
                    val cause = (e as? java.lang.reflect.InvocationTargetException)?.targetException ?: e
                    tried += "${pkg.removePrefix("com.byd.")}: ${cause?.javaClass?.simpleName ?: "null"}${cause?.message?.let { ": " + it.take(80) } ?: ""}"
                }
                if (key !in devices) { errors[key] = tried.joinToString(" | "); Log.w(TAG, "device $key: $tried") }
            }
            openErrors = errors
            if (devices.isEmpty()) null
            else CarBackend("bydauto (${File(apks.first().second).name})", devices).also { Log.w(TAG, "connected: ${devices.keys}") }
        }.getOrNull()

        private fun instantiate(c: Class<*>, ctx: Context): Any? {
            val factories = c.methods.filter { Modifier.isStatic(it.modifiers) && it.name == "getInstance" }
            factories.firstOrNull { it.parameterTypes.singleOrNull()?.isAssignableFrom(Context::class.java) == true }
                ?.let { return it.invoke(null, ctx) }
            factories.firstOrNull { it.parameterTypes.isEmpty() }?.let { return it.invoke(null) }
            return c.getConstructor(Context::class.java).newInstance(ctx)
        }
    }
}

/** One call: method name, arguments in the encoding the car expects, and which device it lives on. */
class Call(val method: String, vararg val args: Any?, val device: String? = null) {
    fun describe(): String = (device?.let { "$it." } ?: "") + "$method/${args.size}"
}
