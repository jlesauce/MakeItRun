package org.jls.makeitrun.workout.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats
import org.jls.makeitrun.workout.model.ResolvedStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    if (confirmingDelete) {
        DeleteWorkoutDialog(
            onConfirm = {
                confirmingDelete = false
                viewModel.delete(onBack)
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.workout?.name ?: stringResource(R.string.workout_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(workoutId) }) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.workout_detail_edit),
                        )
                    }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.workout_detail_delete),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            item {
                StartBar(state = state, onStart = { onStart(workoutId) })
            }

            state.summary?.let { summary ->
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = if (summary.hasDistance) {
                                    stringResource(
                                        R.string.workout_summary_line,
                                        Formats.duration(summary.durationSeconds),
                                        Formats.distance(summary.distanceMeters),
                                    )
                                } else {
                                    Formats.duration(summary.durationSeconds)
                                },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.workout_step_count,
                                    summary.stepCount,
                                    summary.stepCount,
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            itemsIndexed(state.steps) { index, resolved ->
                StepRow(
                    index = index,
                    resolved = resolved,
                    showPace = state.showPaceInsteadOfSpeed,
                )
            }
        }
    }
}

@Composable
private fun StepRow(index: Int, resolved: ResolvedStep, showPace: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp, horizontal = 12.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(WorkoutStepLabels.typeColor(resolved.step.type))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${index + 1}. ${WorkoutStepLabels.typeName(resolved.step.type)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                resolved.repetition?.let { repetition ->
                    Text(
                        text = stringResource(
                            R.string.session_repetition,
                            repetition.current,
                            repetition.total,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            Column(
                modifier = Modifier.padding(end = 16.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = WorkoutStepLabels.duration(resolved.step.duration),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = WorkoutStepLabels.target(resolved.step, showPace),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StartBar(state: WorkoutDetailUiState, onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStart,
            enabled = state.canStart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.workout_detail_start))
        }

        val blockingReason = when {
            !state.isConnected -> R.string.workout_detail_needs_connection
            state.needsHeartRateSensor && !state.isHeartRateSensorConnected ->
                R.string.workout_detail_needs_heart_rate

            else -> null
        }
        blockingReason?.let {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DeleteWorkoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workout_delete_title)) },
        text = { Text(stringResource(R.string.workout_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.workout_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.workout_delete_cancel))
            }
        },
    )
}
