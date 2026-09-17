package org.jls.makeitrun.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.data.TreadmillConflict
import org.jls.makeitrun.workout.WorkoutStepLabels
import org.jls.makeitrun.workout.model.RegulationResponsiveness
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val pendingImport by viewModel.pendingImport.collectAsStateWithLifecycle()
    val responsiveness by viewModel.responsiveness.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)
    ) { destination -> destination?.let(viewModel::export) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { source -> source?.let(viewModel::import) }

    val message = when (val current = outcome) {
        null -> null
        is BackupOutcome.Exported ->
            pluralStringResource(R.plurals.backup_exported, current.count, current.count)

        is BackupOutcome.Imported -> pluralStringResource(
            if (current.treadmillRestored) R.plurals.backup_imported_with_treadmill
            else R.plurals.backup_imported,
            current.count,
            current.count,
        )

        BackupOutcome.ExportFailed -> stringResource(R.string.backup_export_failed)
        BackupOutcome.ImportFailed -> stringResource(R.string.backup_import_failed)
    }

    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.acknowledge()
        }
    }

    pendingImport?.conflict?.let { conflict ->
        TreadmillConflictDialog(
            conflict = conflict,
            onKeepCurrent = { viewModel.resolveTreadmill(replaceTreadmill = false) },
            onUseBackup = { viewModel.resolveTreadmill(replaceTreadmill = true) },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        ) {
            item { LanguageCard() }

            item {
                RegulationCard(
                    selected = responsiveness,
                    onSelect = viewModel::setResponsiveness,
                )
            }

            item {
                BackupCard(
                    onExport = { exportLauncher.launch(suggestedFileName()) },
                    onImport = { importLauncher.launch(BACKUP_OPENABLE_TYPES) },
                )
            }
        }
    }
}

@Composable
private fun TreadmillConflictDialog(
    conflict: TreadmillConflict,
    onKeepCurrent: () -> Unit,
    onUseBackup: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onKeepCurrent,
        title = { Text(stringResource(R.string.backup_treadmill_conflict_title)) },
        text = {
            Text(
                stringResource(
                    R.string.backup_treadmill_conflict_message,
                    conflict.backupLabel,
                    conflict.currentLabel,
                )
            )
        },
        confirmButton = {
            TextButton(onClick = onUseBackup) {
                Text(stringResource(R.string.backup_treadmill_use_backup))
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepCurrent) {
                Text(stringResource(R.string.backup_treadmill_keep_current))
            }
        },
    )
}

@Composable
private fun LanguageCard() {
    var language by remember { mutableStateOf(AppLanguage.current()) }

    SettingsCard(
        title = stringResource(R.string.settings_language_title),
        summary = stringResource(R.string.settings_language_summary),
    ) {
        Column(Modifier.selectableGroup()) {
            AppLanguage.entries.forEach { candidate ->
                RadioRow(
                    label = stringResource(candidate.labelResId),
                    selected = candidate == language,
                    onSelect = {
                        language = candidate
                        AppLanguage.apply(candidate)
                    },
                )
            }
        }
    }
}

@Composable
private fun RegulationCard(
    selected: RegulationResponsiveness,
    onSelect: (RegulationResponsiveness) -> Unit,
) {
    SettingsCard(
        title = stringResource(R.string.settings_regulation_title),
        summary = stringResource(R.string.settings_regulation_summary),
    ) {
        Column(Modifier.selectableGroup()) {
            RegulationResponsiveness.entries.forEach { candidate ->
                RadioRow(
                    label = WorkoutStepLabels.regulationName(candidate),
                    supporting = stringResource(
                        R.string.settings_regulation_option,
                        "%.1f".format(candidate.speedStepKmh),
                        (candidate.settleMillis / 1_000).toInt(),
                    ),
                    selected = candidate == selected,
                    onSelect = { onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
private fun BackupCard(onExport: () -> Unit, onImport: () -> Unit) {
    SettingsCard(
        title = stringResource(R.string.settings_backup_title),
        summary = stringResource(R.string.settings_backup_summary),
    ) {
        ActionRow(
            icon = Icons.Default.FileDownload,
            label = stringResource(R.string.settings_backup_export),
            onClick = onExport,
        )
        ActionRow(
            icon = Icons.Default.FileUpload,
            label = stringResource(R.string.settings_backup_import),
            onClick = onImport,
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    summary: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun RadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    supporting: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            supporting?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun suggestedFileName(): String = "make-it-run-${LocalDate.now()}.json"

private const val BACKUP_MIME_TYPE = "application/json"

private val BACKUP_OPENABLE_TYPES = arrayOf(
    BACKUP_MIME_TYPE,
    "text/plain",
    "application/octet-stream",
)
