package com.loweffort.quakesense

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

class DeviceIdHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("device_id_prefs", Context.MODE_PRIVATE)
    private val deviceIdKey = "device_id"

    val deviceId: String
        get() {
            var id = prefs.getString(deviceIdKey, null)
            if (id.isNullOrEmpty()) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString(deviceIdKey, id).apply()
            }
            return id
        }
}