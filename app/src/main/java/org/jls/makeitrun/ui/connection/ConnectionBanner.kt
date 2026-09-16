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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import org.jls.makeitrun.ftms.DiscoveredTreadmill
import org.jls.makeitrun.ftms.TreadmillConnectionState

@SuppressLint("MissingPermission")
@Composable
fun ConnectionBanner(
    state: ConnectionUiState,
    viewModel: ConnectionViewModel,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusLed(state.connection)
                Text(
                    text = state.connection.label(state.profile.name),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ConnectedAction(state = state, viewModel = viewModel)
            }

            AnimatedVisibility(visible = !state.isConnected) {
                DisconnectedActions(state = state, viewModel = viewModel)
            }

            AnimatedVisibility(
                visible = !state.isConnected && (state.isScanning || state.devices.isNotEmpty())
            ) {
                DeviceList(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun ConnectedAction(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    when {
        state.isConnected -> TextButton(onClick = viewModel::disconnect) {
            Text(stringResource(R.string.connection_disconnect))
        }

        state.connection is TreadmillConnectionState.Reconnecting -> Row(
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

        state.connection is TreadmillConnectionState.Connecting ||
            state.connection is TreadmillConnectionState.DiscoveringServices ->
            CircularProgressIndicator(modifier = Modifier.size(20.dp))

        else -> Unit
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun DisconnectedActions(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    val isBusy = state.connection is TreadmillConnectionState.Connecting ||
        state.connection is TreadmillConnectionState.DiscoveringServices ||
        state.connection is TreadmillConnectionState.Reconnecting
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
            Button(onClick = viewModel::reconnectLastTreadmill, modifier = Modifier.weight(1f)) {
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
private fun DeviceList(state: ConnectionUiState, viewModel: ConnectionViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        if (state.isScanning && state.devices.isEmpty()) {
            Text(
                text = stringResource(R.string.connection_searching),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        LazyColumn(
            modifier = Modifier.heightIn(max = 220.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.devices, key = { it.address }) { device ->
                DeviceRow(device = device, onClick = { viewModel.connect(device) })
            }
        }

        if (!state.isScanning && state.devices.isEmpty()) {
            Text(
                text = stringResource(R.string.connection_no_device),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.connection_show_all),
                style = MaterialTheme.typography.bodySmall,
            )
            Switch(
                checked = state.showAllDevices,
                onCheckedChange = viewModel::setShowAllDevices,
            )
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredTreadmill, onClick: () -> Unit) {
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
                    text = device.name ?: stringResource(R.string.device_unknown_name),
                    style = MaterialTheme.typography.bodyLarge,
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
private fun StatusLed(connection: TreadmillConnectionState) {
    val description = stringResource(R.string.connection_led_description)
    val color = when (connection) {
        is TreadmillConnectionState.Connected -> Color(0xFF2E7D32)
        is TreadmillConnectionState.Failed -> MaterialTheme.colorScheme.error
        TreadmillConnectionState.Connecting,
        TreadmillConnectionState.DiscoveringServices,
        TreadmillConnectionState.Reconnecting,
            -> Color(0xFFF9A825)

        TreadmillConnectionState.Disconnected -> MaterialTheme.colorScheme.outlineVariant
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
private fun TreadmillConnectionState.label(knownName: String?): String = when (this) {
    is TreadmillConnectionState.Connected ->
        stringResource(R.string.connection_connected, knownName ?: address)

    TreadmillConnectionState.Connecting -> stringResource(R.string.connection_connecting)
    TreadmillConnectionState.DiscoveringServices ->
        stringResource(R.string.connection_discovering)

    TreadmillConnectionState.Reconnecting ->
        stringResource(R.string.connection_reconnecting)

    is TreadmillConnectionState.Failed,
    TreadmillConnectionState.Disconnected,
        -> stringResource(R.string.connection_disconnected)
}
