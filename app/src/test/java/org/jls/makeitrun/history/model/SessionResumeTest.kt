package org.jls.makeitrun.history.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionResumeTest {

    @Test
    fun `a session stopped a few minutes ago can be resumed`() {
        assertTrue(SessionResume.isResumable(summary(endedAt = NOW - 5 * MINUTE), NOW))
    }

    @Test
    fun `a session stopped beyond the window can no longer be resumed`() {
        assertFalse(SessionResume.isResumable(summary(endedAt = NOW - 31 * MINUTE), NOW))
    }

    @Test
    fun `a failed session can be resumed as well`() {
        val failed = summary(endedAt = NOW - MINUTE, outcome = SessionOutcome.FAILED)
        assertTrue(SessionResume.isResumable(failed, NOW))
    }

    @Test
    fun `a completed session is never resumable`() {
        val completed = summary(
            endedAt = NOW - MINUTE,
            outcome = SessionOutcome.COMPLETED,
            stepsCompleted = 6,
        )
        assertFalse(SessionResume.isResumable(completed, NOW))
    }

    @Test
    fun `a session that went through every step is not resumable`() {
        assertFalse(SessionResume.isResumable(summary(stepsCompleted = 6), NOW))
    }

    @Test
    fun `a session without any recorded second is not resumable`() {
        assertFalse(SessionResume.isResumable(summary(elapsedSeconds = 0), NOW))
    }

    @Test
    fun `a session still running is not resumable`() {
        assertFalse(SessionResume.isResumable(summary(endedAt = null), NOW))
    }

    private fun summary(
        endedAt: Long? = NOW - MINUTE,
        outcome: SessionOutcome = SessionOutcome.STOPPED,
        elapsedSeconds: Int = 600,
        stepsCompleted: Int = 3,
    ) = SessionSummary(
        id = 1L,
        workoutName = "Reprise",
        startedAt = NOW - 40 * MINUTE,
        endedAt = endedAt,
        outcome = outcome,
        elapsedSeconds = elapsedSeconds,
        distanceMeters = 1_800,
        stepsCompleted = stepsCompleted,
        stepCount = 6,
        averageBpm = 148,
        maximumBpm = 162,
        zoneShare = 0.8f,
    )

    private companion object {
        const val MINUTE = 60_000L
        const val NOW = 1_800_000_000_000L
    }
}
