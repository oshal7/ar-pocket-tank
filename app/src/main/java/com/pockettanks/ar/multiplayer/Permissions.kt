package com.pockettanks.ar.multiplayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {

    fun required(transport: TransportType): Array<String> {
        val perms = mutableListOf<String>()
        when (transport) {
            TransportType.BLUETOOTH -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    perms += Manifest.permission.BLUETOOTH_CONNECT
                    perms += Manifest.permission.BLUETOOTH_SCAN
                } else {
                    perms += Manifest.permission.ACCESS_FINE_LOCATION
                }
            }
            TransportType.WIFI_DIRECT -> {
                perms += Manifest.permission.ACCESS_FINE_LOCATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms += Manifest.permission.NEARBY_WIFI_DEVICES
                }
            }
        }
        return perms.toTypedArray()
    }

    fun allGranted(context: Context, permissions: Array<String>): Boolean =
        permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
