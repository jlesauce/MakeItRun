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

class FtmsScanner(private val context: Context) {

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
            scanMode = BleScanMode.SCAN_MODE_LOW_LATENCY,
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
