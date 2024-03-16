package com.loweffort.quakesense

data class DeviceInfo(
    val id : String? = null,
    val deviceModel : String? = null,
    val deviceManufacturer : String? = null,
    val deviceSDKVersion : String? = null,
    val appVersion : String? = null,
    val tokenFCM : String? = null,
)
