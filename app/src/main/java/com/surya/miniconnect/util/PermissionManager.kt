@file:Suppress("unused")

package com.surya.miniconnect.util

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper that declares and checks the runtime permissions required by MiniConnect
 * for peer discovery.
 */
class PermissionManager {

    /**
     * The set of permissions required for discovery. Includes NEARBY_WIFI_DEVICES for Android 13+.
     */
    fun requiredPermissions(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return base.toTypedArray()
    }

    /**
     * Returns true if all required permissions are granted.
     */
    fun hasAllPermissions(context: Context): Boolean {
        val perms = requiredPermissions()
        return perms.all { p ->
            ContextCompat.checkSelfPermission(context, p) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
