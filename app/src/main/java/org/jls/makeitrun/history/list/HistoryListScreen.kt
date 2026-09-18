package org.jls.makeitrun.history.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.history.SessionFormats
import org.jls.makeitrun.history.model.SessionOutcome
import org.jls.makeitrun.history.model.SessionSummary
import org.jls.makeitrun.ui.MakeItRunTopBar
import org.jls.makeitrun.workout.model.Formats
import kotlin.math.roundToInt

@Composable
fun HistoryListScreen(
    contentPadding: PaddingValues,
    onOpenSession: (Long) -> Unit,
    onResumeSession: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: HistoryListViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val resumableIds by viewModel.resumableIds.collectAsStateWithLifecycle()

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
            item {
                Text(
                    text = stringResource(R.string.history_section),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        onClick = { onOpenSession(session.id) },
                        onResume = if (session.id in resumableIds) {
                            { onResumeSession(session.id) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionSummary,
    onClick: () -> Unit,
    onResume: (() -> Unit)?,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = SessionFormats.shortDateTime(session.startedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = session.workoutName, style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.history_metrics,
                        Formats.duration(session.elapsedSeconds),
                        Formats.distance(session.distanceMeters),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                session.averageBpm?.let { beats ->
                    Text(
                        text = stringResource(R.string.history_average_heart_rate, beats),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OutcomeLine(session)

            onResume?.let { resume ->
                FilledTonalButton(
                    onClick = resume,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.history_resume),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OutcomeLine(session: SessionSummary) {
    val label = when (session.outcome) {
        SessionOutcome.COMPLETED -> session.zoneShare?.let {
            stringResource(R.string.history_zone_share, (it * 100).roundToInt())
        }

        SessionOutcome.FAILED -> stringResource(R.string.history_outcome_failed)
        else -> stringResource(
            R.string.history_outcome_stopped,
        ) + " · " + stringResource(
            R.string.history_steps_done,
            session.stepsCompleted,
            session.stepCount,
        )
    } ?: return

    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = if (session.outcome == SessionOutcome.COMPLETED) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
    )
}
