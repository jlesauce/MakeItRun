package org.jls.makeitrun.treadmill

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jls.makeitrun.R
import org.jls.makeitrun.bluetooth.BluetoothPermissions
import org.jls.makeitrun.ftms.DiscoveredTreadmill
import org.jls.makeitrun.ftms.TreadmillConnectionState
import org.jls.makeitrun.ftms.TreadmillData

/**
 * Ecran de mise au point de la liaison FTMS : recherche, connexion, mesures en direct et
 * commandes de base. Il sera remplace par les vrais ecrans d'entrainement.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreadmillScreen(viewModel: TreadmillViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var hasPermissions by remember { mutableStateOf(BluetoothPermissions.allGranted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        if (!hasPermissions) {
            PermissionRequest(
                onRequest = { permissionLauncher.launch(BluetoothPermissions.required.toTypedArray()) },
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            TreadmillContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onRequest) {
            Text(stringResource(R.string.permission_grant))
        }
    }
}

// Les commandes Bluetooth ne sont atteignables que derriere le garde de permissions ci-dessus,
// ce que l'analyse statique ne peut pas deduire seule.
@SuppressLint("MissingPermission")
@Composable
private fun TreadmillContent(
    state: TreadmillUiState,
    viewModel: TreadmillViewModel,
    modifier: Modifier = Modifier,
) {
    val connected = state.connection as? TreadmillConnectionState.Connected

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            ConnectionStatusCard(
                connection = state.connection,
                onDisconnect = viewModel::disconnect,
            )
        }

        state.lastCommandResult?.let { message ->
            item {
                Text(text = message, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (connected != null) {
            item { MetricsCard(data = state.data) }
            if (connected.canBeControlled) {
                item {
                    ControlCard(
                        targetSpeedKmh = state.targetSpeedKmh,
                        onTargetSpeedChange = viewModel::setTargetSpeed,
                        onRequestControl = viewModel::requestControl,
                        onStart = viewModel::startBelt,
                        onStop = viewModel::stopBelt,
                        onApplySpeed = viewModel::applyTargetSpeed,
                    )
                }
            }
            state.lastRawFrame?.let { item { RawFrameCard(frame = it) } }
        } else {
            item {
                ScanControls(
                    isScanning = state.isScanning,
                    showAllDevices = state.showAllDevices,
                    onToggleScan = {
                        if (state.isScanning) viewModel.stopScan() else viewModel.startScan()
                    },
                    onShowAllDevicesChange = viewModel::setShowAllDevices,
                )
            }

            if (state.devices.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (state.isScanning) R.string.scan_searching else R.string.scan_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            items(state.devices, key = { it.address }) { device ->
                DeviceRow(device = device, onClick = { viewModel.connect(device) })
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    connection: TreadmillConnectionState,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val label = when (connection) {
                TreadmillConnectionState.Disconnected -> stringResource(R.string.status_disconnected)
                TreadmillConnectionState.Connecting -> stringResource(R.string.status_connecting)
                TreadmillConnectionState.DiscoveringServices ->
                    stringResource(R.string.status_discovering)

                is TreadmillConnectionState.Connected ->
                    stringResource(R.string.status_connected, connection.address)

                is TreadmillConnectionState.Failed ->
                    stringResource(R.string.status_failed, connection.reason)
            }
            Text(text = label, style = MaterialTheme.typography.titleMedium)

            if (connection is TreadmillConnectionState.Connected) {
                if (!connection.canBeControlled) {
                    Text(
                        text = stringResource(R.string.status_read_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(onClick = onDisconnect) {
                    Text(stringResource(R.string.disconnect))
                }
            }
        }
    }
}

@Composable
private fun ScanControls(
    isScanning: Boolean,
    showAllDevices: Boolean,
    onToggleScan: () -> Unit,
    onShowAllDevicesChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onToggleScan, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(if (isScanning) R.string.scan_stop else R.string.scan_start)
            )
        }
        if (isScanning) {
            CircularProgressIndicator(modifier = Modifier.padding(4.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.scan_show_all),
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(checked = showAllDevices, onCheckedChange = onShowAllDevicesChange)
        }
        Text(
            text = stringResource(R.string.scan_show_all_hint),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DeviceRow(device: DiscoveredTreadmill, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = device.name ?: stringResource(R.string.device_unknown_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(text = device.address, style = MaterialTheme.typography.bodySmall)
            }
            Column(horizontalAlignment = Alignment.End) {
                device.rssi?.let {
                    Text(
                        text = stringResource(R.string.device_rssi, it),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (device.advertisesFitnessMachine) {
                    Text(
                        text = stringResource(R.string.device_supports_ftms),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricsCard(data: TreadmillData?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.metrics_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            if (data == null) {
                Text(
                    text = stringResource(R.string.metric_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                )
                return@Column
            }

            MetricRow(R.string.metric_speed, data.instantaneousSpeedKmh.formatSpeed())
            MetricRow(R.string.metric_pace, data.paceSecondsPerKm.formatPace())
            MetricRow(R.string.metric_distance, data.totalDistanceMeters.formatDistance())
            MetricRow(R.string.metric_time, data.elapsedTimeSeconds.formatDuration())
            MetricRow(R.string.metric_inclination, data.inclinationPercent.formatInclination())
            MetricRow(R.string.metric_heart_rate, data.heartRateBpm.formatHeartRate())
            MetricRow(R.string.metric_energy, data.totalEnergyKcal.formatEnergy())
        }
    }
}

@Composable
private fun MetricRow(labelResId: Int, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = stringResource(labelResId), style = MaterialTheme.typography.bodyMedium)
        Text(text = value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ControlCard(
    targetSpeedKmh: Double,
    onTargetSpeedChange: (Double) -> Unit,
    onRequestControl: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onApplySpeed: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.control_title),
                style = MaterialTheme.typography.titleMedium,
            )
            HorizontalDivider()

            Button(onClick = onRequestControl, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.control_request))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.control_start))
                }
                Button(onClick = onStop, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.control_stop))
                }
            }

            Text(
                text = stringResource(
                    R.string.control_target_speed,
                    "%.1f".format(targetSpeedKmh),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = targetSpeedKmh.toFloat(),
                onValueChange = { onTargetSpeedChange(it.toDouble()) },
                valueRange = MIN_SPEED_KMH..MAX_SPEED_KMH,
                steps = SPEED_STEPS,
            )
            OutlinedButton(onClick = onApplySpeed, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.control_apply_speed))
            }
        }
    }
}

@Composable
private fun RawFrameCard(frame: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.raw_frame_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = frame,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = stringResource(R.string.raw_frame_hint),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Double?.formatSpeed(): String =
    this?.let { "%.1f km/h".format(it) } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatPace(): String =
    this?.let { "%d:%02d /km".format(it / 60, it % 60) }
        ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatDistance(): String = when {
    this == null -> stringResource(R.string.value_unavailable)
    this < 1000 -> "$this m"
    else -> "%.2f km".format(this / 1000.0)
}

@Composable
private fun Int?.formatDuration(): String =
    this?.let { "%02d:%02d".format(it / 60, it % 60) }
        ?: stringResource(R.string.value_unavailable)

@Composable
private fun Double?.formatInclination(): String =
    this?.let { "%.1f %%".format(it) } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatHeartRate(): String =
    this?.let { "$it bpm" } ?: stringResource(R.string.value_unavailable)

@Composable
private fun Int?.formatEnergy(): String =
    this?.let { "$it kcal" } ?: stringResource(R.string.value_unavailable)

private const val MIN_SPEED_KMH = 1f
private const val MAX_SPEED_KMH = 16f
private const val SPEED_STEPS = 29
