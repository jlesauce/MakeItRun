package org.jls.makeitrun.heartrate

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

class HeartRateScanner(private val context: Context) {

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    fun scan(heartRateSensorsOnly: Boolean = true): Flow<List<DiscoveredHeartRateSensor>> = flow {
        val aggregator = BleScanResultAggregator()
        val filters = if (heartRateSensorsOnly) {
            listOf(
                BleScanFilter(
                    serviceUuid = FilteredServiceUuid(
                        ParcelUuid(HeartRateUuids.HEART_RATE_SERVICE)
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
            val sensors = aggregator.aggregate(result)
                .map { it.toDiscoveredHeartRateSensor() }
                .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            emit(sensors)
        }
    }

    private fun BleScanResults.toDiscoveredHeartRateSensor(): DiscoveredHeartRateSensor {
        val advertisedServices = lastScanResult?.scanRecord?.serviceUuids.orEmpty()
        return DiscoveredHeartRateSensor(
            name = advertisedName ?: device.name,
            address = device.address,
            rssi = scanResult.lastOrNull()?.rssi,
            advertisesHeartRate = advertisedServices.any {
                it.uuid == HeartRateUuids.HEART_RATE_SERVICE
            },
        )
    }
}
