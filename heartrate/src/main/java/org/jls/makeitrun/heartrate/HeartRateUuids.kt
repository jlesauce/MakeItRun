package org.jls.makeitrun.heartrate

import java.util.UUID

object HeartRateUuids {

    private fun shortUuid(assignedNumber: Int): UUID =
        UUID.fromString("%08X-0000-1000-8000-00805F9B34FB".format(assignedNumber))

    val HEART_RATE_SERVICE: UUID = shortUuid(0x180D)
    val DEVICE_INFORMATION_SERVICE: UUID = shortUuid(0x180A)
    val BATTERY_SERVICE: UUID = shortUuid(0x180F)
    val HEART_RATE_MEASUREMENT: UUID = shortUuid(0x2A37)
    val BODY_SENSOR_LOCATION: UUID = shortUuid(0x2A38)
    val BATTERY_LEVEL: UUID = shortUuid(0x2A19)
}
