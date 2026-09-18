package org.jls.makeitrun.history.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.SessionHistoryRepository
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.history.model.SessionReport
import javax.inject.Inject

data class SessionReportUiState(
    val report: SessionReport? = null,
    val isLoaded: Boolean = false,
    val showPaceInsteadOfSpeed: Boolean = true,
)

@HiltViewModel
class SessionReportViewModel @Inject constructor(
    private val repository: SessionHistoryRepository,
    profileRepository: TreadmillProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionReportUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update {
                    it.copy(showPaceInsteadOfSpeed = profile.showPaceInsteadOfSpeed)
                }
            }
        }
    }

    fun load(sessionId: Long) {
        viewModelScope.launch {
            val report = repository.findReport(sessionId)
            _uiState.update { it.copy(report = report, isLoaded = true) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val report = _uiState.value.report ?: return
        viewModelScope.launch {
            repository.delete(report.summary.id)
            onDeleted()
        }
    }
}
