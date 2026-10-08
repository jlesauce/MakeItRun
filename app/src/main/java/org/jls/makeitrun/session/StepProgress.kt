package org.jls.makeitrun.session

import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.WorkoutStep

sealed interface StepRemaining {
    data class Seconds(val value: Int) : StepRemaining
    data class Meters(val value: Int) : StepRemaining
}

object StepProgress {

    const val EXTENSION_SECONDS = 60

    fun canExtend(step: WorkoutStep): Boolean = step.duration is StepDuration.Time

    fun remaining(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
        extraSeconds: Int = 0,
    ): StepRemaining = when (val duration = step.duration) {
        is StepDuration.Time -> StepRemaining.Seconds(
            (duration.seconds + extraSeconds - elapsedSeconds).coerceAtLeast(0)
        )

        is StepDuration.Distance -> StepRemaining.Meters(
            (duration.meters - coveredMeters).coerceAtLeast(0)
        )
    }

    fun isComplete(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
        extraSeconds: Int = 0,
    ): Boolean = when (val duration = step.duration) {
        is StepDuration.Time -> elapsedSeconds >= duration.seconds + extraSeconds
        is StepDuration.Distance -> coveredMeters >= duration.meters
    }

    fun fraction(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
        extraSeconds: Int = 0,
    ): Float = when (val duration = step.duration) {
        is StepDuration.Time -> {
            val total = duration.seconds + extraSeconds
            if (total <= 0) 1f
            else (elapsedSeconds.toFloat() / total).coerceIn(0f, 1f)
        }

        is StepDuration.Distance ->
            if (duration.meters <= 0) 1f
            else (coveredMeters.toFloat() / duration.meters).coerceIn(0f, 1f)
    }
}
