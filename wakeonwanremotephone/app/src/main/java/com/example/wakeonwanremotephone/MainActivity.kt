package com.example.wakeonwanremotephone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.example.wakeonwanremotephone.ui.theme.*
import com.example.wakeonwanremotephone.ui.components.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WakeOnWanRemotePhoneTheme {
                RemoteWakeMainScreen()
            }
        }
    }
}

@Composable
fun UltimateTheme(content: @Composable () -> Unit) {
    WakeOnWanRemotePhoneTheme(content = content)
}

@Composable
fun RemoteWakeMainScreen() {
    val context = LocalContext.current
    val deviceManager = remember { DeviceProfileManager(context) }
    var devices by remember { mutableStateOf(deviceManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(deviceManager.getSelectedDeviceId()) }
    val currentSelectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()

    // Configuration State
    var helperIpv6Address by remember { mutableStateOf(deviceManager.getHelperIpv6()) }
    var computerMacAddress by remember { mutableStateOf(currentSelectedDevice?.mac ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(currentSelectedDevice?.ip ?: "") }
    var localLanMode by remember { mutableStateOf(deviceManager.isLocalLanMode()) }

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var showEditDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<DeviceProfile?>(null) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var confirmIsDestructive by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    fun syncCurrentDevice(mac: String = computerMacAddress, ip: String = computerLocalIpv4) {
        val dev = devices.find { it.id == selectedDeviceId }
        if (dev != null) {
            val updated = dev.copy(mac = mac.trim(), ip = ip.trim())
            deviceManager.updateDevice(updated)
            devices = deviceManager.getDevices()
        }
    }

    fun sendCommand(command: String) {
        syncCurrentDevice()
        deviceManager.setHelperIpv6(helperIpv6Address)
        deviceManager.setLocalLanMode(localLanMode)
        isLoading = true
        statusMessage = "指令傳送中..."
        isStatusError = false
        coroutineScope.launch {
            delay(200)
            val result = if (localLanMode) {
                if (command.startsWith("WAKE:")) {
                    val mac = command.substringAfter("WAKE:")
                    RemoteNetworkUtil.sendLocalMagicPacket(mac)
                } else {
                    val action = command.substringBefore(":")
                    val ip = command.substringAfter(":")
                    RemoteNetworkUtil.sendDirectCommandToPC(ip, action.lowercase(Locale.ROOT))
                }
            } else {
                RemoteNetworkUtil.sendTcpCommand(helperIpv6Address, command)
            }
            statusMessage = result
            isStatusError = result.startsWith("Send failed") || result.startsWith("Address") ||
                    result.startsWith("LAN WoL failed") || result.startsWith("Direct send failed") ||
                    result.startsWith("ERROR:")
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBgPrimary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Header Bar with Telemetry Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "遠端喚醒控制",
                        color = Slate50,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (localLanMode) AccentEmeraldDim else SemanticInfoDim,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (localLanMode) AccentEmeraldBorder else SemanticInfo.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedStatusDot(
                                color = if (localLanMode) AccentEmerald else SemanticInfo,
                                size = 5.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (localLanMode) "LAN BROADCAST (內網直連)" else "WAN TCP 9876 (外網網關)",
                                color = if (localLanMode) AccentEmerald else SemanticInfo,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentEmerald,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        IconButton(
                            onClick = { showAboutDialog = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "關於與更新",
                                tint = Slate300,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Segmented Mode Selector
            SegmentedModeSelector(
                localLanMode = localLanMode,
                onModeChange = {
                    localLanMode = it
                    deviceManager.setLocalLanMode(it)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Target Computers Selector Cockpit Card
            PrecisionSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "目標主機清單",
                            color = Slate50,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Text(
                                text = "${devices.size} 台",
                                color = AccentEmerald,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showAddDeviceDialog = true },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增電腦",
                            tint = AccentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Active Target Detail Banner
                if (currentSelectedDevice != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(AccentEmeraldDim, RoundedCornerShape(8.dp))
                                            .border(1.dp, AccentEmeraldBorder, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Computer,
                                            contentDescription = null,
                                            tint = AccentEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = currentSelectedDevice.name,
                                            color = Slate50,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "當前選定目標主機",
                                            color = Slate400,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        deviceToEdit = currentSelectedDevice
                                        showEditDeviceDialog = true
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "編輯主機設定",
                                        tint = Slate300,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Fast copy address chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            clipboardManager.setText(AnnotatedString(currentSelectedDevice.mac))
                                            Toast.makeText(context, "已複製 MAC 地址", Toast.LENGTH_SHORT).show()
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("MAC", color = Slate500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = currentSelectedDevice.mac.ifBlank { "無 MAC" },
                                                color = Slate200,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Icon(Icons.Default.Share, null, tint = Slate500, modifier = Modifier.size(11.dp))
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            if (currentSelectedDevice.ip.isNotBlank()) {
                                                clipboardManager.setText(AnnotatedString(currentSelectedDevice.ip))
                                                Toast.makeText(context, "已複製 IP 地址", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("IPv4", color = Slate500, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = currentSelectedDevice.ip.ifBlank { "未指定" },
                                                color = Slate200,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Icon(Icons.Default.Share, null, tint = Slate500, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Quick Switcher Carousel
                if (devices.isNotEmpty()) {
                    Text(
                        text = "切換目標主機",
                        color = Slate400,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(devices) { device ->
                            val isSelected = device.id == selectedDeviceId
                            val borderColor = if (isSelected) AccentEmeraldBorder else BorderSubtle
                            val bgColor = if (isSelected) AccentEmeraldDim else DarkSurfaceElevated

                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                    .clickable {
                                        selectedDeviceId = device.id
                                        deviceManager.setSelectedDeviceId(device.id)
                                        computerMacAddress = device.mac
                                        computerLocalIpv4 = device.ip
                                    },
                                color = bgColor,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = device.name,
                                            color = if (isSelected) Slate50 else Slate300,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = device.mac.ifEmpty { "無 MAC" },
                                            color = if (isSelected) AccentEmerald else Slate500,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Configuration & Connection Parameters
            PrecisionSurfaceCard {
                Text(
                    text = "連線位址設定",
                    color = Slate400,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (!localLanMode) {
                    PrecisionOutlinedTextField(
                        value = helperIpv6Address,
                        onValueChange = { helperIpv6Address = it },
                        label = "家用助手 IPv6 位址 (Port 9876)",
                        icon = Icons.Default.Dns,
                        modifier = Modifier.onFocusChanged {
                            if (!it.isFocused) {
                                deviceManager.setHelperIpv6(helperIpv6Address)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                PrecisionOutlinedTextField(
                    value = computerMacAddress,
                    onValueChange = {
                        computerMacAddress = it
                        syncCurrentDevice(mac = it)
                    },
                    label = "目標電腦 MAC 地址 (WoL Magic Packet)",
                    icon = Icons.Default.Lan,
                    modifier = Modifier.onFocusChanged {
                        if (!it.isFocused) {
                            syncCurrentDevice()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                PrecisionOutlinedTextField(
                    value = computerLocalIpv4,
                    onValueChange = {
                        computerLocalIpv4 = it
                        syncCurrentDevice(ip = it)
                    },
                    label = "目標電腦區域 IPv4 (電源控制 Port 9877)",
                    icon = Icons.Default.Computer,
                    modifier = Modifier.onFocusChanged {
                        if (!it.isFocused) {
                            syncCurrentDevice()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Master Hero Trigger: Wake PC
            MasterWakeTriggerButton(
                enabled = !isLoading && computerMacAddress.isNotBlank(),
                isLoading = isLoading,
                targetName = currentSelectedDevice?.name ?: "",
                targetMac = computerMacAddress,
                onClick = { sendCommand("WAKE:${computerMacAddress.trim()}") }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Secondary Power Command Bento Grid (2x2)
            ModernPowerCommandGrid(
                enabled = !isLoading && computerLocalIpv4.isNotBlank(),
                onSleep = { sendCommand("SLEEP:${computerLocalIpv4.trim()}") },
                onHibernate = { sendCommand("HIBERNATE:${computerLocalIpv4.trim()}") },
                onReboot = {
                    confirmAction = "REBOOT"
                    confirmIsDestructive = false
                },
                onShutdown = {
                    confirmAction = "SHUTDOWN"
                    confirmIsDestructive = true
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Terminal HUD Console Output
            TerminalHudStatusCard(
                statusMessage = statusMessage,
                isStatusError = isStatusError,
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Confirmation Dialog for Destructive Power Actions
        if (confirmAction != null) {
            ConfirmPowerDialog(
                actionName = if (confirmAction == "SHUTDOWN") "電腦關機" else "電腦重新開機",
                targetName = currentSelectedDevice?.name ?: "目標主機",
                targetIp = computerLocalIpv4,
                isDestructive = confirmIsDestructive,
                onConfirm = {
                    val act = confirmAction
                    confirmAction = null
                    if (act != null) {
                        sendCommand("$act:${computerLocalIpv4.trim()}")
                    }
                },
                onDismiss = { confirmAction = null }
            )
        }

        // About & Update Dialog
        if (showAboutDialog) {
            Dialog(onDismissRequest = { showAboutDialog = false }) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
                    color = DarkSurfaceElevated,
                    tonalElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "關於與更新",
                            color = Slate50,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "遠端喚醒控制端 (Wake On WAN Remote)",
                            color = Slate400,
                            fontSize = 13.sp
                        )
                        
                        val versionName = remember {
                            try {
                                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
                            } catch (e: Exception) {
                                "1.0"
                            }
                        }
                        Text(
                            text = "目前版本：v$versionName",
                            color = AccentEmerald,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val updateManager = remember { UpdateManager(context) }
                        var isChecking by remember { mutableStateOf(false) }
                        var updateAvailable by remember { mutableStateOf<String?>(null) }
                        var updateMessage by remember { mutableStateOf<String?>(null) }
                        
                        updateMessage?.let { message ->
                            Text(
                                text = message,
                                color = if (updateAvailable != null) AccentEmerald else Slate300,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        
                        if (updateAvailable != null) {
                            Button(
                                onClick = {
                                    updateManager.downloadAndInstall(updateAvailable!!)
                                    Toast.makeText(context, "正在下載更新...", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("下載並安裝新版本", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    isChecking = true
                                    updateMessage = "正在檢查更新..."
                                    updateManager.checkForUpdate(versionName) { hasUpdate, downloadUrl, tag ->
                                        isChecking = false
                                        if (hasUpdate && downloadUrl != null) {
                                            updateAvailable = downloadUrl
                                            updateMessage = "偵測到新版本：${tag ?: ""}"
                                        } else {
                                            updateMessage = "已是最新版本"
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !isChecking,
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                if (isChecking) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = AccentEmerald,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("檢查最新版本", color = Slate200, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases"))
                                context.startActivity(intent)
                            }
                        ) {
                            Text("在 GitHub 上查看發行版", color = Slate400, fontSize = 12.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        TextButton(onClick = { showAboutDialog = false }) {
                            Text("關閉", color = Slate500, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Add Device Dialog
        if (showAddDeviceDialog) {
            AddDeviceDialog(
                onDismiss = { showAddDeviceDialog = false },
                onConfirm = { name, mac, ip ->
                    val newDev = deviceManager.addDevice(name, mac, ip)
                    devices = deviceManager.getDevices()
                    selectedDeviceId = newDev.id
                    computerMacAddress = newDev.mac
                    computerLocalIpv4 = newDev.ip
                    showAddDeviceDialog = false
                }
            )
        }

        // Edit Device Dialog
        if (showEditDeviceDialog && deviceToEdit != null) {
            EditDeviceDialog(
                device = deviceToEdit!!,
                canDelete = devices.size > 1,
                onDismiss = {
                    showEditDeviceDialog = false
                    deviceToEdit = null
                },
                onSave = { name, mac, ip ->
                    val updated = deviceToEdit!!.copy(name = name, mac = mac, ip = ip)
                    deviceManager.updateDevice(updated)
                    devices = deviceManager.getDevices()
                    if (selectedDeviceId == updated.id) {
                        computerMacAddress = updated.mac
                        computerLocalIpv4 = updated.ip
                    }
                    showEditDeviceDialog = false
                    deviceToEdit = null
                },
                onDelete = {
                    val idToDelete = deviceToEdit!!.id
                    deviceManager.deleteDevice(idToDelete)
                    devices = deviceManager.getDevices()
                    selectedDeviceId = deviceManager.getSelectedDeviceId()
                    val nextDev = devices.find { it.id == selectedDeviceId }
                    computerMacAddress = nextDev?.mac ?: ""
                    computerLocalIpv4 = nextDev?.ip ?: ""
                    showEditDeviceDialog = false
                    deviceToEdit = null
                }
            )
        }
    }
}

@Composable
fun PrecisionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        color = DarkSurface,
        tonalElevation = 0.dp
    ) {
        content()
    }
}

@Composable
fun PrecisionOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = Slate400,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = { Icon(icon, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentEmerald,
                unfocusedBorderColor = BorderSubtle,
                focusedTextColor = Slate50,
                unfocusedTextColor = Slate50,
                cursorColor = AccentEmerald,
                focusedContainerColor = DarkSurfaceElevated,
                unfocusedContainerColor = DarkSurfaceElevated
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            shape = RoundedCornerShape(10.dp),
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
fun HardwareCommandCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "buttonScale")

    val bg = if (isDestructive) SemanticDangerDim else DarkSurfaceElevated
    val borderCol = if (isDestructive) SemanticDangerBorder else BorderSubtle
    val iconTint = if (isDestructive) SemanticDanger else Slate300

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) {
                pressed = true
                onClick()
            },
        color = bg
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isDestructive) SemanticDangerDim else DarkSurface,
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    color = if (isDestructive) SemanticDanger else Slate100,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(120)
            pressed = false
        }
    }
}

@Composable
fun AddDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, mac: String, ip: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "新增目標電腦",
                    color = Slate50,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                PrecisionOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "電腦名稱 (例: 臥室主機)",
                    icon = Icons.Default.Computer
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrecisionOutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = "網卡 MAC (例: AA:BB:CC:DD:EE:FF)",
                    icon = Icons.Default.Lan
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrecisionOutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "區域網路 IPv4 (可選)",
                    icon = Icons.Default.Dns
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Slate400)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mac.isNotBlank()) {
                                onConfirm(name.ifBlank { "新主機" }, mac.trim(), ip.trim())
                            }
                        },
                        enabled = mac.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("新增", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EditDeviceDialog(
    device: DeviceProfile,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, mac: String, ip: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var mac by remember { mutableStateOf(device.mac) }
    var ip by remember { mutableStateOf(device.ip) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(16.dp)),
            color = DarkSurfaceElevated,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "編輯目標電腦",
                    color = Slate50,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))
                PrecisionOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "電腦名稱",
                    icon = Icons.Default.Computer
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrecisionOutlinedTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = "網卡 MAC",
                    icon = Icons.Default.Lan
                )
                Spacer(modifier = Modifier.height(10.dp))
                PrecisionOutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it },
                    label = "區域網路 IPv4 (可選)",
                    icon = Icons.Default.Dns
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canDelete) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "刪除", tint = SemanticDanger)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("取消", color = Slate400)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (mac.isNotBlank()) {
                                    onSave(name.ifBlank { "主機" }, mac.trim(), ip.trim())
                                }
                            },
                            enabled = mac.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("儲存", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}