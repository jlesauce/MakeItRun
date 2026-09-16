package org.jls.makeitrun.debug

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.ftms.FtmsControlResponse
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.ftms.TreadmillConnectionState
import org.jls.makeitrun.ftms.TreadmillData
import timber.log.Timber
import javax.inject.Inject

data class DebugUiState(
    val connection: TreadmillConnectionState = TreadmillConnectionState.Disconnected,
    val data: TreadmillData? = null,
    val capabilities: TreadmillCapabilities? = null,
    val lastRawFrame: String? = null,
    val targetSpeedKmh: Double = 6.0,
    val targetInclinationPercent: Double = 0.0,
    val lastCommandResult: String? = null,
) {
    val connected: TreadmillConnectionState.Connected?
        get() = connection as? TreadmillConnectionState.Connected
}

@SuppressLint("MissingPermission")
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val client: FtmsTreadmillClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            client.connectionState.collect { state ->
                _uiState.update { it.copy(connection = state) }
            }
        }
        viewModelScope.launch {
            client.treadmillData.collect { data -> _uiState.update { it.copy(data = data) } }
        }
        viewModelScope.launch {
            client.lastRawFrame.collect { frame ->
                _uiState.update { it.copy(lastRawFrame = frame) }
            }
        }
        viewModelScope.launch {
            client.capabilities.collect { capabilities ->
                _uiState.update { it.copy(capabilities = capabilities) }
            }
        }
    }

    fun setTargetSpeed(speedKmh: Double) {
        _uiState.update { it.copy(targetSpeedKmh = speedKmh) }
    }

    fun requestControl() = runCommand("Prise de contrôle") { client.requestControl() }

    fun startBelt() = runCommand("Démarrage") { client.start() }

    fun stopBelt() = runCommand("Arrêt") { client.stop() }

    fun applyTargetSpeed() = runCommand("Vitesse cible") {
        client.setTargetSpeed(_uiState.value.targetSpeedKmh)
    }

    fun setTargetInclination(percent: Double) {
        _uiState.update { it.copy(targetInclinationPercent = percent) }
    }

    fun applyTargetInclination() = runCommand("Pente cible") {
        client.setTargetInclination(_uiState.value.targetInclinationPercent)
    }

    private fun runCommand(label: String, command: suspend () -> FtmsControlResponse?) {
        viewModelScope.launch {
            val response = command()
            val result = when {
                response == null -> "$label : aucune réponse du tapis"
                response.isSuccess -> "$label : acceptée"
                else -> "$label : refusée (${response.result})"
            }
            Timber.i(result)
            _uiState.update { it.copy(lastCommandResult = result) }
        }
    }
}
