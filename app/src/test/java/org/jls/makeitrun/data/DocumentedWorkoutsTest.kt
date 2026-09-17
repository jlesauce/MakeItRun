package org.jls.makeitrun.data

import kotlinx.serialization.json.Json
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.WorkoutStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DocumentedWorkoutsTest {

    @Test
    fun `the vma test ramps by half a kilometre per hour then leans on the inclination`() {
        val steps = stepsOf("test-vma.json")
        val stages = steps.filter { it.type == StepType.RUN }

        assertEquals(23, steps.size)
        assertEquals(21, stages.size)
        assertTrue(stages.all { it.duration == StepDuration.Time(60) })

        val speeds = stages.map { it.targetSpeedKmh }.distinct()
        assertEquals(8.5, speeds.first())
        assertEquals(16.0, speeds.last())
        assertEquals(16, speeds.size)

        val leaning = stages.filter { it.targetSpeedKmh == 16.0 }
        assertEquals(
            listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0),
            leaning.map { it.inclinationPercent },
        )
    }

    @Test
    fun `the endurance run hands its long block over to the heart rate zone`() {
        val steps = stepsOf("endurance-fondamentale.json")
        val block = steps.single { it.isHeartRateDriven }

        assertEquals(3, steps.size)
        assertEquals(StepDuration.Time(1800), block.duration)
        assertEquals(124, block.heartRateTarget?.minBpm)
        assertEquals(143, block.heartRateTarget?.maxBpm)
        assertEquals(RegulationResponsiveness.GENTLE, block.regulationResponsiveness)
    }

    private fun stepsOf(fileName: String): List<WorkoutStep> {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "kind"
        }
        val file = File(DOCUMENTATION_FOLDER, fileName)
        assertTrue("${file.absolutePath} is missing", file.exists())

        val backup = json.decodeFromString<WorkoutBackup>(file.readText())
        assertEquals(WorkoutBackup.CURRENT_FORMAT, backup.format)

        return backup.workouts.single().elements.filterIsInstance<WorkoutStep>()
    }

    private companion object {
        const val DOCUMENTATION_FOLDER = "../docs/workouts"
    }
}
