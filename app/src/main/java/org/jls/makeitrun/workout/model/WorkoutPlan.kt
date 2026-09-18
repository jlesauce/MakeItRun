package org.jls.makeitrun.workout.model

import kotlin.math.roundToInt

data class Repetition(val current: Int, val total: Int)

data class ResolvedStep(
    val step: WorkoutStep,
    val position: Int,
    val repetition: Repetition?,
)

data class WorkoutSummary(
    val stepCount: Int,
    val durationSeconds: Int,
    val distanceMeters: Int,
    val isComplete: Boolean,
) {

    val hasDistance: Boolean
        get() = isComplete && distanceMeters > 0
}

object WorkoutPlan {

    private const val SECONDS_PER_HOUR = 3600.0
    private const val METRES_PER_KM = 1000.0

    fun flatten(elements: List<WorkoutElement>): List<ResolvedStep> {
        val resolved = mutableListOf<ResolvedStep>()

        elements.forEach { element ->
            when (element) {
                is WorkoutStep -> resolved += ResolvedStep(
                    step = element,
                    position = resolved.size,
                    repetition = null,
                )

                is RepeatBlock -> repeat(element.repetitions) { iteration ->
                    element.stepsForRepetition(iteration).forEach { step ->
                        resolved += ResolvedStep(
                            step = step,
                            position = resolved.size,
                            repetition = Repetition(
                                current = iteration + 1,
                                total = element.repetitions,
                            ),
                        )
                    }
                }
            }
        }

        return resolved
    }

    fun summarize(elements: List<WorkoutElement>): WorkoutSummary {
        val steps = flatten(elements)
        var seconds = 0
        var metres = 0
        var isComplete = true

        steps.forEach { resolved ->
            val speed = resolved.step.targetSpeedKmh
            when (val duration = resolved.step.duration) {
                is StepDuration.Time -> {
                    seconds += duration.seconds
                    if (speed == null) {
                        isComplete = false
                    } else {
                        metres += (speed * METRES_PER_KM * duration.seconds / SECONDS_PER_HOUR)
                            .roundToInt()
                    }
                }

                is StepDuration.Distance -> {
                    metres += duration.meters
                    if (speed == null) {
                        isComplete = false
                    } else {
                        seconds += (duration.meters / METRES_PER_KM * SECONDS_PER_HOUR / speed)
                            .roundToInt()
                    }
                }
            }
        }

        return WorkoutSummary(
            stepCount = steps.size,
            durationSeconds = seconds,
            distanceMeters = metres,
            isComplete = isComplete,
        )
    }
}
