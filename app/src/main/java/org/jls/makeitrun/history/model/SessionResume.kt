package org.jls.makeitrun.history.model

import org.jls.makeitrun.workout.model.Workout

data class SessionResumePoint(
    val sessionId: Long,
    val workout: Workout,
    val stepCount: Int,
    val stepIndex: Int,
    val stepElapsedSeconds: Int,
    val stepDistanceMeters: Int,
    val completedStepsSeconds: Int,
    val distanceMeters: Int,
)

object SessionResume {

    const val WINDOW_MILLIS = 30L * 60L * 1_000L

    private val RESUMABLE_OUTCOMES = setOf(SessionOutcome.STOPPED, SessionOutcome.FAILED)

    fun isResumableOutcome(outcome: SessionOutcome): Boolean = outcome in RESUMABLE_OUTCOMES

    fun isResumable(summary: SessionSummary, nowMillis: Long): Boolean =
        isResumableOutcome(summary.outcome) &&
            summary.elapsedSeconds > 0 &&
            summary.stepsCompleted < summary.stepCount &&
            isWithinWindow(summary.endedAt, nowMillis)

    fun isWithinWindow(endedAt: Long?, nowMillis: Long): Boolean {
        val ended = endedAt ?: return false
        return nowMillis - ended in 0..WINDOW_MILLIS
    }
}
