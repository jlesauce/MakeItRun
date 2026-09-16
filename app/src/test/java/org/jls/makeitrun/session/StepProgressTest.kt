package org.jls.makeitrun.session

import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.WorkoutStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepProgressTest {

    @Test
    fun `a timed step ignores the distance covered`() {
        val step = step(StepDuration.Time(60))

        assertFalse(StepProgress.isComplete(step, elapsedSeconds = 59, coveredMeters = 5_000))
        assertTrue(StepProgress.isComplete(step, elapsedSeconds = 60, coveredMeters = 0))
    }

    @Test
    fun `a distance step ignores the time elapsed`() {
        val step = step(StepDuration.Distance(400))

        assertFalse(StepProgress.isComplete(step, elapsedSeconds = 9_999, coveredMeters = 399))
        assertTrue(StepProgress.isComplete(step, elapsedSeconds = 1, coveredMeters = 400))
    }

    @Test
    fun `remaining time is expressed in seconds and never goes negative`() {
        val step = step(StepDuration.Time(60))

        assertEquals(
            StepRemaining.Seconds(15),
            StepProgress.remaining(step, elapsedSeconds = 45, coveredMeters = 0),
        )
        assertEquals(
            StepRemaining.Seconds(0),
            StepProgress.remaining(step, elapsedSeconds = 75, coveredMeters = 0),
        )
    }

    @Test
    fun `remaining distance is expressed in metres`() {
        val step = step(StepDuration.Distance(400))

        assertEquals(
            StepRemaining.Meters(150),
            StepProgress.remaining(step, elapsedSeconds = 0, coveredMeters = 250),
        )
    }

    @Test
    fun `progress fraction stays within its bounds`() {
        val step = step(StepDuration.Time(100))

        assertEquals(0.25f, StepProgress.fraction(step, 25, 0), DELTA)
        assertEquals(1f, StepProgress.fraction(step, 250, 0), DELTA)
    }

    private fun step(duration: StepDuration) = WorkoutStep(
        id = "step",
        type = StepType.RUN,
        duration = duration,
    )

    private companion object {
        const val DELTA = 0.0001f
    }
}
