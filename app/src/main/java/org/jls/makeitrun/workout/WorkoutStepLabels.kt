package org.jls.makeitrun.workout

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import org.jls.makeitrun.R
import org.jls.makeitrun.workout.model.Formats
import org.jls.makeitrun.workout.model.StepDuration
import org.jls.makeitrun.workout.model.StepType
import org.jls.makeitrun.workout.model.WorkoutStep

object WorkoutStepLabels {

    @Composable
    @ReadOnlyComposable
    fun typeName(type: StepType): String = stringResource(
        when (type) {
            StepType.WARM_UP -> R.string.step_type_warm_up
            StepType.RUN -> R.string.step_type_run
            StepType.RECOVER -> R.string.step_type_recover
            StepType.COOL_DOWN -> R.string.step_type_cool_down
        }
    )

    @Composable
    fun typeColor(type: StepType): Color = when (type) {
        StepType.WARM_UP -> Color(0xFF7CB342)
        StepType.RUN -> MaterialTheme.colorScheme.primary
        StepType.RECOVER -> Color(0xFF42A5F5)
        StepType.COOL_DOWN -> Color(0xFF8D6E63)
    }

    @Composable
    @ReadOnlyComposable
    fun duration(duration: StepDuration): String = when (duration) {
        is StepDuration.Time -> Formats.duration(duration.seconds)
        is StepDuration.Distance -> Formats.distance(duration.meters)
    }

    @Composable
    @ReadOnlyComposable
    fun target(step: WorkoutStep, showPace: Boolean): String {
        val pace = step.heartRateTarget
            ?.let { stringResource(R.string.editor_heart_rate_zone, it.minBpm, it.maxBpm) }
            ?: Formats.target(
                speedKmh = step.targetSpeedKmh,
                showPace = showPace,
                freeLabel = stringResource(R.string.session_free_pace),
            )

        val inclination = step.inclinationPercent ?: return pace
        return stringResource(
            R.string.step_target_with_inclination,
            pace,
            Formats.inclination(inclination),
        )
    }
}
