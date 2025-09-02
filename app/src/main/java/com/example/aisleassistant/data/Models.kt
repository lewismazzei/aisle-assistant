package com.example.aisleassistant.data

data class ItemWithStats(
    val id: Long,
    val name: String,
    val entryCount: Int,
    val lastSeenMs: Long?
)

data class WifiEntryRecord(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val capabilities: String?,
    val distanceMm: Int?,
    val distanceStdDevMm: Int?,
    val createdAtMs: Long
)

data class AllEntryRecord(
    val itemName: String,
    val ssid: String?,
    val bssid: String?,
    val rssi: Int?,
    val frequency: Int?,
    val capabilities: String?,
    val distanceMm: Int?,
    val distanceStdDevMm: Int?,
    val createdAtMs: Long
)
