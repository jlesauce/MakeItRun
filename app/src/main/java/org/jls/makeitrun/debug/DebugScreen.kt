package org.jls.makeitrun.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.ftms.TreadmillData
import org.jls.makeitrun.ui.MakeItRunTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MakeItRunTopBar(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout)
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(
                top = 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item { CapabilitiesCard(capabilities = state.capabilities) }

            state.lastCommandResult?.let { result ->
                item {
                    Text(
                        text = commandResultMessage(result),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            val connected = state.connected
            if (connected == null) {
                item {
                    Text(
                        text = stringResource(R.string.debug_requires_connection),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item { Dimmed(enabled = connected != null) { MetricsCard(data = state.data) } }

            item {
                val canControl = connected?.canBeControlled == true
                Dimmed(enabled = canControl) {
                    ControlCard(
                        state = state,
                        enabled = canControl,
                        onTargetSpeedChange = viewModel::setTargetSpeed,
                        onTargetInclinationChange = viewModel::setTargetInclination,
                        onRequestControl = viewModel::requestControl,
                        onStart = viewModel::startBelt,
                        onStop = viewModel::stopBelt,
                        onApplySpeed = viewModel::applyTargetSpeed,
                        onApplyInclination = viewModel::applyTargetInclination,
                    )
                }
            }

            item {
                Dimmed(enabled = state.lastRawFrame != null) {
                    RawFrameCard(frame = state.lastRawFrame)
                }
            }
        }
    }
}

@Composable
private fun Dimmed(enabled: Boolean, content: @Composable () -> Unit) {
    Box(modifier = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)) {
        content()
    }
}

@Composable
private fun commandResultMessage(result: DebugCommandResult): String {
    val command = stringResource(result.command.labelResId)
    return when {
        !result.answered -> stringResource(R.string.debug_command_no_answer, command)
        result.refusalReason == null -> stringResource(R.string.debug_command_accepted, command)
        else -> stringResource(R.string.debug_command_refused, command, result.refusalReason)
    }
}

@Composable
private fun CapabilitiesCard(capabilities: TreadmillCapabilities?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.capabilities_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            if (capabilities == null) {
                Text(
                    text = stringResource(R.string.capabilities_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            capabilities.speedRange?.let { range ->
                Text(
                    text = stringResource(
                        R.string.capabilities_speed,
                        "%.1f".format(range.minimum),
                        "%.1f".format(range.maximum),
                        "%.2f".format(range.increment),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val inclination = capabilities.inclinationRange
            Text(
                text = if (inclination != null && capabilities.canSetTargetInclination) {
                    stringResource(
                        R.string.capabilities_inclination,
                        "%.1f".format(inclination.minimum),
                        "%.1f".format(inclination.maximum),
                    )
                } else {
                    stringResource(R.string.capabilities_no_inclination)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MetricsCard(data: TreadmillData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.metrics_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            if (data == null) {
                Text(
                    text = stringResource(R.string.metric_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            MetricRow(R.string.metric_speed, data.instantaneousSpeedKmh.formatSpeed())
            MetricRow(R.string.metric_pace, data.paceSecondsPerKm.formatPace())
            MetricRow(R.string.metric_distance, data.totalDistanceMeters.formatDistance())
            MetricRow(R.string.metric_time, data.elapsedTimeSeconds.formatDuration())
            MetricRow(R.string.metric_inclination, data.inclinationPercent.formatInclination())
            MetricRow(R.string.metric_heart_rate, data.heartRateBpm.formatHeartRate())
            MetricRow(R.string.metric_energy, data.totalEnergyKcal.formatEnergy())
        }
    }
}

@Composable
private fun MetricRow(labelResId: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(labelResId), style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ControlCard(
    state: DebugUiState,
    enabled: Boolean,
    onTargetSpeedChange: (Double) -> Unit,
    onTargetInclinationChange: (Double) -> Unit,
    onRequestControl: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onApplySpeed: () -> Unit,
    onApplyInclination: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.control_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            Button(
                onClick = onRequestControl,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.control_request))
            }
            Text(
                text = stringResource(R.string.control_request_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.control_start))
                }
                Button(onClick = onStop, enabled = enabled, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.control_stop))
                }
            }

            HorizontalDivider()

            val speedRange = state.capabilities?.speedRange
            SliderRow(
                label = stringResource(
                    R.string.control_target_speed,
                    "%.1f".format(state.targetSpeedKmh),
                ),
                value = state.targetSpeedKmh,
                minimum = speedRange?.minimum ?: DEFAULT_MIN_SPEED_KMH,
                maximum = speedRange?.maximum ?: DEFAULT_MAX_SPEED_KMH,
                actionLabel = stringResource(R.string.control_apply_speed),
                enabled = enabled,
                onValueChange = onTargetSpeedChange,
                onApply = onApplySpeed,
            )

            HorizontalDivider()

            val inclinationRange = state.capabilities?.inclinationRange
            SliderRow(
                label = stringResource(
                    R.string.control_target_inclination,
                    "%.1f".format(state.targetInclinationPercent),
                ),
                value = state.targetInclinationPercent,
                minimum = inclinationRange?.minimum ?: DEFAULT_MIN_INCLINATION_PERCENT,
                maximum = inclinationRange?.maximum ?: DEFAULT_MAX_INCLINATION_PERCENT,
                actionLabel = stringResource(R.string.control_apply_inclination),
                enabled = enabled,
                onValueChange = onTargetInclinationChange,
                onApply = onApplyInclination,
            )

            if (state.capabilities?.canSetTargetInclination != true) {
                Text(
                    text = stringResource(R.string.control_inclination_undeclared),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Double,
    minimum: Double,
    maximum: Double,
    actionLabel: String,
    enabled: Boolean,
    onValueChange: (Double) -> Unit,
    onApply: () -> Unit,
) {
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value.toFloat().coerceIn(minimum.toFloat(), maximum.toFloat()),
        onValueChange = { onValueChange(it.toDouble()) },
        valueRange = minimum.toFloat()..maximum.toFloat(),
        enabled = enabled,
    )
    OutlinedButton(onClick = onApply, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(actionLabel)
    }
}

@Composable
private fun RawFrameCard(frame: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.raw_frame_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = frame ?: stringResource(R.string.value_unavailable),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.raw_frame_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Double?.formatSpeed(): String =
    this?.let { "%.1f km/h".format(it) } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatPace(): String =
    this?.let { "%d:%02d /km".format(it / 60, it % 60) }
        ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatDistance(): String = when {
    this == null -> stringResource(R.string.value_unavailable)
    this < 1_000 -> "$this m"
    else -> "%.2f km".format(this / 1_000.0)
}

@Composable
private fun Int?.formatDuration(): String =
    this?.let { "%02d:%02d".format(it / 60, it % 60) }
        ?: stringResource(R.string.value_unavailable)

@Composable
private fun Double?.formatInclination(): String =
    this?.let { "%.1f %%".format(it) } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatHeartRate(): String =
    this?.let { "$it bpm" } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatEnergy(): String =
    this?.let { "$it kcal" } ?: stringResource(R.string.value_unavailable)

private const val DISABLED_ALPHA = 0.4f
private const val DEFAULT_MIN_SPEED_KMH = 1.0
private const val DEFAULT_MAX_SPEED_KMH = 16.0
private const val DEFAULT_MIN_INCLINATION_PERCENT = 0.0
private const val DEFAULT_MAX_INCLINATION_PERCENT = 15.0
