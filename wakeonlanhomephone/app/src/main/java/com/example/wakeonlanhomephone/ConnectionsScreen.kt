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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeonlanhomephone.ui.theme.*
import com.example.wakeonlanhomephone.ui.components.*

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
                    text = "確認刪除設備",
                    color = Slate50,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "您確定要刪除設備「$deviceName」嗎？此操作無法復原。",
                    color = Slate300
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        deviceToDeleteId?.let { onDeleteDevice(it) }
                        deviceToDeleteId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticDanger),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("確定刪除", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deviceToDeleteId = null }
                ) {
                    Text("取消", color = Slate400)
                }
            },
            containerColor = DarkSurfaceElevated,
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        )
    }

    Scaffold(
        containerColor = DarkBgPrimary,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddDevice,
                containerColor = AccentEmerald,
                contentColor = Color.Black,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 90.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增設備")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "MQTT 設備清單",
                        color = Slate50,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onSettings() }
                    ) {
                        when (mqttState) {
                            MqttConnectionState.CONNECTED -> {
                                AnimatedPingDot(color = AccentEmerald, size = 6.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Broker: 已連線",
                                    color = AccentEmerald,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            MqttConnectionState.CONNECTING -> {
                                AnimatedPingDot(color = SemanticWarning, size = 6.dp)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Broker: 連線中...",
                                    color = SemanticWarning,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            MqttConnectionState.FAILED -> {
                                Box(modifier = Modifier.size(6.dp).background(SemanticDanger, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Broker: 連線失敗 (點擊設定)",
                                    color = SemanticDanger,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            MqttConnectionState.DISCONNECTED -> {
                                Box(modifier = Modifier.size(6.dp).background(Slate500, CircleShape))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Broker: 未連線",
                                    color = Slate400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "設定", tint = Slate400)
                }
            }

            // Status Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = "設備總數",
                    value = devices.size.toString(),
                    valueColor = Slate50,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "線上狀態",
                    value = devices.count { deviceStatuses[it.id] == true }.toString(),
                    valueColor = AccentEmerald,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "離線狀態",
                    value = devices.count { deviceStatuses[it.id] != true }.toString(),
                    valueColor = Slate400,
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))

            // Device List
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "尚未設定任何 MQTT 設備",
                            color = Slate400,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "點擊右下角「+」新增監聽主題",
                            color = Slate500,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
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
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp)),
        color = DarkSurface
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Slate400,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                color = valueColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
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
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.name,
                        color = Slate50,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOnline) AccentEmeraldDim else DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isOnline) AccentEmeraldBorder else BorderSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(if (isOnline) AccentEmerald else Slate500, CircleShape)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = if (isOnline) "已連線" else "已離線",
                                color = if (isOnline) AccentEmerald else Slate400,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                
                // Action icons
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "編輯", tint = Slate400, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "刪除", tint = SemanticDanger, modifier = Modifier.size(16.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(10.dp))
            
            // Topic Info
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Slate400,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "主題 (Topic)",
                            color = Slate500,
                            fontSize = 10.sp
                        )
                        Text(
                            text = device.topic,
                            color = Slate200,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Toggle Button
            Button(
                onClick = onToggle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOnline) DarkSurfaceElevated else AccentEmeraldDim
                ),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isOnline) BorderSubtle else AccentEmeraldBorder
                ),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Text(
                    text = if (isOnline) "發送離線訊號" else "發送上線訊號",
                    color = if (isOnline) Slate300 else AccentEmerald,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
