package org.jls.makeitrun.ftms

data class DiscoveredTreadmill(
    val name: String?,
    val address: String,
    val rssi: Int?,
    val advertisesFitnessMachine: Boolean,
)
