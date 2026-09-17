package org.jls.makeitrun.heartrate

import android.Manifest
import android.content.Context
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattCharacteristic
import no.nordicsemi.android.kotlin.ble.client.main.service.ClientBleGattServices
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import timber.log.Timber

class HeartRateClient(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val _connectionState =
        MutableStateFlow<HeartRateConnectionState>(HeartRateConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _sample = MutableStateFlow<HeartRateSample?>(null)
    val sample = _sample.asStateFlow()

    private val _batteryLevelPercent = MutableStateFlow<Int?>(null)
    val batteryLevelPercent = _batteryLevelPercent.asStateFlow()

    private val _lastRawFrame = MutableStateFlow<String?>(null)
    val lastRawFrame = _lastRawFrame.asStateFlow()

    private val _signalLost = MutableStateFlow(false)
    val signalLost = _signalLost.asStateFlow()

    private var gatt: ClientBleGatt? = null
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
            _connectionState.value = HeartRateConnectionState.Failed(failure)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun attemptConnection(
        address: String,
        announceProgress: Boolean,
    ): String? = connectionMutex.withLock {
        teardown()
        if (announceProgress) _connectionState.value = HeartRateConnectionState.Connecting

        try {
            val client = openGatt(address) ?: throw HeartRateException(
                "Le capteur n'a pas repondu a la demande de connexion. Verifiez qu'il diffuse " +
                    "toujours sa frequence cardiaque et qu'aucun autre appareil ne l'utilise."
            )
            gatt = client
            observeDisconnection(client)

            if (announceProgress) {
                _connectionState.value = HeartRateConnectionState.DiscoveringServices
            }
            val services = withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) { client.discoverServices() }
                ?: throw HeartRateException(
                    "Le capteur n'a pas publie ses services dans le temps imparti."
                )
            val service = services.findService(HeartRateUuids.HEART_RATE_SERVICE)
                ?: throw HeartRateException(
                    "Cet appareil n'expose pas le service Heart Rate (0x180D). Sur une montre " +
                        "Garmin, verifie que la diffusion de la frequence cardiaque est activee."
                )

            val measurement = service.findCharacteristic(HeartRateUuids.HEART_RATE_MEASUREMENT)
                ?: throw HeartRateException(
                    "Le service Heart Rate ne publie pas la caracteristique de mesure (0x2A37)."
                )
            observeMeasurements(measurement)

            readBatteryLevel(services)

            if (!client.isConnected) {
                throw HeartRateException(
                    "La liaison avec le capteur s'est fermee pendant la connexion."
                )
            }

            Timber.i("Capteur de frequence cardiaque connecte a %s", address)
            _connectionState.value = HeartRateConnectionState.Connected(address)
            watchForSilence(SystemClock.elapsedRealtime())
            null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.e(error, "Echec de la connexion au capteur de frequence cardiaque %s", address)
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
            Timber.w("Connexion au capteur %s jamais aboutie, tentative abandonnee", address)
            return
        }

        Timber.w("Connexion au capteur %s arrivee apres le delai, fermeture", address)
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
        }.onFailure { Timber.d(it, "Fermeture de la liaison GATT du capteur cardiaque") }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scheduleReconnection(address: String) {
        reconnectJob?.cancel()
        _connectionState.value = HeartRateConnectionState.Reconnecting
        reconnectJob = scope.launch {
            repeat(RECONNECTION_ATTEMPTS) { attempt ->
                delay(RECONNECTION_DELAY_MILLIS)
                if (desiredAddress != address) return@launch

                Timber.i("Reconnexion au capteur %s, tentative %d", address, attempt + 1)
                if (attemptConnection(address, announceProgress = false) == null) return@launch
                _connectionState.value = HeartRateConnectionState.Reconnecting
            }
            _connectionState.value = HeartRateConnectionState.Failed(
                "La liaison avec le capteur a ete perdue et n'a pas pu etre retablie."
            )
        }
    }

    private fun watchForSilence(connectedAtMillis: Long) {
        observerJobs += scope.launch {
            while (true) {
                delay(SILENCE_CHECK_INTERVAL_MILLIS)
                val lastActivity = _sample.value?.receivedAtElapsedMillis ?: connectedAtMillis
                if (SystemClock.elapsedRealtime() - lastActivity < SILENCE_TIMEOUT_MILLIS) continue

                if (!_signalLost.value) {
                    Timber.w("Plus aucune mesure cardiaque, la montre ne diffuse plus")
                }
                _signalLost.value = true
                _sample.value = null
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        desiredAddress = null
        reconnectJob?.cancel()
        reconnectJob = null
        _connectionState.value = HeartRateConnectionState.Disconnected

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
        _signalLost.value = false
        _sample.value = null
        _batteryLevelPercent.value = null
        _lastRawFrame.value = null

        val client = gatt ?: return
        gatt = null
        closeQuietly(client)
        delay(GATT_SETTLE_MILLIS)
    }

    private fun observeMeasurements(characteristic: ClientBleGattCharacteristic) {
        observerJobs += scope.launch {
            collectUntilDisconnected("mesures cardiaques") {
                characteristic.getNotifications().collect { frame ->
                    val hex = frame.value.toHexString()
                    _lastRawFrame.value = hex
                    val measurement = HeartRateMeasurementParser.parse(frame.value)
                    Timber.d(
                        "Heart Rate <- %s | %s",
                        hex,
                        measurement?.let { "${it.beatsPerMinute} bpm, contact ${it.sensorContact}" }
                            ?: "trame illisible",
                    )
                    measurement?.let {
                        _sample.value = HeartRateSample(
                            measurement = it,
                            receivedAtElapsedMillis = SystemClock.elapsedRealtime(),
                        )
                        _signalLost.value = false
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private suspend fun readBatteryLevel(services: ClientBleGattServices) {
        val characteristic = services.findService(HeartRateUuids.BATTERY_SERVICE)
            ?.findCharacteristic(HeartRateUuids.BATTERY_LEVEL)
            ?: return

        _batteryLevelPercent.value = withTimeoutOrNull(CHARACTERISTIC_READ_TIMEOUT_MS) {
            runCatching { characteristic.read().value.firstOrNull()?.toInt()?.and(0xFF) }
                .onFailure { Timber.d(it, "Niveau de batterie illisible") }
                .getOrNull()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun observeDisconnection(client: ClientBleGatt) {
        observerJobs += scope.launch {
            client.connectionState.collect { state ->
                if (state != GattConnectionState.STATE_DISCONNECTED) return@collect

                val wasEstablished = _connectionState.value is HeartRateConnectionState.Connected
                if (_connectionState.value !is HeartRateConnectionState.Failed) {
                    _connectionState.value = HeartRateConnectionState.Disconnected
                    _sample.value = null
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

    private fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }

    private companion object {
        const val CONNECTION_TIMEOUT_MS = 15_000L
        const val LATE_CONNECTION_GRACE_MS = 45_000L
        const val DISCONNECTION_TIMEOUT_MS = 2_000L
        const val GATT_SETTLE_MILLIS = 600L
        const val DISCOVERY_TIMEOUT_MS = 15_000L
        const val CHARACTERISTIC_READ_TIMEOUT_MS = 5_000L
        const val RECONNECTION_ATTEMPTS = 3
        const val RECONNECTION_DELAY_MILLIS = 5_000L
        const val SILENCE_TIMEOUT_MILLIS = 15_000L
        const val SILENCE_CHECK_INTERVAL_MILLIS = 2_000L
    }
}
