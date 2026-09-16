package org.jls.makeitrun.ui.connection

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.data.HeartRateSensorProfile
import org.jls.makeitrun.data.HeartRateSensorRepository
import org.jls.makeitrun.heartrate.DiscoveredHeartRateSensor
import org.jls.makeitrun.heartrate.HeartRateClient
import org.jls.makeitrun.heartrate.HeartRateConnectionState
import org.jls.makeitrun.heartrate.HeartRateSample
import org.jls.makeitrun.heartrate.HeartRateScanner
import org.jls.makeitrun.session.RegulationResponsiveness
import timber.log.Timber
import javax.inject.Inject

data class HeartRateConnectionUiState(
    val connection: HeartRateConnectionState = HeartRateConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val sensors: List<DiscoveredHeartRateSensor> = emptyList(),
    val profile: HeartRateSensorProfile = HeartRateSensorProfile(
        address = null,
        name = null,
        responsiveness = RegulationResponsiveness.DEFAULT,
    ),
    val sample: HeartRateSample? = null,
    val batteryLevelPercent: Int? = null,
    val signalLost: Boolean = false,
) {
    val isConnected: Boolean get() = connection is HeartRateConnectionState.Connected

    val isSilent: Boolean get() = isConnected && signalLost

    val beatsPerMinute: Int? get() = sample?.measurement?.takeIf { it.isUsable }?.beatsPerMinute
}

@HiltViewModel
class HeartRateConnectionViewModel @Inject constructor(
    private val scanner: HeartRateScanner,
    private val client: HeartRateClient,
    private val sensorRepository: HeartRateSensorRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeartRateConnectionUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            client.connectionState.collect { state ->
                if (state is HeartRateConnectionState.Failed) {
                    _messages.tryEmit(state.reason)
                }
                if (state is HeartRateConnectionState.Connected) {
                    rememberSensor(
                        address = state.address,
                        name = _uiState.value.sensors
                            .firstOrNull { it.address == state.address }?.name,
                    )
                }
                _uiState.update {
                    it.copy(
                        connection = state,
                        sensors = if (state is HeartRateConnectionState.Connected) {
                            emptyList()
                        } else {
                            it.sensors
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            client.sample.collect { sample -> _uiState.update { it.copy(sample = sample) } }
        }
        viewModelScope.launch {
            client.signalLost.collect { lost ->
                _uiState.update { it.copy(signalLost = lost) }
            }
        }
        viewModelScope.launch {
            client.batteryLevelPercent.collect { level ->
                _uiState.update { it.copy(batteryLevelPercent = level) }
            }
        }
        viewModelScope.launch {
            sensorRepository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, sensors = emptyList()) }
        scanJob = viewModelScope.launch {
            scanner.scan()
                .catch { error ->
                    Timber.e(error, "Le scan des capteurs cardiaques a echoue")
                    error.message?.let(_messages::tryEmit)
                    _uiState.update { it.copy(isScanning = false) }
                }
                .collect { sensors -> _uiState.update { it.copy(sensors = sensors) } }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(sensor: DiscoveredHeartRateSensor) = connect(sensor.address)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun reconnectLastSensor() {
        _uiState.value.profile.address?.let(::connect)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connect(address: String) {
        val connection = _uiState.value.connection
        if (connection != HeartRateConnectionState.Disconnected &&
            connection !is HeartRateConnectionState.Failed
        ) {
            return
        }
        stopScan()
        viewModelScope.launch { client.connect(address) }
    }

    fun disconnect() {
        client.disconnect()
    }

    private fun rememberSensor(address: String, name: String?) {
        viewModelScope.launch {
            sensorRepository.rememberSensor(
                address = address,
                name = name ?: _uiState.value.profile.name,
            )
        }
    }
}
