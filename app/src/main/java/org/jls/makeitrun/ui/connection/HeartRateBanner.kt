package org.jls.makeitrun.ui.connection

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jls.makeitrun.R
import org.jls.makeitrun.heartrate.DiscoveredHeartRateSensor
import org.jls.makeitrun.heartrate.HeartRateConnectionState

@SuppressLint("MissingPermission")
@Composable
fun HeartRateBanner(
    state: HeartRateConnectionUiState,
    viewModel: HeartRateConnectionViewModel,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusLed(connection = state.connection, isSilent = state.isSilent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.connection.label(state.profile.name),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.isConnected) {
                        Text(
                            text = state.liveSummary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                ConnectedAction(state = state, viewModel = viewModel)
            }

            AnimatedVisibility(visible = !state.isConnected) {
                DisconnectedActions(state = state, viewModel = viewModel)
            }

            AnimatedVisibility(
                visible = !state.isConnected && (state.isScanning || state.sensors.isNotEmpty())
            ) {
                SensorList(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun HeartRateConnectionUiState.liveSummary(): String {
    if (isSilent) return stringResource(R.string.heart_rate_no_frames)
    val beats = beatsPerMinute ?: return stringResource(R.string.heart_rate_waiting)
    val battery = batteryLevelPercent
        ?: return stringResource(R.string.heart_rate_bpm, beats)
    return stringResource(R.string.heart_rate_bpm_with_battery, beats, battery)
}

@Composable
private fun ConnectedAction(
    state: HeartRateConnectionUiState,
    viewModel: HeartRateConnectionViewModel,
) {
    when {
        state.isConnected -> TextButton(onClick = viewModel::disconnect) {
            Text(stringResource(R.string.connection_disconnect))
        }

        state.connection is HeartRateConnectionState.Reconnecting -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            IconButton(onClick = viewModel::disconnect) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.connection_stop_reconnecting),
                )
            }
        }

        state.connection is HeartRateConnectionState.Connecting ||
            state.connection is HeartRateConnectionState.DiscoveringServices ->
            CircularProgressIndicator(modifier = Modifier.size(20.dp))

        else -> Unit
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DisconnectedActions(
    state: HeartRateConnectionUiState,
    viewModel: HeartRateConnectionViewModel,
) {
    val isBusy = state.connection is HeartRateConnectionState.Connecting ||
        state.connection is HeartRateConnectionState.DiscoveringServices ||
        state.connection is HeartRateConnectionState.Reconnecting
    if (isBusy) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isScanning) {
            OutlinedButton(onClick = viewModel::stopScan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connection_cancel))
            }
            return@Row
        }

        if (state.profile.address != null) {
            Button(onClick = viewModel::reconnectLastSensor, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connection_reconnect), maxLines = 1)
            }
            OutlinedButton(onClick = viewModel::startScan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connection_search), maxLines = 1)
            }
        } else {
            Button(onClick = viewModel::startScan, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.connection_search), maxLines = 1)
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun SensorList(
    state: HeartRateConnectionUiState,
    viewModel: HeartRateConnectionViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (state.isScanning && state.sensors.isEmpty()) {
            Text(
                text = stringResource(R.string.connection_searching),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.sensors, key = { it.address }) { sensor ->
                SensorRow(sensor = sensor, onClick = { viewModel.connect(sensor) })
            }
        }

        if (!state.isScanning && state.sensors.isEmpty()) {
            Text(
                text = stringResource(R.string.heart_rate_no_sensor),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SensorRow(sensor: DiscoveredHeartRateSensor, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = sensor.name ?: stringResource(R.string.device_unknown_name),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(text = sensor.address, style = MaterialTheme.typography.bodySmall)
            }
            sensor.rssi?.let {
                Text(
                    text = stringResource(R.string.device_rssi, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun StatusLed(connection: HeartRateConnectionState, isSilent: Boolean) {
    val description = stringResource(R.string.heart_rate_led_description)
    val color = when (connection) {
        is HeartRateConnectionState.Connected ->
            if (isSilent) Color(0xFFF9A825) else Color(0xFF2E7D32)

        is HeartRateConnectionState.Failed -> MaterialTheme.colorScheme.error
        HeartRateConnectionState.Connecting,
        HeartRateConnectionState.DiscoveringServices,
        HeartRateConnectionState.Reconnecting,
            -> Color(0xFFF9A825)

        HeartRateConnectionState.Disconnected -> MaterialTheme.colorScheme.outlineVariant
    }

    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description }
    )
}

@Composable
private fun HeartRateConnectionState.label(knownName: String?): String = when (this) {
    is HeartRateConnectionState.Connected ->
        stringResource(R.string.heart_rate_connected, knownName ?: address)

    HeartRateConnectionState.Connecting -> stringResource(R.string.connection_connecting)
    HeartRateConnectionState.DiscoveringServices ->
        stringResource(R.string.heart_rate_discovering)

    HeartRateConnectionState.Reconnecting ->
        stringResource(R.string.connection_reconnecting)

    is HeartRateConnectionState.Failed,
    HeartRateConnectionState.Disconnected,
        -> stringResource(R.string.heart_rate_disconnected)
}
