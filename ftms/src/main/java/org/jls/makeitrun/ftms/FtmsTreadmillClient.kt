package org.jls.makeitrun.ftms

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.core.data.BleWriteType
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import no.nordicsemi.android.kotlin.ble.core.data.util.DataByteArray
import kotlin.math.roundToInt

/**
 * Liaison avec un tapis de course parlant FTMS.
 *
 * Une seule machine est pilotee a la fois : l'instance est donc destinee a etre unique dans
 * l'application, et a survivre aux changements d'ecran pour que la seance ne soit pas coupee.
 *
 * Le pilotage suit toujours le meme ordre impose par la specification : connexion, puis
 * [requestControl], et seulement ensuite les commandes de vitesse ou de demarrage.
 */
class FtmsTreadmillClient(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _connectionState =
        MutableStateFlow<TreadmillConnectionState>(TreadmillConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _treadmillData = MutableStateFlow<TreadmillData?>(null)
    val treadmillData = _treadmillData.asStateFlow()

    /** Derniere trame brute recue, en hexadecimal. Sert au diagnostic face a une vraie machine. */
    private val _lastRawFrame = MutableStateFlow<String?>(null)
    val lastRawFrame = _lastRawFrame.asStateFlow()

    private val _controlResponses = MutableSharedFlow<FtmsControlResponse>(extraBufferCapacity = 8)
    val controlResponses = _controlResponses.asSharedFlow()

    private var gatt: ClientBleGatt? = null
    private var controlPoint: ClientBleGattCharacteristic? = null
    private val observerJobs = mutableListOf<Job>()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connect(address: String) {
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

            _connectionState.value = TreadmillConnectionState.Connected(
                address = address,
                canBeControlled = controlPoint != null,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
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
        gatt?.let {
            if (it.isConnected) it.disconnect()
            it.close()
        }
        gatt = null
        _treadmillData.value = null
        _lastRawFrame.value = null
        _connectionState.value = TreadmillConnectionState.Disconnected
    }

    /**
     * Demande la main sur la machine. Tant que cette commande n'a pas abouti, le tapis rejette
     * toute commande de pilotage avec [FtmsResultCode.CONTROL_NOT_PERMITTED].
     */
    suspend fun requestControl(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.REQUEST_CONTROL.value))

    suspend fun start(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.START_OR_RESUME.value))

    suspend fun stop(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.STOP_OR_PAUSE.value, STOP_PARAMETER))

    suspend fun pause(): FtmsControlResponse? =
        writeCommand(DataByteArray.opCode(FtmsOpCode.STOP_OR_PAUSE.value, PAUSE_PARAMETER))

    /** @param speedKmh vitesse cible en km/h, arrondie au centieme transmis a la machine. */
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

    /** @param inclinationPercent pente cible en pourcentage, negative en descente. */
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

    /**
     * Ecrit une commande puis attend l'indication de reponse correspondante.
     *
     * @return la reponse de la machine, ou `null` si le Control Point est absent ou si la
     * machine n'a pas repondu dans le delai imparti.
     */
    private suspend fun writeCommand(command: DataByteArray): FtmsControlResponse? {
        val characteristic = controlPoint ?: return null
        val sentOpCode = FtmsOpCode.fromValue(command.value[0])

        characteristic.write(command, BleWriteType.DEFAULT)

        return withTimeoutOrNull(CONTROL_RESPONSE_TIMEOUT_MS) {
            controlResponses.first { it.requestOpCode == sentOpCode }
        }
    }

    private fun observeTreadmillData(characteristic: ClientBleGattCharacteristic) {
        observerJobs += scope.launch {
            characteristic.getNotifications().collect { frame ->
                _lastRawFrame.value = frame.value.toHexString()
                TreadmillDataParser.parse(frame.value)?.let { _treadmillData.value = it }
            }
        }
    }

    private fun observeControlResponses(characteristic: ClientBleGattCharacteristic) {
        observerJobs += scope.launch {
            characteristic.getNotifications().collect { frame ->
                FtmsControlResponse.parse(frame.value)?.let { _controlResponses.emit(it) }
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

    private fun Int.lowByte(): Byte = (this and 0xFF).toByte()

    private fun Int.highByte(): Byte = ((this shr 8) and 0xFF).toByte()

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    private companion object {
        const val CONTROL_RESPONSE_TIMEOUT_MS = 3_000L
        const val STOP_PARAMETER: Byte = 0x01
        const val PAUSE_PARAMETER: Byte = 0x02
    }
}
