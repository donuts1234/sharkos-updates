package com.sharkos.launcher

import android.app.Application
import com.sharkos.launcher.apps.Apps
import com.sharkos.launcher.car.CarManager

/** One car connection and one app list for the life of the process. */
class SharkOsApp : Application() {
    val car: CarManager by lazy { CarManager(this) }
    val apps: Apps by lazy { Apps(this) }
}
