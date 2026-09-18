package org.jls.makeitrun.ui.connection

import android.Manifest
import android.annotation.SuppressLint
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
import org.jls.makeitrun.data.TreadmillProfile
import org.jls.makeitrun.data.TreadmillProfileRepository
import org.jls.makeitrun.ftms.DiscoveredTreadmill
import org.jls.makeitrun.ftms.FtmsScanner
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillConnectionState
import timber.log.Timber
import javax.inject.Inject

data class ConnectionUiState(
    val connection: TreadmillConnectionState = TreadmillConnectionState.Disconnected,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
    val showAllDevices: Boolean = false,
    val devices: List<DiscoveredTreadmill> = emptyList(),
    val profile: TreadmillProfile = TreadmillProfile(null, null, null, showPaceInsteadOfSpeed = true),
) {
    val isConnected: Boolean get() = connection is TreadmillConnectionState.Connected

    val hasKnownTreadmill: Boolean get() = !profile.isEmpty
}

@HiltViewModel
class ConnectionViewModel @Inject constructor(
    private val scanner: FtmsScanner,
    private val client: FtmsTreadmillClient,
    private val profileRepository: TreadmillProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectionUiState())
    val uiState = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            client.connectionState.collect { state ->
                if (state is TreadmillConnectionState.Failed) {
                    _messages.tryEmit(state.reason)
                }
                if (state is TreadmillConnectionState.Connected) {
                    rememberTreadmill(
                        address = state.address,
                        name = _uiState.value.devices
                            .firstOrNull { it.address == state.address }?.name,
                    )
                }
                _uiState.update {
                    it.copy(
                        connection = state,
                        devices = if (state is TreadmillConnectionState.Connected) {
                            emptyList()
                        } else {
                            it.devices
                        },
                        hasScanned = it.hasScanned &&
                            state !is TreadmillConnectionState.Connected,
                    )
                }
            }
        }
        viewModelScope.launch {
            profileRepository.profile.collect { profile ->
                _uiState.update { it.copy(profile = profile) }
            }
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun startScan() {
        scanJob?.cancel()
        _uiState.update { it.copy(isScanning = true, hasScanned = true, devices = emptyList()) }
        scanJob = viewModelScope.launch {
            scanner.scan(fitnessMachinesOnly = !_uiState.value.showAllDevices)
                .catch { error ->
                    Timber.e(error, "Le scan Bluetooth a echoue")
                    error.message?.let(_messages::tryEmit)
                    _uiState.update { it.copy(isScanning = false) }
                }
                .collect { devices -> _uiState.update { it.copy(devices = devices) } }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun setShowAllDevices(showAll: Boolean) {
        _uiState.update { it.copy(showAllDevices = showAll) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: DiscoveredTreadmill) = connect(device.address)

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun reconnectLastTreadmill() {
        _uiState.value.profile.address?.let(::connect)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connect(address: String) {
        val connection = _uiState.value.connection
        if (connection != TreadmillConnectionState.Disconnected &&
            connection !is TreadmillConnectionState.Failed
        ) {
            return
        }
        stopScan()
        viewModelScope.launch { client.connect(address) }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        client.disconnect()
    }

    private fun rememberTreadmill(address: String, name: String?) {
        viewModelScope.launch {
            profileRepository.rememberTreadmill(
                address = address,
                name = name ?: _uiState.value.profile.name,
                capabilities = client.capabilities.value,
            )
        }
    }
}
