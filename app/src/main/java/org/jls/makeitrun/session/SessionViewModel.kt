package org.jls.makeitrun.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.HeartRateSensorRepository
import org.jls.makeitrun.data.SessionHistoryRepository
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.data.WorkoutRepository
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.heartrate.HeartRateConnectionState
import javax.inject.Inject

data class SessionHeartRate(
    val isSensorLinked: Boolean = false,
    val beatsPerMinute: Int? = null,
)

@HiltViewModel
class SessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: TreadmillProfileRepository,
    private val sensorRepository: HeartRateSensorRepository,
    private val history: SessionHistoryRepository,
    private val client: FtmsTreadmillClient,
    private val engine: WorkoutSessionEngine,
    heartRateClient: HeartRateClient,
) : ViewModel() {

    val state = engine.state

    private val _showPace = MutableStateFlow(true)
    val showPace = _showPace.asStateFlow()

    val heartRate = combine(
        heartRateClient.connectionState,
        heartRateClient.sample,
    ) { connection, sample ->
        SessionHeartRate(
            isSensorLinked = connection is HeartRateConnectionState.Connected ||
                connection is HeartRateConnectionState.Reconnecting,
            beatsPerMinute = sample?.measurement?.takeIf { it.isUsable }?.beatsPerMinute,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionHeartRate(),
    )

    init {
        viewModelScope.launch {
            profileRepository.profile.collect { _showPace.value = it.showPaceInsteadOfSpeed }
        }
    }

    fun startIfIdle(workoutId: Long) {
        if (engine.state.value.isActive) return
        viewModelScope.launch {
            val workout = workoutRepository.find(workoutId) ?: return@launch
            val capabilities = client.capabilities.value
                ?: profileRepository.profile.first().capabilities
            WorkoutSessionService.start(context)
            engine.start(
                workout = workout,
                capabilities = capabilities,
                responsiveness = sensorRepository.current().responsiveness,
            )
        }
    }

    fun resumeIfIdle(sessionId: Long) {
        if (engine.state.value.isActive) return
        viewModelScope.launch {
            val resumePoint = history.findResumePoint(sessionId)
            if (resumePoint == null) {
                engine.reportFailure(
                    "Cette seance ne peut plus etre reprise. Relancez l'entrainement " +
                        "depuis le debut."
                )
                return@launch
            }

            val capabilities = client.capabilities.value
                ?: profileRepository.profile.first().capabilities
            WorkoutSessionService.start(context)
            engine.start(
                workout = resumePoint.workout,
                capabilities = capabilities,
                responsiveness = sensorRepository.current().responsiveness,
                resumeFrom = resumePoint,
            )
        }
    }

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun skipStep() = engine.skipStep()

    fun stop() {
        engine.stop()
        WorkoutSessionService.stop(context)
    }

    fun acknowledge() {
        engine.acknowledge()
        WorkoutSessionService.stop(context)
    }
}
