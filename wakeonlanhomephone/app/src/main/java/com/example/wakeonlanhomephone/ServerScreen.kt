package com.example.wakeonlanhomephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonlanhomephone.ui.components.*
import com.example.wakeonlanhomephone.ui.theme.*
import java.net.NetworkInterface

@Composable
fun ServerScreen(
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    logs: List<String>,
    onClearLogs: () -> Unit = {},
    onSettings: () -> Unit,
    deviceProfileManager: DeviceProfileManager? = null,
    onWakeComputer: ((DeviceProfile) -> Unit)? = null,
    onPowerCommand: ((DeviceProfile, String) -> Unit)? = null
) {
    val context = LocalContext.current
    val noMacLabel = stringResource(R.string.server_no_mac)
    val profileManager = remember(deviceProfileManager) { deviceProfileManager ?: DeviceProfileManager(context) }
    var devices by remember { mutableStateOf(profileManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(profileManager.getSelectedDeviceId()) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceProfile?>(null) }
    var confirmPowerAction by remember { mutableStateOf<String?>(null) }
    var confirmPowerDevice by remember { mutableStateOf<DeviceProfile?>(null) }

    val refreshDevices = {
        devices = profileManager.getDevices()
        selectedDeviceId = profileManager.getSelectedDeviceId()
    }

    val ipv6Addresses = remember { getDeviceIPv6Addresses() }
    val displayedLogs = remember(logs) { logs.asReversed().take(5) }
    val selectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, start = 20.dp, end = 20.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Large title + status chip
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.server_title),
                        style = FrostLargeTitle,
                        color = FrostTextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ServiceStatusChip(isRunning = isRunning)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.server_subtitle),
                            style = FrostCaption,
                            color = FrostTextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Hero: selected target + big wake button + power actions
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    if (selectedDevice == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.server_no_devices),
                                style = FrostBody.copy(fontSize = 15.sp),
                                color = FrostTextSecondary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { showAddDeviceDialog = true },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FrostAccent,
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.content_add_target), style = FrostCallout)
                            }
                        }
                    } else {
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
                                        .size(56.dp)
                                        .background(FrostAccentDim, RoundedCornerShape(18.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = FrostAccent,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = selectedDevice.name,
                                        style = FrostHeadline.copy(fontSize = 20.sp),
                                        color = FrostTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = selectedDevice.mac.ifEmpty { noMacLabel },
                                        style = FrostCaption.copy(fontFamily = FontFamily.Monospace),
                                        color = FrostTextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selectedDevice.ip.isNotBlank()) {
                                        Text(
                                            text = selectedDevice.ip,
                                            style = FrostMicro.copy(fontFamily = FontFamily.Monospace),
                                            color = FrostTextTertiary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = { deviceToEdit = selectedDevice },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.action_edit),
                                    tint = FrostTextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        MasterWakeButton(
                            enabled = selectedDevice.mac.isNotBlank(),
                            deviceName = selectedDevice.name,
                            deviceMac = selectedDevice.mac,
                            onClick = { onWakeComputer?.invoke(selectedDevice) }
                        )

                        if (selectedDevice.ip.isNotBlank()) {
                            Spacer(Modifier.height(20.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                PowerActionButton(
                                    label = stringResource(R.string.power_sleep),
                                    icon = Icons.Rounded.Bedtime,
                                    tint = FrostWarning,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        confirmPowerAction = "sleep"
                                        confirmPowerDevice = selectedDevice
                                    }
                                )
                                PowerActionButton(
                                    label = stringResource(R.string.power_reboot),
                                    icon = Icons.Rounded.Refresh,
                                    tint = FrostAccent,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        confirmPowerAction = "reboot"
                                        confirmPowerDevice = selectedDevice
                                    }
                                )
                                PowerActionButton(
                                    label = stringResource(R.string.power_shutdown),
                                    icon = Icons.Rounded.PowerSettingsNew,
                                    tint = FrostDanger,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        confirmPowerAction = "shutdown"
                                        confirmPowerDevice = selectedDevice
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Target selection
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.server_targets_title),
                                style = FrostHeadline,
                                color = FrostTextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderSubtle)
                            ) {
                                Text(
                                    text = pluralStringResource(R.plurals.server_device_count_plural, devices.size, devices.size),
                                    color = FrostTextSecondary,
                                    style = FrostMicro,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { showAddDeviceDialog = true },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.content_add_target),
                                tint = FrostAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    if (devices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.server_no_devices),
                            color = FrostTextTertiary,
                            style = FrostCaption,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(devices) { device ->
                                val isSelected = device.id == selectedDeviceId
                                val chipShape = RoundedCornerShape(16.dp)
                                Surface(
                                    modifier = Modifier
                                        .clip(chipShape)
                                        .border(
                                            1.dp,
                                            if (isSelected) FrostAccentBorder else FrostBorderSubtle,
                                            chipShape
                                        )
                                        .clickable {
                                            selectedDeviceId = device.id
                                            profileManager.setSelectedDeviceId(device.id)
                                            refreshDevices()
                                        },
                                    color = if (isSelected) FrostAccentDim else FrostSurfaceElevated,
                                    shape = chipShape
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = device.name,
                                                color = FrostTextPrimary,
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                            Text(
                                                text = device.mac.ifEmpty { noMacLabel },
                                                color = FrostTextSecondary,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        IconButton(
                                            onClick = { deviceToEdit = device },
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = stringResource(R.string.action_edit),
                                                tint = if (isSelected) FrostAccent else FrostTextTertiary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // TCP service + local addresses
            item {
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
                                    .size(40.dp)
                                    .background(
                                        if (isRunning) FrostSuccessDim else FrostSurfaceElevated,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = if (isRunning) FrostSuccess else FrostTextSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.server_tcp_service),
                                    style = FrostCallout,
                                    color = FrostTextPrimary
                                )
                                Text(
                                    text = if (isRunning) stringResource(R.string.server_tcp_running) else stringResource(R.string.server_tcp_stopped),
                                    style = FrostCaption,
                                    color = FrostTextSecondary
                                )
                            }
                        }

                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { if (isRunning) onStopService() else onStartService() },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isRunning) FrostSurfaceElevated else FrostAccent,
                                contentColor = if (isRunning) FrostDanger else Color.White
                            ),
                            border = if (isRunning) {
                                androidx.compose.foundation.BorderStroke(1.dp, FrostBorderStandard)
                            } else null,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                        ) {
                            Text(
                                text = if (isRunning) stringResource(R.string.server_stop_service) else stringResource(R.string.server_start_service),
                                style = FrostCaption,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = FrostBorderSubtle)
                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.server_ipv6_title),
                            color = FrostTextSecondary,
                            style = FrostCaption
                        )
                        Text(
                            text = stringResource(R.string.server_tap_to_copy),
                            color = FrostTextTertiary,
                            style = FrostMicro
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    if (ipv6Addresses.isEmpty()) {
                        Text(
                            text = stringResource(R.string.server_no_ipv6),
                            color = FrostTextTertiary,
                            style = FrostCaption,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        val clipboardManager = LocalClipboardManager.current
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ipv6Addresses.forEach { ip ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(ip))
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.toast_ip_copied, ip),
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                    color = FrostSurfaceElevated,
                                    shape = RoundedCornerShape(14.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = ip,
                                            color = FrostTextPrimary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = stringResource(R.string.content_copy),
                                            tint = FrostTextTertiary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent activity
            item {
                GlassPanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.server_logs_title),
                            style = FrostHeadline,
                            color = FrostTextPrimary
                        )
                        Text(
                            text = stringResource(R.string.server_logs_clear),
                            color = FrostAccent,
                            style = FrostCaption,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onClearLogs() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    if (displayedLogs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.server_logs_empty),
                            color = FrostTextTertiary,
                            style = FrostCaption,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            displayedLogs.forEachIndexed { index, log ->
                                LogItem(log = log)
                                if (index != displayedLogs.lastIndex) {
                                    HorizontalDivider(color = FrostBorderSubtle)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Computer Dialog
        if (showAddDeviceDialog) {
            AddComputerDialog(
                onDismiss = { showAddDeviceDialog = false },
                onConfirm = { name, mac, ip, pcSecret ->
                    profileManager.addDevice(name, mac, ip, pcSecret)
                    refreshDevices()
                    showAddDeviceDialog = false
                }
            )
        }

        // Edit Computer Dialog
        deviceToEdit?.let { device ->
            EditComputerDialog(
                device = device,
                canDelete = devices.size > 1,
                onDismiss = { deviceToEdit = null },
                onSave = { name, mac, ip, pcSecret ->
                    profileManager.updateDevice(
                        device.copy(name = name, mac = mac, ip = ip, pcSecret = pcSecret),
                        preservePcSecret = false
                    )
                    refreshDevices()
                    deviceToEdit = null
                },
                onDelete = {
                    profileManager.deleteDevice(device.id)
                    refreshDevices()
                    deviceToEdit = null
                }
            )
        }

        // Confirmation Dialog for Power Actions
        if (confirmPowerAction != null && confirmPowerDevice != null) {
            val isDestructive = confirmPowerAction == "shutdown"
            val actionName = when (confirmPowerAction) {
                "shutdown" -> stringResource(R.string.power_action_shutdown)
                "reboot" -> stringResource(R.string.power_action_reboot)
                "sleep" -> stringResource(R.string.power_action_sleep)
                else -> confirmPowerAction!!.uppercase()
            }
            ConfirmPowerDialog(
                actionName = actionName,
                targetName = confirmPowerDevice!!.name,
                targetIp = confirmPowerDevice!!.ip,
                isDestructive = isDestructive,
                onConfirm = {
                    val act = confirmPowerAction
                    val dev = confirmPowerDevice
                    confirmPowerAction = null
                    confirmPowerDevice = null
                    if (act != null && dev != null) {
                        onPowerCommand?.invoke(dev, act)
                    }
                },
                onDismiss = {
                    confirmPowerAction = null
                    confirmPowerDevice = null
                }
            )
        }
    }
}

@Composable
private fun ServiceStatusChip(isRunning: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (isRunning) FrostSuccessDim else Color.White.copy(alpha = 0.46f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isRunning) FrostSuccess.copy(alpha = 0.24f) else FrostBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRunning) {
                AnimatedPingDot(color = FrostSuccess, size = 7.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(FrostTextTertiary, CircleShape)
                )
            }
            Spacer(Modifier.width(7.dp))
            Text(
                text = if (isRunning) stringResource(R.string.server_status_listening) else stringResource(R.string.server_status_stopped),
                color = if (isRunning) FrostTextPrimary else FrostTextSecondary,
                style = FrostMicro
            )
        }
    }
}

@Composable
private fun PowerActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FrostSurfaceElevated,
            contentColor = tint
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderSubtle),
        contentPadding = PaddingValues(horizontal = 4.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = FrostTextPrimary,
            style = FrostCaption.copy(fontSize = 12.sp),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LogItem(log: String) {
    val systemSource = stringResource(R.string.log_source_system)
    val mqttConnectedLabel = stringResource(R.string.log_status_mqtt_connected)
    val mqttDisconnectedLabel = stringResource(R.string.log_status_mqtt_disconnected)
    val mqttConnectingLabel = stringResource(R.string.log_status_mqtt_connecting)
    val connectionErrorLabel = stringResource(R.string.log_status_connection_error)
    val serviceReadyLabel = stringResource(R.string.log_status_service_ready)
    val commandReceivedLabel = stringResource(R.string.log_status_command_received)
    val commandExecutedLabel = stringResource(R.string.log_status_command_executed)
    val systemMessageLabel = stringResource(R.string.log_status_system_message)

    val parsed = remember(log) {
        val hasTimestamp = log.startsWith("[") && log.contains("]")
        val timestamp = if (hasTimestamp) log.substring(1, log.indexOf("]")) else ""
        val message = if (hasTimestamp) {
            val idx = log.indexOf("]")
            if (idx + 1 < log.length && log[idx + 1] == ' ') {
                log.substring(idx + 2)
            } else {
                log.substring(idx + 1)
            }
        } else {
            log
        }

        val fromText = if (message.contains(":")) {
            message.substringBefore(":").trim()
        } else {
            systemSource
        }
        val detailText = if (message.contains(":")) {
            message.substringAfter(":").trim()
        } else {
            message
        }

        Triple(timestamp, fromText, detailText)
    }

    val (timestamp, fromText, detailText) = parsed

    val statusInfo = remember(fromText, detailText) {
        val lowerMessage = detailText.lowercase()
        val lowerFrom = fromText.lowercase()

        when {
            lowerFrom.contains("mqtt") && lowerMessage.contains("connected") -> {
                mqttConnectedLabel to FrostSuccess
            }
            lowerFrom.contains("mqtt") && (lowerMessage.contains("disconnected") || lowerMessage.contains("closed")) -> {
                mqttDisconnectedLabel to FrostDanger
            }
            lowerFrom.contains("mqtt") && lowerMessage.contains("connecting") -> {
                mqttConnectingLabel to FrostWarning
            }
            lowerMessage.contains("error") || lowerMessage.contains("failed") -> {
                connectionErrorLabel to FrostDanger
            }
            lowerMessage.contains("listening") || lowerMessage.contains("started") || lowerMessage.contains("connected") -> {
                serviceReadyLabel to FrostSuccess
            }
            lowerMessage.contains("received") -> {
                commandReceivedLabel to FrostInfo
            }
            lowerMessage.contains("action") || lowerMessage.contains("published") || lowerMessage.contains("sent") -> {
                commandExecutedLabel to FrostSuccess
            }
            else -> {
                systemMessageLabel to FrostTextSecondary
            }
        }
    }
    val (statusText, statusColor) = statusInfo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .background(statusColor, CircleShape)
        )
        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = FrostTextSecondary,
                    style = FrostMicro,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timestamp,
                    color = FrostTextTertiary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = detailText,
                color = FrostTextPrimary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp
            )
        }
    }
}

