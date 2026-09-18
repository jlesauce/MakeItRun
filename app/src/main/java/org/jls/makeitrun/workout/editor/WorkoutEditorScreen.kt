package org.jls.makeitrun.workout.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.RepeatBlock
import org.jls.makeitrun.workout.model.WorkoutStep

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorScreen(
    workoutId: Long,
    onDone: () -> Unit,
    viewModel: WorkoutEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(workoutId) { viewModel.load(workoutId) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.isNew) R.string.editor_title_new
                            else R.string.editor_title_edit
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onDone) },
                        enabled = state.canSave,
                    ) {
                        Text(stringResource(R.string.editor_save))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text(stringResource(R.string.editor_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.elements.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.editor_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(state.elements.size, key = { state.elements[it].id }) { index ->
                when (val element = state.elements[index]) {
                    is WorkoutStep -> StepCard(
                        step = element,
                        parentBlockId = null,
                        showPace = state.showPaceInsteadOfSpeed,
                        onEdit = { viewModel.editStep(element, null) },
                        onMoveUp = { viewModel.move(element.id, -1) },
                        onMoveDown = { viewModel.move(element.id, 1) },
                        onDelete = { viewModel.remove(element.id) },
                    )

                    is RepeatBlock -> RepeatBlockCard(
                        block = element,
                        showPace = state.showPaceInsteadOfSpeed,
                        viewModel = viewModel,
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.addStep() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.editor_add_step))
                    }
                    OutlinedButton(
                        onClick = viewModel::addRepeatBlock,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.editor_add_repeat))
                    }
                }
            }
        }
    }

    state.draft?.let { draft ->
        StepEditorSheet(
            draft = draft,
            capabilities = state.capabilities,
            showPace = state.showPaceInsteadOfSpeed,
            onDraftChange = viewModel::updateDraft,
            onToggleUnit = viewModel::toggleUnit,
            onConfirm = viewModel::commitDraft,
            onDismiss = viewModel::cancelDraft,
        )
    }
}

@Composable
private fun RepeatBlockCard(
    block: RepeatBlock,
    showPace: Boolean,
    viewModel: WorkoutEditorViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.editor_repetitions),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    IconButton(
                        onClick = { viewModel.setRepetitions(block.id, block.repetitions - 1) }
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    Text(
                        text = block.repetitions.toString(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(
                        onClick = { viewModel.setRepetitions(block.id, block.repetitions + 1) }
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null)
                    }
                }

                Row {
                    IconButton(onClick = { viewModel.move(block.id, -1) }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.editor_move_up),
                        )
                    }
                    IconButton(onClick = { viewModel.move(block.id, 1) }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.editor_move_down),
                        )
                    }
                    IconButton(onClick = { viewModel.remove(block.id) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.editor_delete),
                        )
                    }
                }
            }

            block.steps.forEach { step ->
                StepCard(
                    step = step,
                    parentBlockId = block.id,
                    showPace = showPace,
                    onEdit = { viewModel.editStep(step, block.id) },
                    onMoveUp = { viewModel.move(step.id, -1, block.id) },
                    onMoveDown = { viewModel.move(step.id, 1, block.id) },
                    onDelete = { viewModel.remove(step.id, block.id) },
                )
            }

            OutlinedButton(
                onClick = { viewModel.addStep(block.id) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.editor_add_step_to_block))
            }

            if (block.steps.isNotEmpty()) {
                SkipLastStepRow(
                    checked = block.skipLastStepOnFinalRepetition,
                    onCheckedChange = {
                        viewModel.setSkipLastStepOnFinalRepetition(block.id, it)
                    },
                )
            }
        }
    }
}

@Composable
private fun SkipLastStepRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = stringResource(R.string.editor_skip_last_step),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepCard(
    step: WorkoutStep,
    parentBlockId: String?,
    showPace: Boolean,
    onEdit: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        onClick = onEdit,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (parentBlockId != null) 8.dp else 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(4.dp)
                    .size(width = 4.dp, height = 40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WorkoutStepLabels.typeColor(step.type))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = WorkoutStepLabels.typeName(step.type),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "${WorkoutStepLabels.duration(step.duration)} · " +
                        WorkoutStepLabels.target(step, showPace),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            IconButton(onClick = onMoveUp) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.editor_move_up),
                )
            }
            IconButton(onClick = onMoveDown) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.editor_move_down),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.editor_delete),
                )
            }
        }
    }
}
