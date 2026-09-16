package org.jls.makeitrun.heartrate

data class DiscoveredHeartRateSensor(
    val name: String?,
    val address: String,
    val rssi: Int?,
    val advertisesHeartRate: Boolean,
)
