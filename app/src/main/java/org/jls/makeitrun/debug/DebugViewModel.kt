package org.jls.makeitrun.debug

import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.R
import org.jls.makeitrun.ftms.FtmsControlResponse
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillCapabilities
import org.jls.makeitrun.ftms.TreadmillConnectionState
import org.jls.makeitrun.ftms.TreadmillData
import timber.log.Timber
import javax.inject.Inject

enum class DebugCommand(@param:StringRes val labelResId: Int) {
    REQUEST_CONTROL(R.string.debug_command_request_control),
    START(R.string.debug_command_start),
    STOP(R.string.debug_command_stop),
    TARGET_SPEED(R.string.debug_command_target_speed),
    TARGET_INCLINATION(R.string.debug_command_target_inclination),
}

data class DebugCommandResult(
    val command: DebugCommand,
    val refusalReason: String?,
    val answered: Boolean,
)

data class DebugUiState(
    val connection: TreadmillConnectionState = TreadmillConnectionState.Disconnected,
    val data: TreadmillData? = null,
    val capabilities: TreadmillCapabilities? = null,
    val lastRawFrame: String? = null,
    val targetSpeedKmh: Double = 6.0,
    val targetInclinationPercent: Double = 0.0,
    val lastCommandResult: DebugCommandResult? = null,
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

    fun requestControl() = runCommand(DebugCommand.REQUEST_CONTROL) { client.requestControl() }

    fun startBelt() = runCommand(DebugCommand.START) { client.start() }

    fun stopBelt() = runCommand(DebugCommand.STOP) { client.stop() }

    fun applyTargetSpeed() = runCommand(DebugCommand.TARGET_SPEED) {
        client.setTargetSpeed(_uiState.value.targetSpeedKmh)
    }

    fun setTargetInclination(percent: Double) {
        _uiState.update { it.copy(targetInclinationPercent = percent) }
    }

    fun applyTargetInclination() = runCommand(DebugCommand.TARGET_INCLINATION) {
        client.setTargetInclination(_uiState.value.targetInclinationPercent)
    }

    private fun runCommand(
        command: DebugCommand,
        send: suspend () -> FtmsControlResponse?,
    ) {
        viewModelScope.launch {
            val response = send()
            val result = DebugCommandResult(
                command = command,
                refusalReason = response?.result?.takeIf { response.isSuccess.not() }?.toString(),
                answered = response != null,
            )
            Timber.i(
                "Commande %s : %s",
                command,
                result.refusalReason ?: if (result.answered) "acceptee" else "sans reponse",
            )
            _uiState.update { it.copy(lastCommandResult = result) }
        }
    }
}
