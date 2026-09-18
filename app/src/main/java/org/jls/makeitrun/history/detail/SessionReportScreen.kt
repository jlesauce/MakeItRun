package org.jls.makeitrun.history.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.history.SessionFormats
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.history.model.SessionReport
import org.jls.makeitrun.history.model.SessionStepReport
import org.jls.makeitrun.session.ZoneStatus
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionReportScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: SessionReportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var askForDeletion by remember { mutableStateOf(false) }

    LaunchedEffect(sessionId) { viewModel.load(sessionId) }

    if (askForDeletion) {
        DeleteDialog(
            onDismiss = { askForDeletion = false },
            onConfirm = {
                askForDeletion = false
                viewModel.delete(onBack)
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.report?.summary?.workoutName
                            ?: stringResource(R.string.session_report_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (state.report != null) {
                        IconButton(onClick = { askForDeletion = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription =
                                    stringResource(R.string.session_report_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        val report = state.report
        if (report == null) {
            if (state.isLoaded) {
                Text(
                    text = stringResource(R.string.session_report_not_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(innerPadding).padding(16.dp),
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            item { HeaderCard(report) }
            item { ChartCard(report) }

            item {
                Text(
                    text = stringResource(R.string.session_report_steps),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            items(report.steps, key = { it.index }) { step ->
                StepRow(step = step, showPace = state.showPaceInsteadOfSpeed)
            }

            item { TreadmillCard(report) }
        }
    }
}

@Composable
private fun HeaderCard(report: SessionReport) {
    val summary = report.summary
    val secondsInZone = report.samples.count { it.zone == ZoneStatus.IN_ZONE }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = SessionFormats.dateTime(summary.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(
                    R.string.history_metrics,
                    Formats.duration(summary.elapsedSeconds),
                    Formats.distance(summary.distanceMeters),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )

            if (summary.outcome != SessionOutcome.COMPLETED) {
                Text(
                    text = report.failureReason
                        ?.let { stringResource(R.string.session_report_failure, it) }
                        ?: stringResource(
                            R.string.history_steps_done,
                            summary.stepsCompleted,
                            summary.stepCount,
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            summary.averageBpm?.let {
                MetricLine(
                    label = stringResource(R.string.session_report_average_heart_rate),
                    value = stringResource(R.string.heart_rate_bpm, it),
                )
            }
            summary.maximumBpm?.let {
                MetricLine(
                    label = stringResource(R.string.session_report_maximum_heart_rate),
                    value = stringResource(R.string.heart_rate_bpm, it),
                )
            }
            summary.zoneShare?.let { share ->
                MetricLine(
                    label = stringResource(R.string.session_report_time_in_zone),
                    value = stringResource(
                        R.string.session_report_time_in_zone_value,
                        Formats.duration(secondsInZone),
                        (share * 100).roundToInt(),
                    ),
                )
            }
            report.energyKcal?.let {
                MetricLine(
                    label = stringResource(R.string.session_report_energy),
                    value = stringResource(R.string.session_report_energy_value, it),
                )
            }
        }
    }
}

@Composable
private fun ChartCard(report: SessionReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.session_report_chart),
                style = MaterialTheme.typography.titleSmall,
            )
            if (report.samples.isEmpty()) {
                Text(
                    text = stringResource(R.string.session_report_chart_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SessionChart(samples = report.samples, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun StepRow(step: SessionStepReport, showPace: Boolean) {
    val planned = step.planned

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
                    .background(
                        planned
                            ?.let { WorkoutStepLabels.typeColor(it.step.type) }
                            ?: MaterialTheme.colorScheme.outlineVariant
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "${step.index + 1}. " + (
                        planned
                            ?.let { WorkoutStepLabels.typeName(it.step.type) }
                            ?: stringResource(R.string.value_unavailable)
                        ),
                    style = MaterialTheme.typography.titleSmall,
                )
                planned?.let {
                    Text(
                        text = stringResource(
                            R.string.session_report_step_planned,
                            WorkoutStepLabels.target(it.step, showPace),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.session_report_step_actual,
                        step.averageSpeedKmh
                            ?.let { Formats.speed(it) }
                            ?: stringResource(R.string.value_unavailable),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (step.averageBpm != null && step.maximumBpm != null) {
                    Text(
                        text = stringResource(
                            R.string.session_report_step_heart_rate,
                            step.averageBpm,
                            step.maximumBpm,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                step.zoneShare?.let { share ->
                    Text(
                        text = stringResource(
                            R.string.history_zone_share,
                            (share * 100).roundToInt(),
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
                    text = Formats.duration(step.elapsedSeconds),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = Formats.distance(step.distanceMeters),
                    style = MaterialTheme.typography.bodySmall,
                )
                step.inclinationPercent
                    ?.takeIf { it > 0.0 }
                    ?.let {
                        Text(
                            text = Formats.inclination(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
            }
        }
    }
}

@Composable
private fun TreadmillCard(report: SessionReport) {
    val distance = report.treadmillDistanceMeters
    val time = report.treadmillElapsedSeconds
    if (distance == null && time == null) return

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.session_report_treadmill),
                style = MaterialTheme.typography.titleSmall,
            )
            distance?.let {
                Text(
                    text = stringResource(
                        R.string.session_report_treadmill_distance,
                        Formats.distance(it),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            time?.let {
                Text(
                    text = stringResource(
                        R.string.session_report_treadmill_time,
                        Formats.duration(it),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.session_report_treadmill_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.session_report_delete_title)) },
        text = { Text(stringResource(R.string.session_report_delete_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.session_report_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.session_report_delete_cancel))
            }
        },
    )
}
