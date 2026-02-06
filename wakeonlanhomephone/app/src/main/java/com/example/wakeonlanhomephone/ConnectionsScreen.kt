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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.wakeonlanhomephone.ui.theme.*

@Composable
fun ConnectionsScreen(
    devices: List<MqttDevice>,
    deviceStatuses: Map<String, Boolean>,
    onAddDevice: () -> Unit,
    onEditDevice: (MqttDevice) -> Unit,
    onDeleteDevice: (String) -> Unit,
    onToggleDevice: (MqttDevice) -> Unit,
    onSettings: () -> Unit,
    brokerUrl: String
) {
    Scaffold(
        containerColor = Navy950,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 100.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Connection")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Connections", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Manage your MQTT brokers", color = Slate400, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Slate400)
                }
            }

            // Stats
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", devices.size.toString(), Color.White, Modifier.weight(1f))
                StatCard("Online", devices.count { deviceStatuses[it.id] == true }.toString(), SuccessGreen, Modifier.weight(1f))
                StatCard("Offline", devices.count { deviceStatuses[it.id] != true }.toString(), Slate500, Modifier.weight(1f))
            }
            
            
            // Device List
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(devices) { device ->
                    NavyDeviceCard(
                        device = device,
                        isOnline = deviceStatuses[device.id] ?: false,
                        brokerUrl = brokerUrl,
                        onDelete = { onDeleteDevice(device.id) },
                        onEdit = { onEditDevice(device) },
                        onToggle = { onToggleDevice(device) }
                    )
                }
                
                item { Spacer(Modifier.height(130.dp)) }
            }
        }
    }
}


@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy900),
        border = androidx.compose.foundation.BorderStroke(1.dp, Navy700),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label.uppercase(), color = Slate400, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(value, color = valueColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NavyDeviceCard(device: MqttDevice, isOnline: Boolean, brokerUrl: String, onDelete: () -> Unit, onEdit: () -> Unit, onToggle: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy900),
        border = androidx.compose.foundation.BorderStroke(1.dp, Navy700),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Status Strip
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(if (isOnline) SuccessGreen else Navy700)
            )
            
            Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(device.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            // Pulse dot
                             Box(modifier = Modifier.size(6.dp).background(if (isOnline) SuccessGreen else Slate600, CircleShape))
                             Spacer(Modifier.width(6.dp))
                             Text(
                                 if (isOnline) "Online" else "Offline",
                                 color = if (isOnline) SuccessGreen else Slate500,
                                 style = MaterialTheme.typography.labelSmall,
                                 fontWeight = FontWeight.Bold
                             )
                        }
                    }
                    
                    // Actions
                    Row {
                         IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                             Icon(Icons.Default.Edit, null, tint = Slate500, modifier = Modifier.size(18.dp))
                         }
                         IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                             Icon(Icons.Default.Delete, null, tint = Slate500, modifier = Modifier.size(18.dp))
                         }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Info Rows
                InfoRow("Broker", brokerUrl)
                InfoRow("Target Topic", device.topic)
                
                Spacer(Modifier.height(16.dp))
                
                // Action Button
                val buttonColor = if (isOnline) Navy800 else PrimaryBlue
                val buttonTextColor = if (isOnline) Slate300 else Color.White
                
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                     Icon(
                         imageVector = Icons.Default.Info,
                         contentDescription = null, 
                         modifier = Modifier.size(18.dp),
                         tint = buttonTextColor
                     )
                     Spacer(Modifier.width(8.dp))
                     Text(if (isOnline) "Disconnect" else "Connect", color = buttonTextColor)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(
            modifier = Modifier.size(24.dp).background(Navy800, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
             // Generic Icon placeholder
             Icon(Icons.Default.Settings, null, tint = Slate500, modifier = Modifier.size(14.dp)) 
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label.uppercase(), color = Slate500, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = Slate300, style = MaterialTheme.typography.bodySmall, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}
