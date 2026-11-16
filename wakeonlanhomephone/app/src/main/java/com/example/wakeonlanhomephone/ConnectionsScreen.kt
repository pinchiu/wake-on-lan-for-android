package com.example.wakeonlanhomephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeonlanhomephone.ui.components.*
import com.example.wakeonlanhomephone.ui.theme.*

@Composable
fun ConnectionsScreen(
    devices: List<MqttDevice>,
    deviceStatuses: Map<String, Boolean>,
    mqttState: MqttConnectionState,
    onAddDevice: () -> Unit,
    onEditDevice: (MqttDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onToggleDevice: (MqttDevice) -> Unit,
    onSettings: () -> Unit,
    brokerUrl: String
) {
    var deviceToDeleteId by remember { mutableStateOf<String?>(null) }

    if (deviceToDeleteId != null) {
        val deviceName = devices.find { it.id == deviceToDeleteId }?.name ?: ""
        AlertDialog(
            onDismissRequest = { deviceToDeleteId = null },
            title = {
                Text(
                    text = stringResource(R.string.mqtt_delete_title),
                    color = FrostTextPrimary,
                    style = FrostHeadline
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.mqtt_delete_message, deviceName),
                    color = FrostTextSecondary,
                    style = FrostBody.copy(fontSize = 14.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deviceToDeleteId?.let { onDeleteDevice(it) }
                        deviceToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostDanger,
                        contentColor = Color.White
                    ),
                    shape = CircleShape
                ) {
                    Text(
                        stringResource(R.string.action_delete_confirm),
                        color = Color.White,
                        style = FrostCallout,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDeleteId = null }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        color = FrostTextSecondary,
                        style = FrostCallout
                    )
                }
            },
            containerColor = FrostSurfaceSolid,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 0.dp
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        // Large title + broker status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.mqtt_devices_title),
                    style = FrostLargeTitle,
                    color = FrostTextPrimary
                )
                Spacer(Modifier.height(6.dp))
                BrokerStatusPill(mqttState = mqttState, onClick = onSettings)
            }
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.content_settings),
                    tint = FrostTextSecondary
                )
            }
        }

        // Broker card
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(FrostAccentDim, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = FrostAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "MQTT",
                            style = FrostCallout,
                            color = FrostTextPrimary
                        )
                        Text(
                            text = brokerUrl.ifBlank { "—" },
                            style = FrostCaption,
                            color = FrostTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onSettings,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostSurfaceElevated,
                        contentColor = FrostTextPrimary
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderStandard),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.tab_settings),
                        style = FrostCaption,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = FrostBorderSubtle)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.stat_total_devices),
                    value = devices.size.toString(),
                    valueColor = FrostTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.stat_online),
                    value = devices.count { deviceStatuses[it.id] == true }.toString(),
                    valueColor = FrostTextPrimary,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.stat_offline),
                    value = devices.count { deviceStatuses[it.id] != true }.toString(),
                    valueColor = FrostTextSecondary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.mqtt_no_devices),
                        color = FrostTextSecondary,
                        style = FrostBody
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.mqtt_empty_hint),
                        color = FrostTextTertiary,
                        style = FrostCaption
                    )
                    Spacer(Modifier.height(18.dp))
                    GhostAddRow(onClick = onAddDevice)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 124.dp)
            ) {
                items(
                    items = devices,
                    key = { it.id }
                ) { device ->
                    MqttDeviceCard(
                        device = device,
                        isOnline = deviceStatuses[device.id] ?: false,
                        brokerUrl = brokerUrl,
                        onDelete = { deviceToDeleteId = device.id },
                        onEdit = { onEditDevice(device) },
                        onToggle = { onToggleDevice(device) }
                    )
                }
                item {
                    GhostAddRow(onClick = onAddDevice)
                }
            }
        }
    }
}

@Composable
private fun BrokerStatusPill(
    mqttState: MqttConnectionState,
    onClick: () -> Unit
) {
    val accent = when (mqttState) {
        MqttConnectionState.CONNECTED -> FrostSuccess
        MqttConnectionState.CONNECTING -> FrostWarning
        MqttConnectionState.FAILED -> FrostDanger
        MqttConnectionState.DISCONNECTED -> FrostTextTertiary
    }
    val label = when (mqttState) {
        MqttConnectionState.CONNECTED -> R.string.broker_connected
        MqttConnectionState.CONNECTING -> R.string.broker_connecting
        MqttConnectionState.FAILED -> R.string.broker_failed
        MqttConnectionState.DISCONNECTED -> R.string.broker_disconnected
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.46f), CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (mqttState == MqttConnectionState.CONNECTED || mqttState == MqttConnectionState.CONNECTING) {
            AnimatedPingDot(color = accent, size = 6.dp)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(accent, CircleShape)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(label),
            color = if (mqttState == MqttConnectionState.DISCONNECTED) FrostTextSecondary else FrostTextPrimary,
            style = FrostMicro
        )
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = FrostSurfaceElevated,
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = FrostTextSecondary,
                style = FrostMicro,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun MqttDeviceCard(
    device: MqttDevice,
    isOnline: Boolean,
    brokerUrl: String,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit
) {
    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isOnline) FrostSuccessDim else FrostSurfaceElevated,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = device.name.trim().take(1).uppercase().ifBlank { "PC" },
                    color = FrostTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        color = FrostTextPrimary,
                        style = FrostHeadline.copy(fontSize = 17.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    DeviceStatusPill(isOnline = isOnline)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = device.topic.ifBlank { brokerUrl },
                    color = FrostTextSecondary,
                    style = FrostCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onEdit, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.action_edit),
                    tint = FrostTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = FrostDanger,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onToggle,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = FrostSurfaceElevated,
                contentColor = FrostAccent
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderSubtle),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = if (isOnline) stringResource(R.string.mqtt_send_offline) else stringResource(R.string.mqtt_send_online),
                color = FrostTextPrimary,
                style = FrostCaption,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DeviceStatusPill(isOnline: Boolean) {
    val accent = if (isOnline) FrostSuccess else FrostTextTertiary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (isOnline) FrostSuccessDim else Color.White.copy(alpha = 0.46f),
                CircleShape
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .background(accent, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (isOnline) stringResource(R.string.status_connected) else stringResource(R.string.status_offline),
            color = if (isOnline) FrostTextPrimary else FrostTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun GhostAddRow(onClick: () -> Unit) {
    val shape = CircleShape
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .border(1.dp, FrostBorderStandard, shape)
            .clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.30f),
        shape = shape,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = FrostAccent,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.action_add),
                color = FrostTextPrimary,
                style = FrostCallout,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
