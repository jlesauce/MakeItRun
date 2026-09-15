package org.jls.makeitrun.ftms

import android.Manifest
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanFilter
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanMode
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScanResults
import no.nordicsemi.android.kotlin.ble.core.scanner.BleScannerSettings
import no.nordicsemi.android.kotlin.ble.core.scanner.FilteredServiceUuid
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import no.nordicsemi.android.kotlin.ble.scanner.aggregator.BleScanResultAggregator

/**
 * Recherche les tapis de course a portee.
 *
 * Le scan s'arrete automatiquement des que la collecte du [Flow] cesse : il suffit donc de
 * l'englober dans un scope qui vit le temps de l'ecran de recherche.
 */
class FtmsScanner(private val context: Context) {

    /**
     * @param fitnessMachinesOnly si vrai, seuls les appareils annoncant le service FTMS sont
     * remontes. Passer `false` permet de lister tous les appareils BLE alentour, ce qui aide
     * a diagnostiquer un tapis qui n'annoncerait pas le service dans son advertising.
     * @return un [Flow] qui reemet la liste complete des appareils connus a chaque detection,
     * triee du signal le plus fort au plus faible.
     */
    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun scan(fitnessMachinesOnly: Boolean = true): Flow<List<DiscoveredTreadmill>> = flow {
        val aggregator = BleScanResultAggregator()
        val filters = if (fitnessMachinesOnly) {
            listOf(
                BleScanFilter(
                    serviceUuid = FilteredServiceUuid(
                        ParcelUuid(FtmsUuids.FITNESS_MACHINE_SERVICE)
                    )
                )
            )
        } else {
            emptyList()
        }

        val settings = BleScannerSettings(
            // L'ecran de recherche est au premier plan : on privilegie la reactivite.
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
            // Les appareils apparies sont remontes sans passer par les filtres de service,
            // ce qui polluerait la liste filtree.
            includeStoredBondedDevices = false,
        )

        BleScanner(context).scan(filters, settings).collect { result ->
            val devices = aggregator.aggregate(result)
                .map { it.toDiscoveredTreadmill() }
                .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            emit(devices)
        }
    }

    private fun BleScanResults.toDiscoveredTreadmill(): DiscoveredTreadmill {
        val advertisedServices = lastScanResult?.scanRecord?.serviceUuids.orEmpty()
        return DiscoveredTreadmill(
            name = advertisedName ?: device.name,
            address = device.address,
            rssi = scanResult.lastOrNull()?.rssi,
            advertisesFitnessMachine = advertisedServices.any {
                it.uuid == FtmsUuids.FITNESS_MACHINE_SERVICE
            },
        )
    }
}