/**
 * Get device IPv6 addresses
 */
private fun getDeviceIPv6Addresses(): List<String> {
    val list = mutableListOf<String>()
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == true) {
                    val ipv6 = address.hostAddress?.split("%")?.get(0) ?: continue
                    if (!ipv6.startsWith("fe80")) { // Skip link-local
                        if (!list.contains(ipv6)) {
                            list.add(ipv6)
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@Composable
fun AddComputerDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, mac: String, ip: String, pcSecret: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var pcSecret by remember { mutableStateOf("") }
    var pcSecretVisible by remember { mutableStateOf(false) }
    val newHostName = stringResource(R.string.default_device_new_host)

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, shape, ambientColor = FrostShadow, spotColor = FrostShadow)
                .border(1.dp, FrostBorderSubtle, shape)
                .clip(shape),
            color = FrostSurfaceSolid,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_add_target_title),
                    style = FrostTitle.copy(fontSize = 20.sp),
                    color = FrostTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_name_with_example),
                    value = name,
                    onValueChange = { name = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_mac_with_example),
                    value = mac,
                    onValueChange = { mac = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_ip_optional_power),
                    value = ip,
                    onValueChange = { ip = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PcSecretField(
                    value = pcSecret,
                    onValueChange = { pcSecret = it },
                    visible = pcSecretVisible,
                    onToggleVisibility = { pcSecretVisible = !pcSecretVisible }
                )
                Spacer(modifier = Modifier.height(20.dp))
                DialogActions(
                    onDismiss = onDismiss,
                    confirmLabel = stringResource(R.string.action_add),
                    confirmEnabled = mac.isNotBlank(),
                    onConfirm = {
                        if (mac.isNotBlank()) {
                            onConfirm(name.ifBlank { newHostName }, mac.trim(), ip.trim(), pcSecret.trim())
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun EditComputerDialog(
    device: DeviceProfile,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, mac: String, ip: String, pcSecret: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var mac by remember { mutableStateOf(device.mac) }
    var ip by remember { mutableStateOf(device.ip) }
    var pcSecret by remember { mutableStateOf(device.pcSecret) }
    var pcSecretVisible by remember { mutableStateOf(false) }
    val fallbackHostName = stringResource(R.string.default_device_host)

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(28.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, shape, ambientColor = FrostShadow, spotColor = FrostShadow)
                .border(1.dp, FrostBorderSubtle, shape)
                .clip(shape),
            color = FrostSurfaceSolid,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(R.string.dialog_edit_target_title),
                    style = FrostTitle.copy(fontSize = 20.sp),
                    color = FrostTextPrimary
                )
                Spacer(modifier = Modifier.height(16.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_name),
                    value = name,
                    onValueChange = { name = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_mac),
                    value = mac,
                    onValueChange = { mac = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                DialogTextField(
                    label = stringResource(R.string.dialog_ip_optional),
                    value = ip,
                    onValueChange = { ip = it }
                )
                Spacer(modifier = Modifier.height(12.dp))
                PcSecretField(
                    value = pcSecret,
                    onValueChange = { pcSecret = it },
                    visible = pcSecretVisible,
                    onToggleVisibility = { pcSecretVisible = !pcSecretVisible }
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = FrostDanger
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    DialogActions(
                        onDismiss = onDismiss,
                        confirmLabel = stringResource(R.string.action_save),
                        confirmEnabled = mac.isNotBlank(),
                        onConfirm = {
                            if (mac.isNotBlank()) {
                                onSave(name.ifBlank { fallbackHostName }, mac.trim(), ip.trim(), pcSecret.trim())
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PcSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    DialogTextField(
        label = stringResource(R.string.dialog_pc_secret_label),
        value = value,
        onValueChange = onValueChange,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleVisibility) {
                Icon(
                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                    contentDescription = if (visible) {
                        stringResource(R.string.password_hide)
                    } else {
                        stringResource(R.string.password_show)
                    },
                    tint = FrostTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        supportingText = {
            Text(
                text = stringResource(R.string.dialog_pc_secret_caption),
                color = FrostTextTertiary,
                style = FrostCaption
            )
        }
    )
}

@Composable
private fun DialogTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = FrostCaption) },
        singleLine = true,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = FrostAccentBorder,
            unfocusedBorderColor = FrostBorderSubtle,
            focusedLabelColor = FrostAccent,
            unfocusedLabelColor = FrostTextSecondary,
            focusedTextColor = FrostTextPrimary,
            unfocusedTextColor = FrostTextPrimary,
            focusedContainerColor = FrostSurfaceElevated,
            unfocusedContainerColor = FrostSurfaceElevated,
            cursorColor = FrostAccent
        ),
        shape = RoundedCornerShape(14.dp),
        textStyle = FrostBody.copy(fontSize = 15.sp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DialogActions(
    onDismiss: () -> Unit,
    confirmLabel: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onDismiss) {
            Text(
                stringResource(R.string.action_cancel),
                color = FrostTextSecondary,
                style = FrostCallout
            )
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = FrostAccent,
                contentColor = Color.White
            ),
            shape = CircleShape
        ) {
            Text(
                confirmLabel,
                style = FrostCallout,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
