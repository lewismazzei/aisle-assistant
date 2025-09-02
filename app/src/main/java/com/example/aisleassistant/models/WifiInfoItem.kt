package com.example.aisleassistant.models

data class WifiInfoItem(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int,
    val capabilities: String,
    val distanceMm: Int? = null,
    val distanceStdDevMm: Int? = null


)
