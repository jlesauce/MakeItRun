package org.jls.makeitrun.workout.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.ui.MakeItRunTopBar
import org.jls.makeitrun.ui.connection.ConnectionBanner
import org.jls.makeitrun.ui.connection.ConnectionViewModel
import org.jls.makeitrun.ui.connection.HeartRateBanner
import org.jls.makeitrun.ui.connection.HeartRateConnectionViewModel
import org.jls.makeitrun.workout.model.Formats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutListScreen(
    contentPadding: PaddingValues,
    onCreateWorkout: () -> Unit,
    onOpenWorkout: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: WorkoutListViewModel = hiltViewModel(),
    connectionViewModel: ConnectionViewModel = hiltViewModel(),
    heartRateViewModel: HeartRateConnectionViewModel = hiltViewModel(),
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    val connection by connectionViewModel.uiState.collectAsStateWithLifecycle()
    val heartRate by heartRateViewModel.uiState.collectAsStateWithLifecycle()
    var askForTreadmill by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    if (askForTreadmill) {
        TreadmillRequiredDialog(onDismiss = { askForTreadmill = false })
    }

    LaunchedEffect(Unit) {
        connectionViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    LaunchedEffect(Unit) {
        heartRateViewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MakeItRunTopBar(onOpenSettings = onOpenSettings, onOpenAbout = onOpenAbout)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (connection.hasKnownTreadmill) {
                        onCreateWorkout()
                    } else {
                        askForTreadmill = true
                    }
                },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.workouts_create),
                )
            }
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
                bottom = contentPadding.calculateBottomPadding() + 88.dp,
            ),
        ) {
            item {
                ConnectionBanner(state = connection, viewModel = connectionViewModel)
            }

            item {
                HeartRateBanner(state = heartRate, viewModel = heartRateViewModel)
            }

            item {
                Text(
                    text = stringResource(R.string.workouts_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            if (!connection.hasKnownTreadmill) {
                item {
                    Text(
                        text = stringResource(R.string.workouts_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }

            if (workouts.isEmpty()) {
                item { EmptyMessage(stringResource(R.string.workouts_empty)) }
            } else {
                items(workouts, key = { it.workout.id }) { item ->
                    WorkoutRow(
                        item = item,
                        enabled = connection.hasKnownTreadmill,
                        onClick = { onOpenWorkout(item.workout.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TreadmillRequiredDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.workouts_locked_title)) },
        text = { Text(stringResource(R.string.workouts_locked)) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_understood))
            }
        },
    )
}

@Composable
private fun WorkoutRow(item: WorkoutListItem, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else LOCKED_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = item.workout.name, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (item.summary.hasDistance) {
                        stringResource(
                            R.string.workout_summary_line,
                            Formats.duration(item.summary.durationSeconds),
                            Formats.distance(item.summary.distanceMeters),
                        )
                    } else {
                        Formats.duration(item.summary.durationSeconds)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.workout_step_count,
                        item.summary.stepCount,
                        item.summary.stepCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 24.dp),
    )
}

private const val LOCKED_ALPHA = 0.38f
