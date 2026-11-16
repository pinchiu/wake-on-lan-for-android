package com.example.wakeonlanhomephone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeonlanhomephone.ui.components.GlassPanel
import com.example.wakeonlanhomephone.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    configManager: MqttConfigManager,
    onCancel: () -> Unit,
    onSave: (MqttDevice) -> Unit,
    deviceToEdit: MqttDevice? = null
) {
    val currentConfig = remember { configManager.getConfig() }
    var name by remember { mutableStateOf(deviceToEdit?.name ?: if (currentConfig.brokerName.isNotEmpty()) currentConfig.brokerName else "Adafruit IO") }
    var topic by remember { mutableStateOf(deviceToEdit?.topic ?: currentConfig.topic) }
    var brokerUrl by remember { mutableStateOf(currentConfig.host) }
    var port by remember { mutableStateOf(if (currentConfig.port > 0) currentConfig.port.toString() else if (currentConfig.useSsl) "8883" else "1883") }
    var protocol by remember { mutableStateOf(currentConfig.protocol) }
    var useSsl by remember { mutableStateOf(currentConfig.useSsl) }
    var username by remember { mutableStateOf(currentConfig.username) }
    var password by remember { mutableStateOf(currentConfig.password) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(
                            stringResource(R.string.action_cancel),
                            color = FrostAccent,
                            style = FrostCallout
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(color = FrostBorderSubtle)
                Surface(
                    color = FrostSurfaceSolid,
                    tonalElevation = 0.dp,
                    shadowElevation = 12.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                val parsedPort = port.toIntOrNull() ?: if (useSsl) 8883 else 1883
                                // Keep scheme/port/path (e.g. wss://host:443/mqtt); MqttEndpoint parses it.
                                val cleanHost = brokerUrl.trim()

                                configManager.saveConfig(
                                    currentConfig.copy(
                                        brokerName = name.ifEmpty { currentConfig.brokerName },
                                        host = cleanHost,
                                        port = parsedPort,
                                        username = username.trim(),
                                        password = password.trim(),
                                        useSsl = useSsl,
                                        protocol = protocol,
                                        topic = if (topic.isNotEmpty()) topic.trim() else currentConfig.topic
                                    )
                                )

                                val device = if (deviceToEdit != null) {
                                    deviceToEdit.copy(name = name, topic = topic)
                                } else {
                                    MqttDevice(name = name, topic = topic)
                                }
                                onSave(device)
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            enabled = name.isNotEmpty() && topic.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FrostAccent,
                                contentColor = Color.White
                            ),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Done, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.add_device_save_apply),
                                color = Color.White,
                                style = FrostCallout,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Large, sheet-like title
            Text(
                text = if (deviceToEdit != null) {
                    stringResource(R.string.add_device_edit_title)
                } else {
                    stringResource(R.string.add_device_add_title)
                },
                style = FrostLargeTitle,
                color = FrostTextPrimary,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            )

            // Broker connection
            ConfigSectionCard(title = stringResource(R.string.add_device_broker_section)) {
                PrecisionInputField(
                    label = stringResource(R.string.add_device_name_label),
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Adafruit IO"
                )

                Spacer(Modifier.height(14.dp))

                PrecisionInputField(
                    label = stringResource(R.string.add_device_host_label),
                    value = brokerUrl,
                    onValueChange = { brokerUrl = it },
                    placeholder = "io.adafruit.com"
                )

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        PrecisionInputField(
                            label = stringResource(R.string.add_device_port_label),
                            value = port,
                            onValueChange = { port = it },
                            placeholder = if (useSsl) "8883" else "1883"
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.add_device_protocol_label),
                            style = FrostCaption,
                            color = FrostTextSecondary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .height(56.dp)
                                .fillMaxWidth()
                                .background(FrostSegmentTrack, CircleShape)
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SegmentOption(
                                label = "TCP",
                                selected = protocol == "TCP",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    protocol = "TCP"
                                    if (useSsl && port == "8084") port = "8883"
                                    else if (!useSsl && port == "8083") port = "1883"
                                }
                            )
                            SegmentOption(
                                label = "WS",
                                selected = protocol != "TCP",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    protocol = "WS"
                                    if (useSsl && port == "8883") port = "8084"
                                    else if (!useSsl && port == "1883") port = "8083"
                                }
                            )
                        }
                    }
                }
            }

            // Security & auth
            ConfigSectionCard(title = stringResource(R.string.add_device_security_section)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.add_device_ssl_title),
                            color = FrostTextPrimary,
                            style = FrostBody.copy(fontSize = 15.sp)
                        )
                        Text(
                            stringResource(R.string.add_device_ssl_hint),
                            color = FrostTextSecondary,
                            style = FrostCaption
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = useSsl,
                        onCheckedChange = { checked ->
                            useSsl = checked
                            if (checked && port == "1883") port = "8883"
                            else if (!checked && port == "8883") port = "1883"
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FrostAccent,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = FrostSegmentTrack
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = FrostBorderSubtle)
                Spacer(Modifier.height(14.dp))

                PrecisionInputField(
                    label = stringResource(R.string.add_device_username_label),
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "username"
                )

                Spacer(Modifier.height(14.dp))

                PrecisionInputField(
                    label = stringResource(R.string.add_device_password_label),
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "aio_xxxxxxxxxxxxxxxxxxxxxxxx",
                    visualTransformation = if (isPasswordVisible) {
                        androidx.compose.ui.text.input.VisualTransformation.None
                    } else {
                        androidx.compose.ui.text.input.PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) stringResource(R.string.password_hide) else stringResource(R.string.password_show),
                                tint = if (isPasswordVisible) FrostAccent else FrostTextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // Target topic
            ConfigSectionCard(title = stringResource(R.string.add_device_topic_section)) {
                PrecisionInputField(
                    label = stringResource(R.string.add_device_topic_label),
                    value = topic,
                    onValueChange = { topic = it },
                    placeholder = "username/feeds/feed-name"
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SegmentOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.shadow(2.dp, shape, ambientColor = FrostShadow, spotColor = FrostShadow)
                } else {
                    Modifier
                }
            )
            .background(if (selected) Color.White else Color.Transparent, shape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) FrostTextPrimary else FrostTextSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun ConfigSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = FrostTextSecondary,
            style = FrostCaption,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun PrecisionInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    Column {
        Text(
            text = label,
            style = FrostCaption,
            color = FrostTextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = FrostTextTertiary, style = FrostCaption) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FrostSurfaceElevated,
                unfocusedContainerColor = FrostSurfaceElevated,
                focusedBorderColor = FrostAccentBorder,
                unfocusedBorderColor = FrostBorderSubtle,
                focusedTextColor = FrostTextPrimary,
                unfocusedTextColor = FrostTextPrimary,
                cursorColor = FrostAccent,
                focusedPlaceholderColor = FrostTextTertiary,
                unfocusedPlaceholderColor = FrostTextTertiary
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
            textStyle = FrostBody.copy(fontSize = 15.sp),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon
        )
    }
}
