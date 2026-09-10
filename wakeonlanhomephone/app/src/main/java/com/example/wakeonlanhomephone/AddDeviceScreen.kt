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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.res.painterResource
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
                title = { Text("MQTT Configuration", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = Slate500)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Navy950,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
             Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy950)
                    .padding(24.dp)
            ) {
                Button(
                    onClick = { 
                        val parsedPort = port.toIntOrNull() ?: if (useSsl) 8883 else 1883
                        val cleanHost = brokerUrl
                            .replace(Regex("^[a-zA-Z]+://"), "")
                            .substringBefore(":")
                            .substringBefore("/")
                            .trim()

                        // Save broker config
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
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = name.isNotEmpty() && topic.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonGreen, contentColor = Color.Black),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Done, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Configuration", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        },
        containerColor = Navy950
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Broker Connection Group
            ConfigSectionHeader("Broker Connection", NeonGreen)
            
            // Broker Name
            InputGroup("Broker Name", Icons.Default.Info) {
                NavyInput(value = name, onValueChange = { name = it }, placeholder = "Adafruit IO")
            }
            
            // Broker URL
            InputGroup("Broker Host / URL", Icons.Default.Share) { // 'link' icon
                NavyInput(value = brokerUrl, onValueChange = { brokerUrl = it }, placeholder = "io.adafruit.com")
            }
            
            // Port & Protocol
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                     InputGroup("Port", Icons.Default.Edit) { // Tag icon
                        NavyInput(value = port, onValueChange = { port = it }, placeholder = if (useSsl) "8883" else "1883")
                     }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protocol", style = MaterialTheme.typography.labelMedium, color = Slate300, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.height(56.dp).fillMaxWidth().background(Navy900, RoundedCornerShape(8.dp)).border(1.dp, Navy700, RoundedCornerShape(8.dp)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         Box(
                             contentAlignment = Alignment.Center,
                             modifier = Modifier
                                 .weight(1f)
                                 .fillMaxHeight()
                                 .clip(RoundedCornerShape(6.dp))
                                 .background(if (protocol == "TCP") PrimaryBlue.copy(alpha = 0.3f) else Color.Transparent)
                                 .clickable { 
                                     protocol = "TCP"
                                     if (useSsl && port == "8084") port = "8883"
                                     else if (!useSsl && port == "8083") port = "1883"
                                 }
                         ) {
                             Text("TCP", color = if (protocol == "TCP") PrimaryBlue else Slate500, fontWeight = FontWeight.Bold)
                         }
                         Box(
                             contentAlignment = Alignment.Center,
                             modifier = Modifier
                                 .weight(1f)
                                 .fillMaxHeight()
                                 .clip(RoundedCornerShape(6.dp))
                                 .background(if (protocol != "TCP") PrimaryBlue.copy(alpha = 0.3f) else Color.Transparent)
                                 .clickable { 
                                     protocol = "WS"
                                     if (useSsl && port == "8883") port = "8084"
                                     else if (!useSsl && port == "1883") port = "8083"
                                 }
                         ) {
                             Text("WS", color = if (protocol != "TCP") PrimaryBlue else Slate500, fontWeight = FontWeight.Bold)
                         }
                    }
                }
            }
            
            HorizontalDivider(color = Navy700)
            
            // Security Group
            ConfigSectionHeader("Security & Auth", Slate400) // Icon security
            
            // SSL Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Navy900, RoundedCornerShape(8.dp))
                    .border(1.dp, Navy700, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     Box(modifier = Modifier.size(40.dp).background(Navy800, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                         Icon(Icons.Default.Lock, null, tint = Slate400)
                     }
                     Spacer(Modifier.width(12.dp))
                     Column {
                         Text("Use SSL/TLS", color = Color.White, fontWeight = FontWeight.SemiBold)
                         Text("Encrypt connection (Port 8883)", color = Slate400, style = MaterialTheme.typography.bodySmall)
                     }
                 }
                 Switch(
                     checked = useSsl,
                     onCheckedChange = { checked ->
                         useSsl = checked
                         if (checked && port == "1883") port = "8883"
                         else if (!checked && port == "8883") port = "1883"
                     },
                     colors = SwitchDefaults.colors(
                         checkedThumbColor = NeonGreen,
                         checkedTrackColor = Navy800
                     )
                 )
            }
            
            // Username
            InputGroup("Username (Adafruit Username)", Icons.Default.Person) {
                NavyInput(value = username, onValueChange = { username = it }, placeholder = "your_username")
            }
             // Password
            InputGroup("Password (Adafruit AIO Key)", Icons.Default.Lock) {
                NavyInput(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "aio_xxxxxxxxxxxxxxxxxxxxxxxx",
                    visualTransformation = if (isPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (isPasswordVisible) "隱藏密碼" else "顯示密碼",
                                tint = if (isPasswordVisible) NeonGreen else Slate400
                            )
                        }
                    }
                )
            }
            
            HorizontalDivider(color = Navy700)
            
            // Target Settings
            ConfigSectionHeader("Target Device", Slate400)
            
            // Target Topic
            InputGroup("Target Topic", Icons.Default.Share) {
                 NavyInput(value = topic, onValueChange = { topic = it }, placeholder = "username/feeds/feed-name")
            }
            
            Spacer(Modifier.height(80.dp)) // Scroll padding
        }
    }
}

@Composable
fun ConfigSectionHeader(title: String, dotColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(dotColor, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 2.sp)
    }
}

@Composable
fun InputGroup(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Slate300, modifier = Modifier.padding(bottom = 8.dp))
        Box(contentAlignment = Alignment.CenterStart) {
             content()
             Icon(icon, null, tint = Slate500, modifier = Modifier.padding(start = 12.dp).size(20.dp))
        }
    }
}

@Composable
fun NavyInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Slate500) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Navy800,
            unfocusedContainerColor = Navy900,
            focusedBorderColor = NeonGreen,
            unfocusedBorderColor = Navy700,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
        visualTransformation = visualTransformation,
        leadingIcon = { Spacer(Modifier.width(24.dp)) }, // Spacer for the external icon overlay
        trailingIcon = trailingIcon
    )
}
