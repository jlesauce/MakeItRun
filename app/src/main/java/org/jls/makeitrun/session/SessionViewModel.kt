package org.jls.makeitrun.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.HeartRateSensorRepository
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.data.WorkoutRepository
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workoutRepository: WorkoutRepository,
    private val profileRepository: TreadmillProfileRepository,
    private val sensorRepository: HeartRateSensorRepository,
    private val client: FtmsTreadmillClient,
    private val engine: WorkoutSessionEngine,
) : ViewModel() {

    val state = engine.state

    private val _showPace = MutableStateFlow(true)
    val showPace = _showPace.asStateFlow()

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

    fun pause() = engine.pause()

    fun resume() = engine.resume()

    fun stop() {
        engine.stop()
        WorkoutSessionService.stop(context)
    }

    fun acknowledge() {
        engine.acknowledge()
        WorkoutSessionService.stop(context)
    }
}
