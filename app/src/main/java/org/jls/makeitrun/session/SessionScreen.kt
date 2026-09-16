package org.jls.makeitrun.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats

@Composable
fun SessionScreen(
    workoutId: Long,
    onFinished: () -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showPace by viewModel.showPace.collectAsStateWithLifecycle()

    LaunchedEffect(workoutId) { viewModel.startIfIdle(workoutId) }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val current = state) {
            is SessionState.CountingDown -> Countdown(current.secondsRemaining)

            is SessionState.Running -> RunningSession(
                progress = current.progress,
                showPace = showPace,
                isPaused = false,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onStop = {
                    viewModel.stop()
                    onFinished()
                },
            )

            is SessionState.Paused -> RunningSession(
                progress = current.progress,
                showPace = showPace,
                isPaused = true,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onStop = {
                    viewModel.stop()
                    onFinished()
                },
            )

            is SessionState.Finished -> Completion(
                title = stringResource(R.string.session_finished),
                detail = stringResource(
                    R.string.session_finished_summary,
                    Formats.distance(current.totalDistanceMeters),
                    Formats.duration(current.totalElapsedSeconds),
                ),
                onClose = {
                    viewModel.acknowledge()
                    onFinished()
                },
            )

            is SessionState.Failed -> Completion(
                title = stringResource(R.string.session_title),
                detail = current.reason,
                onClose = {
                    viewModel.acknowledge()
                    onFinished()
                },
            )

            SessionState.Idle -> Text(stringResource(R.string.session_preparing))
        }
    }
}

@Composable
private fun Countdown(seconds: Int) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.session_countdown),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(text = seconds.toString(), style = MaterialTheme.typography.displayLarge)
    }
}

@Composable
private fun RunningSession(
    progress: SessionProgress,
    showPace: Boolean,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Text(
        text = stringResource(
            R.string.session_step_progress,
            progress.stepIndex + 1,
            progress.stepCount,
        ),
        style = MaterialTheme.typography.labelLarge,
    )

    Text(
        text = WorkoutStepLabels.typeName(progress.currentStep.step.type),
        style = MaterialTheme.typography.headlineMedium,
    )

    progress.currentStep.repetition?.let { repetition ->
        Text(
            text = stringResource(
                R.string.session_repetition,
                repetition.current,
                repetition.total,
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    Text(
        text = when (val remaining = progress.remaining) {
            is StepRemaining.Seconds -> Formats.duration(remaining.value)
            is StepRemaining.Meters -> Formats.distance(remaining.value)
        },
        style = MaterialTheme.typography.displayMedium,
    )

    Text(
        text = stringResource(
            when (progress.remaining) {
                is StepRemaining.Seconds -> R.string.session_remaining_time
                is StepRemaining.Meters -> R.string.session_remaining_distance
            }
        ),
        style = MaterialTheme.typography.bodySmall,
    )

    LinearProgressIndicator(
        progress = { progress.stepFraction },
        modifier = Modifier.fillMaxWidth(),
    )

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ValueRow(
                label = stringResource(R.string.session_target),
                value = WorkoutStepLabels.target(progress.currentStep.step, showPace),
            )
            ValueRow(
                label = stringResource(R.string.session_actual),
                value = progress.liveData?.instantaneousSpeedKmh?.let(Formats::speed)
                    ?: stringResource(R.string.value_unavailable),
            )
            progress.nextStep?.let { next ->
                Text(
                    text = stringResource(
                        R.string.session_next_step,
                        "${WorkoutStepLabels.typeName(next.step.type)} · " +
                            WorkoutStepLabels.duration(next.step.duration),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    Text(
        text = stringResource(
            R.string.session_total,
            Formats.duration(progress.totalElapsedSeconds),
            Formats.distance(progress.totalDistanceMeters),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = if (isPaused) onResume else onPause,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(if (isPaused) R.string.session_resume else R.string.session_pause)
            )
        }
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            ),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.session_stop))
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun Completion(title: String, detail: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
        Text(
            text = detail,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onClose) {
            Text(stringResource(R.string.session_close))
        }
    }
}
