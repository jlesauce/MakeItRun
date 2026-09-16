package org.jls.makeitrun.ftms

import java.util.UUID

object FtmsUuids {

    private fun shortUuid(assignedNumber: Int): UUID =
        UUID.fromString("%08X-0000-1000-8000-00805F9B34FB".format(assignedNumber))

    val FITNESS_MACHINE_SERVICE: UUID = shortUuid(0x1826)
    val DEVICE_INFORMATION_SERVICE: UUID = shortUuid(0x180A)
    val FITNESS_MACHINE_FEATURE: UUID = shortUuid(0x2ACC)
    val TREADMILL_DATA: UUID = shortUuid(0x2ACD)
    val TRAINING_STATUS: UUID = shortUuid(0x2AD3)
    val SUPPORTED_SPEED_RANGE: UUID = shortUuid(0x2AD4)
    val SUPPORTED_INCLINATION_RANGE: UUID = shortUuid(0x2AD5)
    val FITNESS_MACHINE_CONTROL_POINT: UUID = shortUuid(0x2AD9)
    val FITNESS_MACHINE_STATUS: UUID = shortUuid(0x2ADA)
}
