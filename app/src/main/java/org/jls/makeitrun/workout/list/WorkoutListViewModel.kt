package org.jls.makeitrun.workout.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.jls.makeitrun.data.WorkoutRepository
import org.jls.makeitrun.workout.model.Workout
import org.jls.makeitrun.workout.model.WorkoutPlan
import org.jls.makeitrun.workout.model.WorkoutSummary
import javax.inject.Inject

data class WorkoutListItem(
    val workout: Workout,
    val summary: WorkoutSummary,
)

@HiltViewModel
class WorkoutListViewModel @Inject constructor(
    repository: WorkoutRepository,
) : ViewModel() {

    val workouts = repository.observeAll()
        .map { workouts ->
            workouts.map { WorkoutListItem(it, WorkoutPlan.summarize(it.elements)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
