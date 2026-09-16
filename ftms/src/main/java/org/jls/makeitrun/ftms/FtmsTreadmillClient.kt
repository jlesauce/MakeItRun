package org.jls.makeitrun.ftms

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattService
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import timber.log.Timber
import java.util.UUID
import kotlin.math.roundToInt

class FtmsTreadmillClient(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _connectionState =
        MutableStateFlow<TreadmillConnectionState>(TreadmillConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _treadmillData = MutableStateFlow<TreadmillData?>(null)
    val treadmillData = _treadmillData.asStateFlow()

    private val _lastRawFrame = MutableStateFlow<String?>(null)
    val lastRawFrame = _lastRawFrame.asStateFlow()

    private val _controlResponses = MutableSharedFlow<FtmsControlResponse>(extraBufferCapacity = 8)
    val controlResponses = _controlResponses.asSharedFlow()

    private val _capabilities = MutableStateFlow<TreadmillCapabilities?>(null)
    val capabilities = _capabilities.asStateFlow()

    private var gatt: ClientBleGatt? = null
    private var controlPoint: ClientBleGattCharacteristic? = null
    private val observerJobs = mutableListOf<Job>()

    private val connectionMutex = Mutex()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(address: String) = connectionMutex.withLock {
        disconnect()
        _connectionState.value = TreadmillConnectionState.Connecting

        try {
            val client = ClientBleGatt.connect(context, address, scope)
            gatt = client
            observeDisconnection(client)

            _connectionState.value = TreadmillConnectionState.DiscoveringServices
            val service = client.discoverServices()
                .findService(FtmsUuids.FITNESS_MACHINE_SERVICE)
                ?: throw FtmsException(
                    "Cet appareil n'expose pas le service Fitness Machine (0x1826)."
                )

            val data = service.findCharacteristic(FtmsUuids.TREADMILL_DATA)
                ?: throw FtmsException(
                    "Le service FTMS ne publie pas la caracteristique Treadmill Data (0x2ACD)."
                )
            observeTreadmillData(data)

            controlPoint = service.findCharacteristic(FtmsUuids.FITNESS_MACHINE_CONTROL_POINT)
                ?.also { observeControlResponses(it) }

            _capabilities.value = readCapabilities(service)

            Timber.i("Connecte a %s, pilotage %s", address, if (controlPoint != null) "disponible" else "indisponible")
            _connectionState.value = TreadmillConnectionState.Connected(
                address = address,
                canBeControlled = controlPoint != null,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.e(error, "Echec de la connexion a %s", address)
            disconnect()
            _connectionState.value = TreadmillConnectionState.Failed(
                error.message ?: error::class.java.simpleName
            )
        }
    }

    fun disconnect() {
        observerJobs.forEach { it.cancel() }
        observerJobs.clear()
        controlPoint = null
        gatt?.let { client ->
            runCatching {
                if (client.isConnected) client.disconnect()
                client.close()
            }.onFailure { Timber.d(it, "Fermeture de la liaison GATT") }
        }
        gatt = null
        _treadmillData.value = null
        _lastRawFrame.value = null
        _connectionState.value = TreadmillConnectionState.Disconnected
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun requestControl(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.REQUEST_CONTROL.value))

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun start(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.START_OR_RESUME.value))

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun stop(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.STOP_OR_PAUSE.value, STOP_PARAMETER))

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun pause(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.STOP_OR_PAUSE.value, PAUSE_PARAMETER))

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun setTargetSpeed(speedKmh: Double): FtmsControlResponse? {
        val hundredths = (speedKmh * 100).roundToInt()
        return writeCommand(
            DataByteArray(
                byteArrayOf(
                    FtmsOpCode.SET_TARGET_SPEED.value,
                    hundredths.lowByte(),
                    hundredths.highByte(),
                )
            )
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun setTargetInclination(inclinationPercent: Double): FtmsControlResponse? {
        val tenths = (inclinationPercent * 10).roundToInt()
        return writeCommand(
            DataByteArray(
                byteArrayOf(
                    FtmsOpCode.SET_TARGET_INCLINATION.value,
                    tenths.lowByte(),
                    tenths.highByte(),
                )
            )
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun writeCommand(command: DataByteArray): FtmsControlResponse? {
        val characteristic = controlPoint ?: return null
        val sentOpCode = FtmsOpCode.fromValue(command.value[0])

        return coroutineScope {
            val pendingResponse = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(CONTROL_RESPONSE_TIMEOUT_MS) {
                    _controlResponses.first { it.requestOpCode == sentOpCode }
                }
            }

            Timber.d("Control Point -> %s", command.value.toHexString())
            characteristic.write(command, BleWriteType.DEFAULT)

            pendingResponse.await()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun readCapabilities(
        service: ClientBleGattService,
    ): TreadmillCapabilities {
        suspend fun read(uuid: UUID): ByteArray? =
            service.findCharacteristic(uuid)?.let { characteristic ->
                runCatching { characteristic.read().value }
                    .onFailure { Timber.d(it, "Lecture impossible de %s", uuid) }
                    .getOrNull()
            }

        val capabilities = TreadmillCapabilitiesParser.parse(
            featurePayload = read(FtmsUuids.FITNESS_MACHINE_FEATURE),
            speedRangePayload = read(FtmsUuids.SUPPORTED_SPEED_RANGE),
            inclinationRangePayload = read(FtmsUuids.SUPPORTED_INCLINATION_RANGE),
        )
        Timber.i("Capacites du tapis : %s", capabilities)
        return capabilities
    }

    private fun observeTreadmillData(characteristic: ClientBleGattCharacteristic) {
        observerJobs += scope.launch {
            collectUntilDisconnected("mesures du tapis") {
                characteristic.getNotifications().collect { frame ->
                    val hex = frame.value.toHexString()
                    _lastRawFrame.value = hex
                    val data = TreadmillDataParser.parse(frame.value)
                    Timber.d("Treadmill Data <- %s | %s", hex, data?.summary() ?: "trame illisible")
                    data?.let { _treadmillData.value = it }
                }
            }
        }
    }

    private fun observeControlResponses(characteristic: ClientBleGattCharacteristic) {
        observerJobs += scope.launch {
            collectUntilDisconnected("reponses de pilotage") {
                characteristic.getNotifications().collect { frame ->
                    Timber.d("Control Point <- %s", frame.value.toHexString())
                    FtmsControlResponse.parse(frame.value)?.let { _controlResponses.emit(it) }
                }
            }
        }
    }

    private fun observeDisconnection(client: ClientBleGatt) {
        observerJobs += scope.launch {
            client.connectionState.collect { state ->
                if (state == GattConnectionState.STATE_DISCONNECTED &&
                    _connectionState.value !is TreadmillConnectionState.Failed
                ) {
                    _connectionState.value = TreadmillConnectionState.Disconnected
                    _treadmillData.value = null
                }
            }
        }
    }

    private suspend fun collectUntilDisconnected(label: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.d(error, "Fin de l'abonnement aux %s", label)
        }
    }

    private fun TreadmillData.summary(): String = buildString {
        append("vitesse=").append(instantaneousSpeedKmh ?: "-")
        append(" distance=").append(totalDistanceMeters ?: "-")
        append(" temps=").append(elapsedTimeSeconds ?: "-")
        append(" pente=").append(inclinationPercent ?: "-")
        append(" fc=").append(heartRateBpm ?: "-")
    }

    private fun Int.lowByte(): Byte = (this and 0xFF).toByte()

    private fun Int.highByte(): Byte = ((this shr 8) and 0xFF).toByte()

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    private companion object {
        const val CONTROL_RESPONSE_TIMEOUT_MS = 3_000L
        const val STOP_PARAMETER: Byte = 0x01
        const val PAUSE_PARAMETER: Byte = 0x02
    }
}
