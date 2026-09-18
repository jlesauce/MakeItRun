package org.jls.makeitrun.history.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import org.jls.makeitrun.data.SessionHistoryRepository
import org.jls.makeitrun.history.model.SessionResume
import org.jls.makeitrun.session.WorkoutSessionEngine
import javax.inject.Inject

@HiltViewModel
class HistoryListViewModel @Inject constructor(
    repository: SessionHistoryRepository,
    engine: WorkoutSessionEngine,
) : ViewModel() {

    val sessions = repository.observeSummaries()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val clock = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_PERIOD_MILLIS)
        }
    }

    val resumableIds = combine(sessions, clock, engine.state) { summaries, now, sessionState ->
        if (sessionState.isActive) {
            emptySet()
        } else {
            summaries.filter { SessionResume.isResumable(it, now) }
                .map { it.id }
                .toSet()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptySet(),
    )

    private companion object {
        const val CLOCK_PERIOD_MILLIS = 20_000L
    }
}
