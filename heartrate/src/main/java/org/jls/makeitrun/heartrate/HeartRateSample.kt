package org.jls.makeitrun.heartrate

data class HeartRateSample(
    val measurement: HeartRateMeasurement,
    val receivedAtElapsedMillis: Long,
)
