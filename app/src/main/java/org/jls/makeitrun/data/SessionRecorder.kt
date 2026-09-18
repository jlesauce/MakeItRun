package org.jls.makeitrun.data

import org.jls.makeitrun.heartrate.HeartRateSample
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.session.SessionProgress
import org.jls.makeitrun.session.ZoneStatus
import timber.log.Timber

class SessionRecorder internal constructor(
    val sessionId: Long,
    private val dao: SessionHistoryDao,
    private var lastRecordedSecond: Int = -1,
    private var lastStepIndex: Int = 0,
) {

    private val pending = mutableListOf<SessionSampleEntity>()
    private var lastProgress: SessionProgress? = null

    suspend fun onTick(
        nowElapsedMillis: Long,
        progress: SessionProgress,
        heartRate: HeartRateSample?,
    ) {
        lastProgress = progress
        lastStepIndex = progress.stepIndex

        val second = progress.totalElapsedSeconds
        if (second <= lastRecordedSecond) return
        lastRecordedSecond = second

        pending += sample(second, progress, heartRate.beatsPerMinuteAt(nowElapsedMillis))
        if (pending.size >= SAMPLES_PER_FLUSH) flush()
    }

    suspend fun finish(outcome: SessionOutcome, failureReason: String?) {
        flush()

        val record = dao.findRecord(sessionId) ?: return
        val progress = lastProgress
        val live = progress?.liveData

        dao.updateRecord(
            record.copy(
                endedAt = System.currentTimeMillis(),
                outcome = outcome.name,
                failureReason = failureReason,
                elapsedSeconds = progress?.totalElapsedSeconds ?: record.elapsedSeconds,
                distanceMeters = progress?.totalDistanceMeters ?: record.distanceMeters,
                stepsCompleted = if (outcome == SessionOutcome.COMPLETED) {
                    record.stepCount
                } else {
                    lastStepIndex
                },
                treadmillDistanceMeters = live?.totalDistanceMeters
                    ?: record.treadmillDistanceMeters,
                treadmillElapsedSeconds = live?.elapsedTimeSeconds
                    ?: record.treadmillElapsedSeconds,
                energyKcal = live?.totalEnergyKcal ?: record.energyKcal,
            )
        )
        Timber.i(
            "Seance %d refermee en %s : %d s, %d m",
            sessionId,
            outcome,
            progress?.totalElapsedSeconds ?: record.elapsedSeconds,
            progress?.totalDistanceMeters ?: record.distanceMeters,
        )
    }

    private suspend fun flush() {
        if (pending.isEmpty()) return
        dao.insertSamples(pending.toList())
        pending.clear()
    }

    private fun sample(
        second: Int,
        progress: SessionProgress,
        beatsPerMinute: Int?,
    ) = SessionSampleEntity(
        sessionId = sessionId,
        elapsedSeconds = second,
        distanceMeters = progress.totalDistanceMeters,
        stepIndex = progress.stepIndex,
        speedKmh = progress.liveData?.instantaneousSpeedKmh,
        inclinationPercent = progress.liveData?.inclinationPercent,
        heartRateBpm = beatsPerMinute,
        smoothedBpm = progress.regulation?.smoothedBpm,
        targetSpeedKmh = progress.regulation?.targetSpeedKmh
            ?: progress.currentStep.step.targetSpeedKmh,
        zone = progress.regulation?.zone?.takeIf { it != ZoneStatus.UNKNOWN }?.name,
    )

    private fun HeartRateSample?.beatsPerMinuteAt(nowElapsedMillis: Long): Int? {
        val sample = this ?: return null
        if (!sample.measurement.isUsable) return null
        if (nowElapsedMillis - sample.receivedAtElapsedMillis > STALE_SAMPLE_MILLIS) return null
        return sample.measurement.beatsPerMinute
    }

    private companion object {
        const val SAMPLES_PER_FLUSH = 15
        const val STALE_SAMPLE_MILLIS = 10_000L
    }
}
