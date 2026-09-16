package org.jls.makeitrun.workout.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutPlanTest {

    @Test
    fun `a repeat block is unrolled into one step per repetition`() {
        val elements = listOf(
            step("warmup", StepType.WARM_UP, StepDuration.Time(600), 8.0),
            RepeatBlock(
                id = "block",
                repetitions = 3,
                steps = listOf(
                    step("fast", StepType.RUN, StepDuration.Distance(400), 15.0),
                    step("easy", StepType.RECOVER, StepDuration.Distance(200), 7.0),
                ),
            ),
        )

        val resolved = WorkoutPlan.flatten(elements)

        assertEquals(7, resolved.size)
        assertEquals("warmup", resolved[0].step.id)
        assertEquals("fast", resolved[1].step.id)
        assertEquals("easy", resolved[2].step.id)
        assertEquals("fast", resolved[5].step.id)
    }

    @Test
    fun `each unrolled step carries its repetition number`() {
        val elements = listOf(
            RepeatBlock(
                id = "block",
                repetitions = 4,
                steps = listOf(step("fast", StepType.RUN, StepDuration.Distance(400), 15.0)),
            ),
        )

        val resolved = WorkoutPlan.flatten(elements)

        assertEquals(Repetition(current = 1, total = 4), resolved.first().repetition)
        assertEquals(Repetition(current = 4, total = 4), resolved.last().repetition)
    }

    @Test
    fun `a standalone step has no repetition number`() {
        val resolved = WorkoutPlan.flatten(
            listOf(step("run", StepType.RUN, StepDuration.Time(60), 10.0))
        )

        assertEquals(null, resolved.single().repetition)
    }

    @Test
    fun `distance is estimated from the duration and the target speed`() {
        val summary = WorkoutPlan.summarize(
            listOf(step("run", StepType.RUN, StepDuration.Time(600), 12.0))
        )

        assertEquals(600, summary.durationSeconds)
        assertEquals(2000, summary.distanceMeters)
        assertTrue(summary.isComplete)
    }

    @Test
    fun `duration is estimated from the distance and the target speed`() {
        val summary = WorkoutPlan.summarize(
            listOf(step("fast", StepType.RUN, StepDuration.Distance(400), 12.0))
        )

        assertEquals(120, summary.durationSeconds)
        assertEquals(400, summary.distanceMeters)
    }

    @Test
    fun `a step without target speed makes the estimate incomplete`() {
        val summary = WorkoutPlan.summarize(
            listOf(step("free", StepType.RUN, StepDuration.Time(300), null))
        )

        assertEquals(300, summary.durationSeconds)
        assertEquals(0, summary.distanceMeters)
        assertFalse(summary.isComplete)
    }

    @Test
    fun `totals cover every repetition of a block`() {
        val summary = WorkoutPlan.summarize(
            listOf(
                RepeatBlock(
                    id = "block",
                    repetitions = 5,
                    steps = listOf(step("fast", StepType.RUN, StepDuration.Distance(400), 12.0)),
                )
            )
        )

        assertEquals(5, summary.stepCount)
        assertEquals(2000, summary.distanceMeters)
        assertEquals(600, summary.durationSeconds)
    }

    private fun step(
        id: String,
        type: StepType,
        duration: StepDuration,
        speedKmh: Double?,
    ) = WorkoutStep(id = id, type = type, duration = duration, targetSpeedKmh = speedKmh)
}
