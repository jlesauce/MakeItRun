package org.jls.makeitrun.workout.editor

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.jls.makeitrun.R
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.Formats
import org.jls.makeitrun.workout.model.HeartRateTarget
import org.jls.makeitrun.workout.model.Pace
import org.jls.makeitrun.workout.model.RegulationResponsiveness
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
    val focusManager = LocalFocusManager.current

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
                        showPace = showPace,
                        onDraftChange = onDraftChange,
                    )

                    StepTargetMode.FREE -> Unit
                }
            }

            if (draft.targetMode == StepTargetMode.HEART_RATE) {
                Section(stringResource(R.string.editor_regulation)) {
                    RegulationPicker(draft = draft, onDraftChange = onDraftChange)
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
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f),
                ) {
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
    val speedStep = speedRange.increment.takeIf { it > 0.0 } ?: 0.1

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onToggleUnit) {
            Text(
                stringResource(
                    if (showPace) R.string.editor_unit_speed else R.string.editor_unit_pace
                )
            )
        }
    }

    if (showPace) {
        val bounds = paceBounds(speedRange)
        val fastestSpeed = speedRange.coerce(Pace.speedKmhFrom(bounds.first) ?: speedRange.maximum)
        val slowestSpeed = speedRange.coerce(Pace.speedKmhFrom(bounds.last) ?: speedRange.minimum)
        val slowdownRange = (fastestSpeed - slowestSpeed).coerceAtLeast(0.0)

        StepperSlider(
            value = (fastestSpeed - draft.targetSpeedKmh)
                .coerceIn(0.0, slowdownRange)
                .toFloat(),
            onValueChange = { slowdown ->
                val speed = speedRange.coerce(fastestSpeed - slowdown)
                onDraftChange(draft.copy(targetSpeedKmh = speed.coerceIn(slowestSpeed, fastestSpeed)))
            },
            valueRange = 0f..slowdownRange.toFloat(),
            step = speedStep.toFloat(),
            valueLabel = Formats.target(draft.targetSpeedKmh, showPace = true, freeLabel = ""),
            onValueClick = { isTyping = true },
        )
    } else {
        StepperSlider(
            value = draft.targetSpeedKmh.toFloat()
                .coerceIn(speedRange.minimum.toFloat(), speedRange.maximum.toFloat()),
            onValueChange = {
                onDraftChange(draft.copy(targetSpeedKmh = speedRange.coerce(it.toDouble())))
            },
            valueRange = speedRange.minimum.toFloat()..speedRange.maximum.toFloat(),
            step = speedStep.toFloat(),
            valueLabel = Formats.target(draft.targetSpeedKmh, showPace = false, freeLabel = ""),
            onValueClick = { isTyping = true },
        )
    }

    Text(
        text = stringResource(R.string.editor_target_manual_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(text = boundsLabel(speedRange, showPace), style = MaterialTheme.typography.bodySmall)

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

    val bounds = paceBounds(speedRange)
    val resolved: Double? = if (showPace) {
        val total = (minutes.toIntOrNull() ?: 0) * 60 + (seconds.toIntOrNull() ?: 0)
        Pace.speedKmhFrom(total.coerceIn(bounds))
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
                    text = boundsLabel(speedRange, showPace),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeartRateZonePicker(
    draft: StepDraft,
    capabilities: TreadmillCapabilities,
    showPace: Boolean,
    onDraftChange: (StepDraft) -> Unit,
) {
    val speedRange = capabilities.speedRange ?: TreadmillCapabilities.UNKNOWN.speedRange!!
    val focusManager = LocalFocusManager.current
    var minText by remember { mutableStateOf(draft.heartRateMinBpm.toString()) }
    var maxText by remember { mutableStateOf(draft.heartRateMaxBpm.toString()) }
    var minFocused by remember { mutableStateOf(false) }
    var maxFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberField(
            value = if (minFocused) minText else draft.heartRateMinBpm.toString(),
            label = stringResource(R.string.editor_heart_rate_min),
            onValueChange = { input ->
                minText = input
                applyHeartRateMin(input, draft, onDraftChange)
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        minFocused = true
                        minText = draft.heartRateMinBpm.toString()
                    } else if (minFocused) {
                        minFocused = false
                        minText = commitHeartRateMin(minText, draft, onDraftChange)
                    }
                },
        )
        NumberField(
            value = if (maxFocused) maxText else draft.heartRateMaxBpm.toString(),
            label = stringResource(R.string.editor_heart_rate_max),
            onValueChange = { input ->
                maxText = input
                applyHeartRateMax(input, draft, onDraftChange)
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focus ->
                    if (focus.isFocused) {
                        maxFocused = true
                        maxText = draft.heartRateMaxBpm.toString()
                    } else if (maxFocused) {
                        maxFocused = false
                        maxText = commitHeartRateMax(maxText, draft, onDraftChange)
                    }
                },
        )
    }

    RangeSlider(
        value = draft.heartRateMinBpm.toFloat()..draft.heartRateMaxBpm.toFloat(),
        onValueChange = { range ->
            focusManager.clearFocus()
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
        text = stringResource(
            R.string.editor_heart_rate_range_bounds,
            HeartRateTarget.LOWEST_BPM,
            HeartRateTarget.HIGHEST_BPM,
            HeartRateTarget.NARROWEST_WIDTH_BPM,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(
        text = stringResource(R.string.editor_heart_rate_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )
    Text(text = boundsLabel(speedRange, showPace), style = MaterialTheme.typography.bodySmall)
}

private fun paceBounds(speedRange: TreadmillCapabilities.ValueRange): IntRange {
    val fastest = maxOf(
        Pace.secondsPerKmFrom(speedRange.maximum) ?: FASTEST_PACE_SECONDS,
        FASTEST_PACE_SECONDS,
    )
    val slowest = minOf(
        Pace.secondsPerKmFrom(speedRange.minimum) ?: SLOWEST_PACE_SECONDS,
        SLOWEST_PACE_SECONDS,
    )
    return fastest..maxOf(slowest, fastest)
}

@Composable
private fun boundsLabel(
    speedRange: TreadmillCapabilities.ValueRange,
    showPace: Boolean,
): String = if (showPace) {
    val bounds = paceBounds(speedRange)
    stringResource(
        R.string.editor_pace_bounds,
        Pace.format(bounds.first),
        Pace.format(bounds.last),
    )
} else {
    stringResource(
        R.string.editor_speed_bounds,
        "%.1f".format(speedRange.minimum),
        "%.1f".format(speedRange.maximum),
    )
}

@Composable
private fun RegulationPicker(draft: StepDraft, onDraftChange: (StepDraft) -> Unit) {
    ChipRow {
        FilterChip(
            selected = draft.regulationResponsiveness == null,
            onClick = { onDraftChange(draft.copy(regulationResponsiveness = null)) },
            label = { Text(stringResource(R.string.editor_regulation_default)) },
        )
        RegulationResponsiveness.entries.forEach { candidate ->
            FilterChip(
                selected = draft.regulationResponsiveness == candidate,
                onClick = { onDraftChange(draft.copy(regulationResponsiveness = candidate)) },
                label = { Text(WorkoutStepLabels.regulationName(candidate)) },
            )
        }
    }

    Text(
        text = stringResource(R.string.editor_regulation_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
    )

    draft.regulationResponsiveness?.let { responsiveness ->
        Text(
            text = stringResource(
                R.string.settings_regulation_option,
                "%.1f".format(responsiveness.speedStepKmh),
                (responsiveness.settleMillis / 1_000).toInt(),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun InclinationPicker(
    draft: StepDraft,
    range: TreadmillCapabilities.ValueRange,
    onDraftChange: (StepDraft) -> Unit,
) {
    val current = draft.inclinationPercent ?: 0.0
    val step = range.increment.takeIf { it > 0.0 } ?: 0.5
    StepperSlider(
        value = current.toFloat().coerceIn(range.minimum.toFloat(), range.maximum.toFloat()),
        onValueChange = {
            onDraftChange(draft.copy(inclinationPercent = range.coerce(it.toDouble())))
        },
        valueRange = range.minimum.toFloat()..range.maximum.toFloat(),
        step = step.toFloat(),
        valueLabel = "%.1f %%".format(current),
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepperSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    valueLabel: String,
    onValueClick: (() -> Unit)? = null,
) {
    val snapped = snappedValue(value, valueRange.start, valueRange.endInclusive, step)
    val start = minOf(valueRange.start, valueRange.endInclusive)
    val end = maxOf(valueRange.start, valueRange.endInclusive)
    val canSlide = end - start >= step / 2f
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onValueChange(snappedValue(snapped - step, start, end, step)) },
            enabled = snapped > start + step / 2f,
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = stringResource(R.string.editor_decrease),
            )
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            if (onValueClick != null) {
                TextButton(onClick = onValueClick) {
                    Text(text = valueLabel, style = MaterialTheme.typography.headlineSmall)
                }
            } else {
                Text(text = valueLabel, style = MaterialTheme.typography.headlineSmall)
            }
        }
        IconButton(
            onClick = { onValueChange(snappedValue(snapped + step, start, end, step)) },
            enabled = snapped < end - step / 2f,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.editor_increase),
            )
        }
    }

    if (canSlide) {
        Slider(
            value = snapped.coerceIn(start, end),
            onValueChange = { onValueChange(snappedValue(it, start, end, step)) },
            valueRange = start..end,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    thumbSize = DpSize(28.dp, 28.dp),
                )
            },
        )
    }
}

private fun applyHeartRateMin(
    raw: String,
    draft: StepDraft,
    onDraftChange: (StepDraft) -> Unit,
) {
    val parsed = raw.toIntOrNull() ?: return
    if (parsed !in HeartRateTarget.LOWEST_BPM..HeartRateTarget.HIGHEST_BPM) return
    val (minimum, maximum) = heartRateAfterMinChange(parsed, draft.heartRateMaxBpm)
    onDraftChange(draft.copy(heartRateMinBpm = minimum, heartRateMaxBpm = maximum))
}

private fun applyHeartRateMax(
    raw: String,
    draft: StepDraft,
    onDraftChange: (StepDraft) -> Unit,
) {
    val parsed = raw.toIntOrNull() ?: return
    if (parsed !in HeartRateTarget.LOWEST_BPM..HeartRateTarget.HIGHEST_BPM) return
    val (minimum, maximum) = heartRateAfterMaxChange(parsed, draft.heartRateMinBpm)
    onDraftChange(draft.copy(heartRateMinBpm = minimum, heartRateMaxBpm = maximum))
}

private fun commitHeartRateMin(
    raw: String,
    draft: StepDraft,
    onDraftChange: (StepDraft) -> Unit,
): String {
    val parsed = raw.toIntOrNull() ?: return draft.heartRateMinBpm.toString()
    val (minimum, maximum) = heartRateAfterMinChange(parsed, draft.heartRateMaxBpm)
    onDraftChange(draft.copy(heartRateMinBpm = minimum, heartRateMaxBpm = maximum))
    return minimum.toString()
}

private fun commitHeartRateMax(
    raw: String,
    draft: StepDraft,
    onDraftChange: (StepDraft) -> Unit,
): String {
    val parsed = raw.toIntOrNull() ?: return draft.heartRateMaxBpm.toString()
    val (minimum, maximum) = heartRateAfterMaxChange(parsed, draft.heartRateMinBpm)
    onDraftChange(draft.copy(heartRateMinBpm = minimum, heartRateMaxBpm = maximum))
    return maximum.toString()
}

private fun heartRateAfterMinChange(requested: Int, currentMax: Int): Pair<Int, Int> {
    val minimum = requested.coerceIn(
        HeartRateTarget.LOWEST_BPM,
        HeartRateTarget.HIGHEST_BPM - HeartRateTarget.NARROWEST_WIDTH_BPM,
    )
    val maximum = currentMax.coerceIn(
        minimum + HeartRateTarget.NARROWEST_WIDTH_BPM,
        HeartRateTarget.HIGHEST_BPM,
    )
    return minimum to maximum
}

private fun heartRateAfterMaxChange(requested: Int, currentMin: Int): Pair<Int, Int> {
    val maximum = requested.coerceIn(
        HeartRateTarget.LOWEST_BPM + HeartRateTarget.NARROWEST_WIDTH_BPM,
        HeartRateTarget.HIGHEST_BPM,
    )
    val minimum = currentMin.coerceIn(
        HeartRateTarget.LOWEST_BPM,
        maximum - HeartRateTarget.NARROWEST_WIDTH_BPM,
    )
    return minimum to maximum
}

private fun snappedValue(
    value: Float,
    rangeStart: Float,
    rangeEnd: Float,
    step: Float,
): Float {
    val clamped = value.coerceIn(minOf(rangeStart, rangeEnd), maxOf(rangeStart, rangeEnd))
    if (step <= 0f) return clamped
    val stepsFromStart = ((clamped - rangeStart) / step).roundToInt()
    return (rangeStart + stepsFromStart * step).coerceIn(
        minOf(rangeStart, rangeEnd),
        maxOf(rangeStart, rangeEnd),
    )
}

private const val FASTEST_PACE_SECONDS = 150
private const val SLOWEST_PACE_SECONDS = 900
