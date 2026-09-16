package org.jls.makeitrun.heartrate

data class HeartRateMeasurement(
    val beatsPerMinute: Int,
    val sensorContact: SensorContact,
    val energyExpendedKiloJoules: Int?,
    val rrIntervalsMillis: List<Int>,
) {

    val isUsable: Boolean
        get() = beatsPerMinute > 0 && sensorContact != SensorContact.NOT_DETECTED

    enum class SensorContact {
        NOT_SUPPORTED,
        DETECTED,
        NOT_DETECTED,
    }
}
