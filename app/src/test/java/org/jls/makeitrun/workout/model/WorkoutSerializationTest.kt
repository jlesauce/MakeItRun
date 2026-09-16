package org.jls.makeitrun.workout.model

import org.jls.makeitrun.di.DataModule
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSerializationTest {

    private val json = DataModule.provideJson()

    @Test
    fun `a workout mixing steps and repeat blocks survives a round trip`() {
        val elements = listOf<WorkoutElement>(
            WorkoutStep(
                id = "warmup",
                type = StepType.WARM_UP,
                duration = StepDuration.Time(600),
                targetSpeedKmh = 8.0,
            ),
            RepeatBlock(
                id = "block",
                repetitions = 6,
                steps = listOf(
                    WorkoutStep(
                        id = "fast",
                        type = StepType.RUN,
                        duration = StepDuration.Distance(400),
                        targetSpeedKmh = 15.0,
                        inclinationPercent = 2.0,
                    ),
                    WorkoutStep(
                        id = "easy",
                        type = StepType.RECOVER,
                        duration = StepDuration.Time(90),
                        targetSpeedKmh = null,
                    ),
                ),
            ),
        )

        val encoded = json.encodeToString(elements)
        val decoded = json.decodeFromString<List<WorkoutElement>>(encoded)

        assertEquals(elements, decoded)
    }

    @Test
    fun `the step type is preserved alongside the polymorphic discriminator`() {
        val step: WorkoutElement = WorkoutStep(
            id = "run",
            type = StepType.COOL_DOWN,
            duration = StepDuration.Time(300),
        )

        val decoded = json.decodeFromString<WorkoutElement>(json.encodeToString(step))

        assertEquals(StepType.COOL_DOWN, (decoded as WorkoutStep).type)
    }
}
