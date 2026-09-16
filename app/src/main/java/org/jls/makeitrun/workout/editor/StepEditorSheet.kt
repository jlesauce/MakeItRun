package org.jls.makeitrun.workout.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jls.makeitrun.R
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats
import org.jls.makeitrun.workout.model.HeartRateTarget
import org.jls.makeitrun.workout.model.Pace
import org.jls.makeitrun.workout.model.StepType
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEditorSheet(
    draft: StepDraft,
    capabilities: TreadmillCapabilities,
    showPace: Boolean,
    onDraftChange: (StepDraft) -> Unit,
    onToggleUnit: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Section(stringResource(R.string.editor_step_type)) {
                ChipRow {
                    StepType.entries.forEach { type ->
                        FilterChip(
                            selected = draft.type == type,
                            onClick = { onDraftChange(draft.copy(type = type)) },
                            label = { Text(WorkoutStepLabels.typeName(type)) },
                        )
                    }
                }
            }

            Section(stringResource(R.string.editor_duration_mode)) {
                ChipRow {
                    FilterChip(
                        selected = !draft.byDistance,
                        onClick = { onDraftChange(draft.copy(byDistance = false)) },
                        label = { Text(stringResource(R.string.editor_duration_time)) },
                    )
                    FilterChip(
                        selected = draft.byDistance,
                        onClick = { onDraftChange(draft.copy(byDistance = true)) },
                        label = { Text(stringResource(R.string.editor_duration_distance)) },
                    )
                }

                if (draft.byDistance) {
                    NumberField(
                        value = draft.meters,
                        label = stringResource(R.string.editor_metres),
                        onValueChange = { onDraftChange(draft.copy(meters = it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = draft.minutes,
                            label = stringResource(R.string.editor_minutes),
                            onValueChange = { onDraftChange(draft.copy(minutes = it)) },
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = draft.seconds,
                            label = stringResource(R.string.editor_seconds),
                            onValueChange = { onDraftChange(draft.copy(seconds = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Section(
                title = stringResource(
                    if (showPace) R.string.editor_target_title_pace
                    else R.string.editor_target_title_speed
                ),
            ) {
                Text(
                    text = stringResource(R.string.editor_target_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )

                ChipRow {
                    FilterChip(
                        selected = draft.targetMode == StepTargetMode.SPEED,
                        onClick = { onDraftChange(draft.copy(targetMode = StepTargetMode.SPEED)) },
                        label = { Text(stringResource(R.string.editor_target_fixed)) },
                    )
                    FilterChip(
                        selected = draft.targetMode == StepTargetMode.HEART_RATE,
                        onClick = {
                            onDraftChange(draft.copy(targetMode = StepTargetMode.HEART_RATE))
                        },
                        label = { Text(stringResource(R.string.editor_target_heart_rate)) },
                    )
                    FilterChip(
                        selected = draft.targetMode == StepTargetMode.FREE,
                        onClick = { onDraftChange(draft.copy(targetMode = StepTargetMode.FREE)) },
                        label = { Text(stringResource(R.string.editor_target_free)) },
                    )
                }

                when (draft.targetMode) {
                    StepTargetMode.SPEED -> TargetPicker(
                        draft = draft,
                        capabilities = capabilities,
                        showPace = showPace,
                        onDraftChange = onDraftChange,
                        onToggleUnit = onToggleUnit,
                    )

                    StepTargetMode.HEART_RATE -> HeartRateZonePicker(
                        draft = draft,
                        capabilities = capabilities,
                        onDraftChange = onDraftChange,
                    )

                    StepTargetMode.FREE -> Unit
                }
            }

            capabilities.inclinationRange
                ?.takeIf { capabilities.canSetTargetInclination }
                ?.let { range ->
                    Section(stringResource(R.string.editor_inclination)) {
                        InclinationPicker(
                            draft = draft,
                            range = range,
                            onDraftChange = onDraftChange,
                        )
                    }
                }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_cancel))
                }
                Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.editor_done))
                }
            }
        }
    }
}

@Composable
private fun TargetPicker(
    draft: StepDraft,
    capabilities: TreadmillCapabilities,
    showPace: Boolean,
    onDraftChange: (StepDraft) -> Unit,
    onToggleUnit: () -> Unit,
) {
    val speedRange = capabilities.speedRange ?: TreadmillCapabilities.UNKNOWN.speedRange!!
    var isTyping by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = { isTyping = true }) {
            Text(
                text = Formats.target(draft.targetSpeedKmh, showPace, freeLabel = ""),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        TextButton(onClick = onToggleUnit) {
            Text(
                stringResource(
                    if (showPace) R.string.editor_unit_speed else R.string.editor_unit_pace
                )
            )
        }
    }

    if (showPace) {
        val fastest = maxOf(
            Pace.secondsPerKmFrom(speedRange.maximum) ?: FASTEST_PACE_SECONDS,
            FASTEST_PACE_SECONDS,
        )
        val slowest = minOf(
            Pace.secondsPerKmFrom(speedRange.minimum) ?: SLOWEST_PACE_SECONDS,
            SLOWEST_PACE_SECONDS,
        )
        val current = Pace.secondsPerKmFrom(draft.targetSpeedKmh) ?: fastest

        Slider(
            value = current.toFloat().coerceIn(fastest.toFloat(), slowest.toFloat()),
            onValueChange = { seconds ->
                val rounded = (seconds / PACE_STEP_SECONDS).roundToInt() * PACE_STEP_SECONDS
                val speed = Pace.speedKmhFrom(rounded) ?: return@Slider
                onDraftChange(draft.copy(targetSpeedKmh = speedRange.coerce(speed)))
            },
            valueRange = fastest.toFloat()..slowest.toFloat(),
        )
    } else {
        Slider(
            value = draft.targetSpeedKmh.toFloat()
                .coerceIn(speedRange.minimum.toFloat(), speedRange.maximum.toFloat()),
            onValueChange = {
                onDraftChange(draft.copy(targetSpeedKmh = speedRange.coerce(it.toDouble())))
            },
            valueRange = speedRange.minimum.toFloat()..speedRange.maximum.toFloat(),
        )
    }

    Text(
        text = stringResource(R.string.editor_target_manual_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        text = stringResource(
            R.string.editor_speed_bounds,
            "%.1f".format(speedRange.minimum),
            "%.1f".format(speedRange.maximum),
        ),
        style = MaterialTheme.typography.bodySmall,
    )

    if (isTyping) {
        TargetInputDialog(
            speedKmh = draft.targetSpeedKmh,
            speedRange = speedRange,
            showPace = showPace,
            onDismiss = { isTyping = false },
            onConfirm = { speed ->
                onDraftChange(draft.copy(targetSpeedKmh = speed))
                isTyping = false
            },
        )
    }
}

@Composable
private fun TargetInputDialog(
    speedKmh: Double,
    speedRange: TreadmillCapabilities.ValueRange,
    showPace: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    val initialPace = Pace.secondsPerKmFrom(speedKmh) ?: 0
    var minutes by remember { mutableStateOf((initialPace / 60).toString()) }
    var seconds by remember { mutableStateOf("%02d".format(initialPace % 60)) }
    var speed by remember { mutableStateOf("%.1f".format(speedKmh)) }

    val resolved: Double? = if (showPace) {
        val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        Pace.speedKmhFrom(total)
    } else {
        speed.replace(',', '.').toDoubleOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (showPace) R.string.editor_target_manual_pace
                    else R.string.editor_target_manual_speed
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showPace) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            value = minutes,
                            label = stringResource(R.string.editor_minutes),
                            onValueChange = { minutes = it },
                            modifier = Modifier.weight(1f),
                        )
                        NumberField(
                            value = seconds,
                            label = stringResource(R.string.editor_seconds),
                            onValueChange = { seconds = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = speed,
                        onValueChange = { input ->
                            speed = input.filter { it.isDigit() || it == '.' || it == ',' }.take(5)
                        },
                        label = { Text(stringResource(R.string.editor_speed_field)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = stringResource(
                        R.string.editor_speed_bounds,
                        "%.1f".format(speedRange.minimum),
                        "%.1f".format(speedRange.maximum),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { resolved?.let { onConfirm(speedRange.coerce(it)) } },
                enabled = resolved != null,
            ) {
                Text(stringResource(R.string.editor_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.editor_cancel))
            }
        },
    )
}

@Composable
private fun HeartRateZonePicker(
    draft: StepDraft,
    capabilities: TreadmillCapabilities,
    onDraftChange: (StepDraft) -> Unit,
) {
    val speedRange = capabilities.speedRange ?: TreadmillCapabilities.UNKNOWN.speedRange!!

    Text(
        text = stringResource(
            R.string.editor_heart_rate_zone,
            draft.heartRateMinBpm,
            draft.heartRateMaxBpm,
        ),
        style = MaterialTheme.typography.headlineSmall,
    )

    RangeSlider(
        value = draft.heartRateMinBpm.toFloat()..draft.heartRateMaxBpm.toFloat(),
        onValueChange = { range ->
            val maximum = range.endInclusive.roundToInt().coerceIn(
                HeartRateTarget.LOWEST_BPM + HeartRateTarget.NARROWEST_WIDTH_BPM,
                HeartRateTarget.HIGHEST_BPM,
            )
            val minimum = range.start.roundToInt().coerceIn(
                HeartRateTarget.LOWEST_BPM,
                maximum - HeartRateTarget.NARROWEST_WIDTH_BPM,
            )
            onDraftChange(draft.copy(heartRateMinBpm = minimum, heartRateMaxBpm = maximum))
        },
        valueRange = HeartRateTarget.LOWEST_BPM.toFloat()..HeartRateTarget.HIGHEST_BPM.toFloat(),
    )

    Text(
        text = stringResource(R.string.editor_heart_rate_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        text = stringResource(
            R.string.editor_heart_rate_bounds,
            "%.1f".format(speedRange.minimum),
            "%.1f".format(speedRange.maximum),
        ),
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun InclinationPicker(
    draft: StepDraft,
    range: TreadmillCapabilities.ValueRange,
    onDraftChange: (StepDraft) -> Unit,
) {
    val current = draft.inclinationPercent ?: 0.0
    Text(text = "%.1f %%".format(current), style = MaterialTheme.typography.titleMedium)
    Slider(
        value = current.toFloat().coerceIn(range.minimum.toFloat(), range.maximum.toFloat()),
        onValueChange = {
            onDraftChange(draft.copy(inclinationPercent = range.coerce(it.toDouble())))
        },
        valueRange = range.minimum.toFloat()..range.maximum.toFloat(),
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun NumberField(
    value: String,
    label: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private const val PACE_STEP_SECONDS = 5

private const val FASTEST_PACE_SECONDS = 150
private const val SLOWEST_PACE_SECONDS = 900
