package org.jls.makeitrun.treadmill

import android.Manifest
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jls.makeitrun.ftms.DiscoveredTreadmill
import org.jls.makeitrun.ftms.FtmsControlResponse
import org.jls.makeitrun.ftms.FtmsScanner
import org.jls.makeitrun.ftms.FtmsTreadmillClient
import org.jls.makeitrun.ftms.TreadmillConnectionState
import org.jls.makeitrun.ftms.TreadmillData
import timber.log.Timber
import javax.inject.Inject

data class TreadmillUiState(
    val isScanning: Boolean = false,
    val showAllDevices: Boolean = false,
    val devices: List<DiscoveredTreadmill> = emptyList(),
    val connection: TreadmillConnectionState = TreadmillConnectionState.Disconnected,
    val data: TreadmillData? = null,
    val lastRawFrame: String? = null,
    val targetSpeedKmh: Double = 6.0,
    val lastCommandResult: String? = null,
)

@HiltViewModel
class TreadmillViewModel @Inject constructor(
    private val scanner: FtmsScanner,
    private val client: FtmsTreadmillClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(TreadmillUiState())
    val uiState = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            client.connectionState.collect { state ->
                _uiState.update { it.copy(connection = state) }
            }
        }
        viewModelScope.launch {
            client.treadmillData.collect { data ->
                _uiState.update { it.copy(data = data) }
            }
        }
        viewModelScope.launch {
            client.lastRawFrame.collect { frame ->
                _uiState.update { it.copy(lastRawFrame = frame) }
            }
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun startScan() {
        scanJob?.cancel()
        _uiState.update {
            it.copy(isScanning = true, devices = emptyList(), lastCommandResult = null)
        }
        scanJob = viewModelScope.launch {
            scanner.scan(fitnessMachinesOnly = !_uiState.value.showAllDevices)
                .catch { error ->
                    Timber.e(error, "Le scan Bluetooth a echoue")
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            lastCommandResult = "Scan impossible : ${error.message}",
                        )
                    }
                }
                .collect { devices ->
                    _uiState.update { it.copy(devices = devices) }
                }
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
    fun connect(device: DiscoveredTreadmill) {
        // Un second appui pendant la connexion ouvrirait une deuxieme liaison GATT vers la
        // meme machine, que plus rien ne refermerait ensuite.
        if (_uiState.value.connection != TreadmillConnectionState.Disconnected &&
            _uiState.value.connection !is TreadmillConnectionState.Failed
        ) {
            return
        }
        stopScan()
        viewModelScope.launch { client.connect(device.address) }
    }

    fun disconnect() {
        client.disconnect()
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

    /**
     * Exécute une commande de pilotage et transforme la réponse de la machine en message lisible.
     */
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
