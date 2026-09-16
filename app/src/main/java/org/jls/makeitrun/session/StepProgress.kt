package org.jls.makeitrun.session

import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.WorkoutStep

sealed interface StepRemaining {
    data class Seconds(val value: Int) : StepRemaining
    data class Meters(val value: Int) : StepRemaining
}

object StepProgress {

    fun remaining(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
    ): StepRemaining = when (val duration = step.duration) {
        is StepDuration.Time -> StepRemaining.Seconds(
            (duration.seconds - elapsedSeconds).coerceAtLeast(0)
        )

        is StepDuration.Distance -> StepRemaining.Meters(
            (duration.meters - coveredMeters).coerceAtLeast(0)
        )
    }

    fun isComplete(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
    ): Boolean = when (val duration = step.duration) {
        is StepDuration.Time -> elapsedSeconds >= duration.seconds
        is StepDuration.Distance -> coveredMeters >= duration.meters
    }

    fun fraction(
        step: WorkoutStep,
        elapsedSeconds: Int,
        coveredMeters: Int,
    ): Float = when (val duration = step.duration) {
        is StepDuration.Time ->
            if (duration.seconds <= 0) 1f
            else (elapsedSeconds.toFloat() / duration.seconds).coerceIn(0f, 1f)

        is StepDuration.Distance ->
            if (duration.meters <= 0) 1f
            else (coveredMeters.toFloat() / duration.meters).coerceIn(0f, 1f)
    }
}
