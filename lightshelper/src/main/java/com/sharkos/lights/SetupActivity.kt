package com.sharkos.lights

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** Invisible: asks for location once (Android needs it to search for Bluetooth devices), then closes. */
class SetupActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) finish()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        LightsService.post(this, "Location for the helper: " + if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) "allowed" else "refused")
        finish()
    }
}
