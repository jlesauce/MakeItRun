package org.jls.makeitrun.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.BackupRepository
import org.jls.makeitrun.data.HeartRateSensorRepository
import org.jls.makeitrun.data.PendingImport
import org.jls.makeitrun.session.RegulationResponsiveness
import timber.log.Timber
import javax.inject.Inject

sealed interface BackupOutcome {
    data class Exported(val count: Int) : BackupOutcome
    data class Imported(val count: Int, val treadmillRestored: Boolean) : BackupOutcome
    data object ExportFailed : BackupOutcome
    data object ImportFailed : BackupOutcome
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: BackupRepository,
    private val sensorRepository: HeartRateSensorRepository,
) : ViewModel() {

    private val _outcome = MutableStateFlow<BackupOutcome?>(null)
    val outcome = _outcome.asStateFlow()

    private val _responsiveness = MutableStateFlow(RegulationResponsiveness.DEFAULT)
    val responsiveness = _responsiveness.asStateFlow()

    init {
        viewModelScope.launch {
            sensorRepository.profile.collect { _responsiveness.value = it.responsiveness }
        }
    }

    fun setResponsiveness(responsiveness: RegulationResponsiveness) {
        viewModelScope.launch { sensorRepository.setResponsiveness(responsiveness) }
    }

    private val _pendingImport = MutableStateFlow<PendingImport?>(null)
    val pendingImport = _pendingImport.asStateFlow()

    fun export(destination: Uri) {
        viewModelScope.launch {
            _outcome.value = runCatching {
                val backup = repository.exportAll()
                context.contentResolver.openOutputStream(destination)?.use { stream ->
                    stream.write(backup.text.toByteArray())
                } ?: error("Le fichier de destination n'a pas pu etre ouvert en ecriture")
                BackupOutcome.Exported(backup.workoutCount)
            }.onFailure {
                Timber.e(it, "Echec de la sauvegarde des entrainements")
            }.getOrDefault(BackupOutcome.ExportFailed)
        }
    }

    fun import(source: Uri) {
        viewModelScope.launch {
            runCatching {
                val content = context.contentResolver.openInputStream(source)?.use { stream ->
                    stream.readBytes().decodeToString()
                } ?: error("Le fichier choisi n'a pas pu etre ouvert en lecture")
                repository.read(content)
            }.onSuccess { pending ->
                if (pending.conflict == null) {
                    applyImport(pending, replaceTreadmill = false)
                } else {
                    _pendingImport.value = pending
                }
            }.onFailure {
                Timber.e(it, "Echec de la restauration des entrainements")
                _outcome.value = BackupOutcome.ImportFailed
            }
        }
    }

    fun resolveTreadmill(replaceTreadmill: Boolean) {
        val pending = _pendingImport.value ?: return
        _pendingImport.value = null
        viewModelScope.launch { applyImport(pending, replaceTreadmill) }
    }

    private suspend fun applyImport(pending: PendingImport, replaceTreadmill: Boolean) {
        _outcome.value = runCatching {
            val result = repository.apply(pending, replaceTreadmill)
            BackupOutcome.Imported(result.workoutCount, result.treadmillRestored)
        }.onFailure {
            Timber.e(it, "Echec de la restauration des entrainements")
        }.getOrDefault(BackupOutcome.ImportFailed)
    }

    fun acknowledge() {
        _outcome.value = null
    }
}
