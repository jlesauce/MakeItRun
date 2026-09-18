package org.jls.makeitrun.history.model

import org.jls.makeitrun.session.ZoneStatus
import org.jls.makeitrun.workout.model.ResolvedStep

enum class SessionOutcome {
    IN_PROGRESS,
    COMPLETED,
    STOPPED,
    FAILED,
    ;

    companion object {
        fun parse(value: String): SessionOutcome =
            entries.firstOrNull { it.name == value } ?: STOPPED
    }
}

data class SessionSummary(
    val id: Long,
    val workoutName: String,
    val startedAt: Long,
    val endedAt: Long?,
    val outcome: SessionOutcome,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val stepsCompleted: Int,
    val stepCount: Int,
    val averageBpm: Int?,
    val maximumBpm: Int?,
    val zoneShare: Float?,
)

data class SessionSample(
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val stepIndex: Int,
    val speedKmh: Double?,
    val inclinationPercent: Double?,
    val heartRateBpm: Int?,
    val smoothedBpm: Int?,
    val targetSpeedKmh: Double?,
    val zone: ZoneStatus?,
)

data class SessionStepReport(
    val index: Int,
    val planned: ResolvedStep?,
    val elapsedSeconds: Int,
    val distanceMeters: Int,
    val averageSpeedKmh: Double?,
    val inclinationPercent: Double?,
    val averageBpm: Int?,
    val maximumBpm: Int?,
    val zoneShare: Float?,
)

data class SessionReport(
    val summary: SessionSummary,
    val failureReason: String?,
    val treadmillDistanceMeters: Int?,
    val treadmillElapsedSeconds: Int?,
    val energyKcal: Int?,
    val steps: List<SessionStepReport>,
    val samples: List<SessionSample>,
)
