package com.example.wakeonlanhomephone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
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
    onCancel: () -> Unit,
    onSave: (MqttDevice) -> Unit,
    deviceToEdit: MqttDevice? = null
) {
    var name by remember { mutableStateOf(deviceToEdit?.name ?: "") }
    var topic by remember { mutableStateOf(deviceToEdit?.topic ?: "") }
    var brokerUrl by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1883") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

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
                NavyInput(value = name, onValueChange = { name = it }, placeholder = "Home Assistant")
            }
            
            // Broker URL
            InputGroup("Broker Host / URL", Icons.Default.Share) { // 'link' icon
                NavyInput(value = brokerUrl, onValueChange = { brokerUrl = it }, placeholder = "mqtt://broker.hivemq.com")
            }
            
            // Port & Protocol
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                     InputGroup("Port", Icons.Default.Edit) { // Tag icon
                        NavyInput(value = port, onValueChange = { port = it }, placeholder = "1883")
                     }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Protocol", style = MaterialTheme.typography.labelMedium, color = Slate300, modifier = Modifier.padding(bottom = 8.dp))
                    Row(
                        modifier = Modifier.height(56.dp).fillMaxWidth().background(Navy900, RoundedCornerShape(8.dp)).border(1.dp, Navy700, RoundedCornerShape(8.dp)).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                         // Mock Toggle
                         Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxHeight().background(Color.Transparent, RoundedCornerShape(6.dp))) {
                             Text("TCP", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                         }
                         Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxHeight()) {
                             Text("WS", color = Slate500, fontWeight = FontWeight.Bold)
                         }
                    }
                }
            }
            
            Divider(color = Navy700)
            
            // Security Group
            ConfigSectionHeader("Security & Auth", Slate400) // Icon security
            
            // SSL Toggle (Static UI for now)
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
                         Text("Encrypt connection", color = Slate400, style = MaterialTheme.typography.bodySmall)
                     }
                 }
                 Switch(checked = false, onCheckedChange = {})
            }
            
            // Username
            InputGroup("Username (Optional)", Icons.Default.Person) {
                NavyInput(value = username, onValueChange = { username = it }, placeholder = "MQTTUser")
            }
             // Password
            InputGroup("Password (Optional)", Icons.Default.Lock) {
                NavyInput(value = password, onValueChange = { password = it }, placeholder = "••••••••", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
            
            Divider(color = Navy700)
            
            // Target Settings
            ConfigSectionHeader("Target Device", Slate400)
            
            // Target Topic (Mapped to 'Topic' in data model, though template says 'Target MAC Address'. We need Topic for MQTT. Template has 'Target MAC Address' input. 
            // In WAKE ON LAN, usually it's MAC. But this is MQTT context. 
            // The template 1 (Connections) shows "Target Topic: home/gaming/wol".
            // Template 2 (Add) shows "Target MAC Address". 
            // I suspect the user wants both? Or is confused? 
            // Existing app uses Topic. I will label it "Target Topic" to match functionality but keep the UI style.
            InputGroup("Target Topic", Icons.Default.Share) {
                 NavyInput(value = topic, onValueChange = { topic = it }, placeholder = "home/gaming/wol")
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
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
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
        leadingIcon = { Spacer(Modifier.width(24.dp)) } // Spacer for the external icon overlay
    )
}
