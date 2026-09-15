package org.jls.makeitrun.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Permissions a demander a l'execution pour scanner et piloter un tapis.
 *
 * Android 12 a scinde l'ancienne permission Bluetooth en droits dedies. Avant cette version,
 * le scan BLE etait assimile a de la geolocalisation et exigeait donc la position fine.
 */
object BluetoothPermissions {

    val required: List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun allGranted(context: Context): Boolean = required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
