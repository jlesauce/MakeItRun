package org.jls.makeitrun.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jls.makeitrun.session.WorkoutSessionEngine
import javax.inject.Inject

@HiltViewModel
class RunningSessionViewModel @Inject constructor(
    private val engine: WorkoutSessionEngine,
) : ViewModel() {

    val runningWorkoutId = engine.state
        .map { engine.runningWorkoutId }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = engine.runningWorkoutId,
        )
}
