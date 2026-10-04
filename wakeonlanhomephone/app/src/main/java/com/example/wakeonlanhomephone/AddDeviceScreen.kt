package com.example.wakeonlanhomephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = if (deviceToEdit != null) "編輯 MQTT 設備" else "新增 MQTT 設備",
                        color = Slate50,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    ) 
                },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("取消", color = Slate400, fontSize = 14.sp)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = DarkBgPrimary,
                    titleContentColor = Slate50
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBgPrimary)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = { 
                        val parsedPort = port.toIntOrNull() ?: if (useSsl) 8883 else 1883
                        val cleanHost = brokerUrl
                            .replace(Regex("^[a-zA-Z]+://"), "")
                            .substringBefore(":")
                            .substringBefore("/")
                            .trim()

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
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = name.isNotEmpty() && topic.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Done, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("儲存並套用設定", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
        containerColor = DarkBgPrimary
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Broker Connection Section
            ConfigSectionCard(title = "Broker 連線資訊") {
                PrecisionInputField(
                    label = "設備 / Broker 名稱",
                    value = name,
                    onValueChange = { name = it },
                    placeholder = "Adafruit IO"
                )

                Spacer(Modifier.height(12.dp))

                PrecisionInputField(
                    label = "Broker 伺服器網址 (Host)",
                    value = brokerUrl,
                    onValueChange = { brokerUrl = it },
                    placeholder = "io.adafruit.com"
                )

                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        PrecisionInputField(
                            label = "通訊埠 (Port)",
                            value = port,
                            onValueChange = { port = it },
                            placeholder = if (useSsl) "8883" else "1883"
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "連線協議",
                            fontSize = 11.sp,
                            color = Slate400,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Row(
                            modifier = Modifier
                                .height(50.dp)
                                .fillMaxWidth()
                                .background(DarkSurfaceElevated, RoundedCornerShape(10.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                .padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (protocol == "TCP") AccentEmeraldDim else Color.Transparent)
                                    .clickable { 
                                        protocol = "TCP"
                                        if (useSsl && port == "8084") port = "8883"
                                        else if (!useSsl && port == "8083") port = "1883"
                                    }
                            ) {
                                Text(
                                    "TCP",
                                    color = if (protocol == "TCP") AccentEmerald else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (protocol != "TCP") AccentEmeraldDim else Color.Transparent)
                                    .clickable { 
                                        protocol = "WS"
                                        if (useSsl && port == "8883") port = "8084"
                                        else if (!useSsl && port == "1883") port = "8083"
                                    }
                            ) {
                                Text(
                                    "WS",
                                    color = if (protocol != "TCP") AccentEmerald else Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Security & Auth Section
            ConfigSectionCard(title = "安全與身份驗證") {
                // SSL Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SSL / TLS 加密連線", color = Slate50, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("建議啟用 (預設 Port 8883)", color = Slate400, fontSize = 11.sp)
                    }
                    Switch(
                        checked = useSsl,
                        onCheckedChange = { checked ->
                            useSsl = checked
                            if (checked && port == "1883") port = "8883"
                            else if (!checked && port == "8883") port = "1883"
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AccentEmerald,
                            checkedTrackColor = AccentEmeraldDim
                        )
                    )
                }

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(Modifier.height(14.dp))

                PrecisionInputField(
                    label = "使用者名稱 (Adafruit Username)",
                    value = username,
                    onValueChange = { username = it },
                    placeholder = "username"
                )

                Spacer(Modifier.height(12.dp))

                PrecisionInputField(
                    label = "密碼 / 憑證 (Adafruit AIO Key)",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "aio_xxxxxxxxxxxxxxxxxxxxxxxx",
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "隱藏密碼" else "顯示密碼",
                                tint = if (isPasswordVisible) AccentEmerald else Slate400,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // Target Topic Section
            ConfigSectionCard(title = "目標主題配置") {
                PrecisionInputField(
                    label = "監聽主題 (Topic)",
                    value = topic,
                    onValueChange = { topic = it },
                    placeholder = "username/feeds/feed-name"
                )
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun ConfigSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        color = DarkSurface
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = {
                Text(
                    text = title,
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(14.dp))
                content()
            }
        )
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
            fontSize = 11.sp,
            color = Slate400,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = Slate500, fontSize = 13.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated,
                focusedBorderColor = AccentEmerald,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            visualTransformation = visualTransformation,
            trailingIcon = trailingIcon
        )
    }
}
