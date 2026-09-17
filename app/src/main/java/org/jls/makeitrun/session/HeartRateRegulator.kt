package org.jls.makeitrun.session

import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.heartrate.HeartRateSample
import org.jls.makeitrun.workout.model.HeartRateTarget
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import kotlin.math.exp
import kotlin.math.roundToInt

class HeartRateRegulator(
    private val target: HeartRateTarget,
    private val speedRange: TreadmillCapabilities.ValueRange,
    private val responsiveness: RegulationResponsiveness = RegulationResponsiveness.NORMAL,
    initialSpeedKmh: Double,
) {

    private var currentSpeedKmh: Double = speedRange.coerce(initialSpeedKmh)
    private var smoothedBpm: Double? = null
    private var lastBeatsPerMinute: Int? = null
    private var firstSampleAtMillis: Long? = null
    private var lastSampleAtMillis: Long? = null
    private var lastAdjustmentAtMillis: Long? = null
    private var lastFrameWasOffWrist = false

    fun update(nowElapsedMillis: Long, sample: HeartRateSample?): RegulationOutcome {
        consume(sample)

        val smoothed = smoothedBpm
        val lastSampleAt = lastSampleAtMillis

        if (smoothed == null || lastSampleAt == null) {
            return outcome(zone = ZoneStatus.UNKNOWN, alert = RegulationAlert.WAITING_FOR_SIGNAL)
        }

        val smoothedBeats = smoothed.roundToInt()
        val zone = when {
            smoothedBeats < target.minBpm -> ZoneStatus.BELOW
            smoothedBeats > target.maxBpm -> ZoneStatus.ABOVE
            else -> ZoneStatus.IN_ZONE
        }

        if (nowElapsedMillis - lastSampleAt > SIGNAL_TIMEOUT_MILLIS) {
            val alert = if (lastFrameWasOffWrist) {
                RegulationAlert.SENSOR_NOT_WORN
            } else {
                RegulationAlert.SIGNAL_LOST
            }
            return outcome(zone = ZoneStatus.UNKNOWN, alert = alert, smoothedBeats = smoothedBeats)
        }

        if (zone == ZoneStatus.IN_ZONE) {
            return outcome(zone = zone, alert = null, smoothedBeats = smoothedBeats)
        }

        if (!isReadyToCorrect(nowElapsedMillis)) {
            return outcome(zone = zone, alert = null, smoothedBeats = smoothedBeats)
        }

        val correction = if (zone == ZoneStatus.BELOW) {
            responsiveness.speedStepKmh
        } else {
            -responsiveness.speedStepKmh
        }
        val adjusted = speedRange.coerce(currentSpeedKmh + correction)

        if (adjusted == currentSpeedKmh) {
            val alert = if (zone == ZoneStatus.BELOW) {
                RegulationAlert.SPEED_AT_MAXIMUM
            } else {
                RegulationAlert.SPEED_AT_MINIMUM
            }
            return outcome(zone = zone, alert = alert, smoothedBeats = smoothedBeats)
        }

        currentSpeedKmh = adjusted
        lastAdjustmentAtMillis = nowElapsedMillis
        return outcome(
            zone = zone,
            alert = null,
            smoothedBeats = smoothedBeats,
            speedChanged = true,
        )
    }

    private fun consume(sample: HeartRateSample?) {
        if (sample == null) return
        if (sample.receivedAtElapsedMillis == lastSampleAtMillis) return

        if (!sample.measurement.isUsable) {
            lastFrameWasOffWrist = true
            return
        }

        lastFrameWasOffWrist = false
        lastBeatsPerMinute = sample.measurement.beatsPerMinute
        val beats = sample.measurement.beatsPerMinute.toDouble()
        val previousSampleAt = lastSampleAtMillis
        val previous = smoothedBpm

        smoothedBpm = if (previous == null || previousSampleAt == null) {
            beats
        } else {
            val elapsedSeconds = (sample.receivedAtElapsedMillis - previousSampleAt) / 1000.0
            val weight = 1.0 - exp(-elapsedSeconds / SMOOTHING_TIME_CONSTANT_SECONDS)
            previous + weight * (beats - previous)
        }

        lastSampleAtMillis = sample.receivedAtElapsedMillis
        if (firstSampleAtMillis == null) firstSampleAtMillis = sample.receivedAtElapsedMillis
    }

    private fun isReadyToCorrect(nowElapsedMillis: Long): Boolean {
        val lastAdjustmentAt = lastAdjustmentAtMillis
            ?: return nowElapsedMillis - (firstSampleAtMillis ?: nowElapsedMillis) >= WARM_UP_MILLIS
        return nowElapsedMillis - lastAdjustmentAt >= responsiveness.settleMillis
    }

    private fun outcome(
        zone: ZoneStatus,
        alert: RegulationAlert?,
        smoothedBeats: Int? = null,
        speedChanged: Boolean = false,
    ) = RegulationOutcome(
        targetSpeedKmh = currentSpeedKmh,
        speedChanged = speedChanged,
        beatsPerMinute = lastBeatsPerMinute.takeIf { zone != ZoneStatus.UNKNOWN },
        smoothedBpm = smoothedBeats,
        zone = zone,
        alert = alert,
    )

    companion object {
        const val SIGNAL_TIMEOUT_MILLIS = 10_000L
        const val SMOOTHING_TIME_CONSTANT_SECONDS = 10.0
        const val WARM_UP_MILLIS = 15_000L

        fun startingSpeedKmh(
            target: HeartRateTarget,
            speedRange: TreadmillCapabilities.ValueRange,
        ): Double {
            val speedPerBeat = (BRISK_ANCHOR_SPEED_KMH - EASY_ANCHOR_SPEED_KMH) /
                (BRISK_ANCHOR_BPM - EASY_ANCHOR_BPM)
            val estimate = EASY_ANCHOR_SPEED_KMH +
                (target.centerBpm - EASY_ANCHOR_BPM) * speedPerBeat
            return speedRange.coerce(estimate)
        }

        private const val EASY_ANCHOR_BPM = 110
        private const val EASY_ANCHOR_SPEED_KMH = 7.0
        private const val BRISK_ANCHOR_BPM = 170
        private const val BRISK_ANCHOR_SPEED_KMH = 14.0
    }
}

data class RegulationOutcome(
    val targetSpeedKmh: Double,
    val speedChanged: Boolean,
    val beatsPerMinute: Int?,
    val smoothedBpm: Int?,
    val zone: ZoneStatus,
    val alert: RegulationAlert?,
)

enum class ZoneStatus {
    UNKNOWN,
    BELOW,
    IN_ZONE,
    ABOVE,
}

enum class RegulationAlert {
    WAITING_FOR_SIGNAL,
    SIGNAL_LOST,
    SENSOR_NOT_WORN,
    SPEED_AT_MAXIMUM,
    SPEED_AT_MINIMUM,
}
