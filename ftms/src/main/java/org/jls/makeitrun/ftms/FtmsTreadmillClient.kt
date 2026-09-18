package org.jls.makeitrun.ftms

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

    private var desiredAddress: String? = null
    private var reconnectJob: Job? = null
    private var disconnectJob: Job? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(address: String) {
        reconnectJob?.cancel()
        desiredAddress = address
        attemptConnection(address, announceProgress = true)?.let { failure ->
            _connectionState.value = TreadmillConnectionState.Failed(failure)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun attemptConnection(
        address: String,
        announceProgress: Boolean,
    ): String? = connectionMutex.withLock {
        teardown()
        if (announceProgress) _connectionState.value = TreadmillConnectionState.Connecting

        try {
            val client = openGatt(address) ?: throw FtmsException(
                "Le tapis n'a pas repondu a la demande de connexion. Verifiez qu'il est allume " +
                    "et qu'aucune autre application n'y est connectee."
            )
            gatt = client
            observeDisconnection(client)

            if (announceProgress) {
                _connectionState.value = TreadmillConnectionState.DiscoveringServices
            }
            val services = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { client.discoverServices() }
                ?: throw FtmsException(
                    "Le tapis n'a pas publie ses services dans le temps imparti."
                )
            delay(POST_DISCOVERY_SETTLE_MILLIS)
            val service = services.findService(FtmsUuids.FITNESS_MACHINE_SERVICE)
                ?: throw FtmsException(
                    "Cet appareil n'expose pas le service Fitness Machine (0x1826)."
                )

            val data = service.findCharacteristic(FtmsUuids.TREADMILL_DATA)
                ?: throw FtmsException(
                    "Le service FTMS ne publie pas la caracteristique Treadmill Data (0x2ACD)."
                )

            readCapabilities(service)?.let { _capabilities.value = it }

            observeTreadmillData(data)
            controlPoint = service.findCharacteristic(FtmsUuids.FITNESS_MACHINE_CONTROL_POINT)
                ?.also { observeControlResponses(it) }

            if (!client.isConnected) {
                throw FtmsException(
                    "La liaison avec le tapis s'est fermee pendant la lecture de ses capacites."
                )
            }

            Timber.i("Connecte a %s, pilotage %s", address, if (controlPoint != null) "disponible" else "indisponible")
            _connectionState.value = TreadmillConnectionState.Connected(
                address = address,
                canBeControlled = controlPoint != null,
            )
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.e(error, "Echec de la connexion a %s", address)
            teardown()
            error.message ?: error::class.java.simpleName
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun openGatt(address: String): ClientBleGatt? {
        val pending = scope.async { ClientBleGatt.connect(context, address, scope) }
        val client = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) { pending.await() }

        if (client == null) {
            scope.launch { discardLateConnection(pending, address) }
            return null
        }
        if (!client.isConnected) {
            closeQuietly(client)
            return null
        }
        return client
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun discardLateConnection(pending: Deferred<ClientBleGatt>, address: String) {
        val late = withTimeoutOrNull(LATE_CONNECTION_GRACE_MS) { runCatching { pending.await() } }
            ?.getOrNull()

        if (late == null) {
            pending.cancel()
            Timber.w("Connexion a %s jamais aboutie, tentative abandonnee", address)
            return
        }

        Timber.w("Connexion a %s arrivee apres le delai, fermeture de la liaison", address)
        closeQuietly(late)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun closeQuietly(client: ClientBleGatt) {
        runCatching {
            if (client.isConnected) {
                client.disconnect()
                withTimeoutOrNull(DISCONNECTION_TIMEOUT_MS) {
                    client.connectionState.first { it == GattConnectionState.STATE_DISCONNECTED }
                }
            }
            client.close()
        }.onFailure { Timber.d(it, "Fermeture de la liaison GATT") }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scheduleReconnection(address: String) {
        reconnectJob?.cancel()
        _connectionState.value = TreadmillConnectionState.Reconnecting
        reconnectJob = scope.launch {
            repeat(RECONNECTION_ATTEMPTS) { attempt ->
                delay(RECONNECTION_DELAY_MILLIS)
                if (desiredAddress != address) return@launch

                Timber.i("Reconnexion au tapis %s, tentative %d", address, attempt + 1)
                if (attemptConnection(address, announceProgress = false) == null) return@launch
                _connectionState.value = TreadmillConnectionState.Reconnecting
            }
            _connectionState.value = TreadmillConnectionState.Failed(
                "La liaison avec le tapis a ete perdue et n'a pas pu etre retablie."
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        desiredAddress = null
        reconnectJob?.cancel()
        reconnectJob = null
        _connectionState.value = TreadmillConnectionState.Disconnected

        disconnectJob?.cancel()
        disconnectJob = scope.launch {
            connectionMutex.withLock {
                if (desiredAddress == null) teardown()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun teardown() {
        observerJobs.forEach { it.cancel() }
        observerJobs.clear()
        controlPoint = null
        _treadmillData.value = null
        _lastRawFrame.value = null

        val client = gatt ?: return
        gatt = null
        closeQuietly(client)
        delay(GATT_SETTLE_MILLIS)
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

        val response = withTimeoutOrNull(CONTROL_COMMAND_TIMEOUT_MS) {
            coroutineScope {
                val pendingResponse = async(start = CoroutineStart.UNDISPATCHED) {
                    _controlResponses.first { it.requestOpCode == sentOpCode }
                }

                Timber.d("Control Point -> %s", command.value.toHexString())
                characteristic.write(command, BleWriteType.DEFAULT)

                pendingResponse.await()
            }
        }

        if (response == null) {
            Timber.w(
                "Le tapis n'a pas traite la commande %s dans le temps imparti",
                command.value.toHexString(),
            )
        }
        return response
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun readCapabilities(
        service: ClientBleGattService,
    ): TreadmillCapabilities? {
        val featurePayload = read(service, FtmsUuids.FITNESS_MACHINE_FEATURE)
        val speedRangePayload = read(service, FtmsUuids.SUPPORTED_SPEED_RANGE)
        val inclinationRangePayload = read(service, FtmsUuids.SUPPORTED_INCLINATION_RANGE)

        if (featurePayload == null && speedRangePayload == null) {
            Timber.w("Capacites du tapis illisibles, celles deja connues sont conservees")
            return null
        }

        return TreadmillCapabilitiesParser.parse(
            featurePayload = featurePayload,
            speedRangePayload = speedRangePayload,
            inclinationRangePayload = inclinationRangePayload,
        ).also { Timber.i("Capacites du tapis : %s", it) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun read(service: ClientBleGattService, uuid: UUID): ByteArray? {
        val characteristic = service.findCharacteristic(uuid) ?: return null

        repeat(CHARACTERISTIC_READ_ATTEMPTS) { attempt ->
            val payload = withTimeoutOrNull(CHARACTERISTIC_READ_TIMEOUT_MS) {
                runCatching { characteristic.read().value }
                    .onFailure { Timber.w(it, "Lecture interrompue de %s", uuid) }
                    .getOrNull()
            }
            if (payload != null) return payload

            Timber.w("Lecture de %s sans reponse, tentative %d", uuid, attempt + 1)
            delay(CHARACTERISTIC_READ_RETRY_MILLIS)
        }
        return null
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun observeDisconnection(client: ClientBleGatt) {
        observerJobs += scope.launch {
            client.connectionState.collect { state ->
                if (state != GattConnectionState.STATE_DISCONNECTED) return@collect

                val wasEstablished =
                    _connectionState.value is TreadmillConnectionState.Connected
                if (_connectionState.value !is TreadmillConnectionState.Failed) {
                    _connectionState.value = TreadmillConnectionState.Disconnected
                    _treadmillData.value = null
                }

                val address = desiredAddress
                if (wasEstablished && address != null) scheduleReconnection(address)
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
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val LATE_CONNECTION_GRACE_MS = 45_000L
        const val DISCONNECTION_TIMEOUT_MS = 2_000L
        const val GATT_SETTLE_MILLIS = 600L
        const val POST_DISCOVERY_SETTLE_MILLIS = 400L
        const val DISCOVERY_TIMEOUT_MS = 15_000L
        const val CHARACTERISTIC_READ_TIMEOUT_MS = 5_000L
        const val CHARACTERISTIC_READ_ATTEMPTS = 2
        const val CHARACTERISTIC_READ_RETRY_MILLIS = 500L
        const val CONTROL_COMMAND_TIMEOUT_MS = 5_000L
        const val RECONNECTION_ATTEMPTS = 3
        const val RECONNECTION_DELAY_MILLIS = 5_000L
        const val STOP_PARAMETER: Byte = 0x01
        const val PAUSE_PARAMETER: Byte = 0x02
    }
}
