package org.jls.makeitrun.workout.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.data.WorkoutRepository
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillConnectionState
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.heartrate.HeartRateConnectionState
import org.jls.makeitrun.workout.model.ResolvedStep
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutPlan
import org.jls.makeitrun.workout.model.WorkoutSummary
import javax.inject.Inject

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val steps: List<ResolvedStep> = emptyList(),
    val summary: WorkoutSummary? = null,
    val isConnected: Boolean = false,
    val isHeartRateSensorConnected: Boolean = false,
    val showPaceInsteadOfSpeed: Boolean = true,
) {
    val needsHeartRateSensor: Boolean get() = steps.any { it.step.isHeartRateDriven }

    val canStart: Boolean
        get() = isConnected && (!needsHeartRateSensor || isHeartRateSensorConnected)
}

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    profileRepository: TreadmillProfileRepository,
    client: FtmsTreadmillClient,
    heartRateClient: HeartRateClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            client.connectionState.collect { state ->
                _uiState.update {
                    it.copy(isConnected = state is TreadmillConnectionState.Connected)
                }
            }
        }
        viewModelScope.launch {
            heartRateClient.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        isHeartRateSensorConnected =
                            state is HeartRateConnectionState.Connected,
                    )
                }
            }
        }
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update {
                    it.copy(showPaceInsteadOfSpeed = profile.showPaceInsteadOfSpeed)
                }
            }
        }
    }

    fun load(workoutId: Long) {
        viewModelScope.launch {
            val workout = repository.find(workoutId) ?: return@launch
            _uiState.update {
                it.copy(
                    workout = workout,
                    steps = WorkoutPlan.flatten(workout.elements),
                    summary = WorkoutPlan.summarize(workout.elements),
                )
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val workout = _uiState.value.workout ?: return
        viewModelScope.launch {
            repository.delete(workout.id)
            onDeleted()
        }
    }
}
