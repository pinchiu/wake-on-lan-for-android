package com.example.wakeonwanremotephone

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.wakeonwanremotephone.ui.components.AnimatedStatusDot
import com.example.wakeonwanremotephone.ui.components.ConfirmPowerDialog
import com.example.wakeonwanremotephone.ui.components.FrostBackground
import com.example.wakeonwanremotephone.ui.components.MasterWakeTriggerButton
import com.example.wakeonwanremotephone.ui.components.ModernPowerCommandGrid
import com.example.wakeonwanremotephone.ui.components.PrecisionSurfaceCard
import com.example.wakeonwanremotephone.ui.components.SegmentedModeSelector
import com.example.wakeonwanremotephone.ui.components.TerminalHudStatusCard
import com.example.wakeonwanremotephone.ui.theme.AppThemeMode
import com.example.wakeonwanremotephone.ui.theme.FrostAccent
import com.example.wakeonwanremotephone.ui.theme.FrostAccentBorder
import com.example.wakeonwanremotephone.ui.theme.FrostAccentDim
import com.example.wakeonwanremotephone.ui.theme.FrostBorderStandard
import com.example.wakeonwanremotephone.ui.theme.FrostBorderSubtle
import com.example.wakeonwanremotephone.ui.theme.FrostCallout
import com.example.wakeonwanremotephone.ui.theme.FrostCaption
import com.example.wakeonwanremotephone.ui.theme.FrostDanger
import com.example.wakeonwanremotephone.ui.theme.FrostHeadline
import com.example.wakeonwanremotephone.ui.theme.FrostLargeTitle
import com.example.wakeonwanremotephone.ui.theme.FrostMicro
import com.example.wakeonwanremotephone.ui.theme.FrostShadow
import com.example.wakeonwanremotephone.ui.theme.FrostSuccess
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceElevated
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceGlass
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceInset
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceSolid
import com.example.wakeonwanremotephone.ui.theme.FrostTextPrimary
import com.example.wakeonwanremotephone.ui.theme.FrostTextSecondary
import com.example.wakeonwanremotephone.ui.theme.FrostTextTertiary
import com.example.wakeonwanremotephone.ui.theme.ThemePreference
import com.example.wakeonwanremotephone.ui.theme.WakeOnWanRemotePhoneTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    @Suppress("DEPRECATION")
    private fun applyStartupWindowBackground() {
        val dark = when (ThemePreference(this).mode) {
            AppThemeMode.DARK -> true
            AppThemeMode.LIGHT -> false
            AppThemeMode.SYSTEM ->
                (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
        val background = if (dark) 0xFF121214.toInt() else 0xFFF2F2F7.toInt()
        window.setBackgroundDrawable(ColorDrawable(background))
        window.statusBarColor = background
        window.navigationBarColor = if (dark) 0xFF1C1C1E.toInt() else 0xFFE9E9EF.toInt()
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !dark
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyStartupWindowBackground()
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
    val securitySettings = remember { SecuritySettings(context) }
    var devices by remember { mutableStateOf(deviceManager.getDevices()) }
    var selectedDeviceId by remember { mutableStateOf(deviceManager.getSelectedDeviceId()) }
    val currentSelectedDevice = devices.find { it.id == selectedDeviceId } ?: devices.firstOrNull()

    // Configuration state
    var helperIpv6Address by remember { mutableStateOf(deviceManager.getHelperIpv6()) }
    var computerMacAddress by remember { mutableStateOf(currentSelectedDevice?.mac ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(currentSelectedDevice?.ip ?: "") }
    var localLanMode by remember { mutableStateOf(deviceManager.isLocalLanMode()) }

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
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
        statusMessage = context.getString(R.string.command_sending)
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
                    RemoteNetworkUtil.sendDirectCommandToPC(
                        ip,
                        action.lowercase(Locale.ROOT),
                        currentSelectedDevice?.pcSecret?.ifBlank { securitySettings.getPcSecret() }
                            ?: securitySettings.getPcSecret()
                    )
                }
            } else {
                RemoteNetworkUtil.sendTcpCommand(
                    helperIpv6Address,
                    command,
                    securitySettings.getHelperSecret()
                )
            }
            statusMessage = result
            isStatusError = result.startsWith("Send failed") || result.startsWith("Address") ||
                    result.startsWith("LAN WoL failed") || result.startsWith("Direct send failed") ||
                    result.startsWith("Shared ") || result.startsWith("Failed to sign") ||
                    result.startsWith("ERROR:")
            isLoading = false
        }
    }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
    val targetName = currentSelectedDevice?.name ?: stringResource(R.string.default_target_name)

    FrostBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(18.dp))

            // Header: large title, status capsule, About glass button.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_title),
                        color = FrostTextPrimary,
                        style = FrostLargeTitle,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ConnectionStatusCapsule(
                        localLanMode = localLanMode,
                        isLoading = isLoading
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassIconButton(
                        icon = Icons.Default.Security,
                        contentDescription = stringResource(R.string.security_title),
                        onClick = { showSecurityDialog = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassIconButton(
                        icon = Icons.Default.Brightness6,
                        contentDescription = stringResource(R.string.theme_title),
                        onClick = { showThemeDialog = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    GlassIconButton(
                        icon = Icons.Default.Info,
                        contentDescription = stringResource(R.string.about_title),
                        onClick = { showAboutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            SegmentedModeSelector(
                localLanMode = localLanMode,
                onModeChange = {
                    localLanMode = it
                    deviceManager.setLocalLanMode(it)
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Hero target card.
            PrecisionSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(FrostAccentDim, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = null,
                            tint = FrostAccent,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetName,
                            color = FrostTextPrimary,
                            style = FrostHeadline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.current_selected_target),
                            color = FrostTextSecondary,
                            style = FrostCaption
                        )
                    }
                    if (currentSelectedDevice != null) {
                        IconButton(
                            onClick = {
                                deviceToEdit = currentSelectedDevice
                                showEditDeviceDialog = true
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_device_settings),
                                tint = FrostTextSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(FrostSurfaceInset)
                        .border(1.dp, FrostBorderSubtle, RoundedCornerShape(16.dp))
                ) {
                    AddressRow(
                        label = "MAC",
                        value = computerMacAddress.ifBlank { stringResource(R.string.no_mac) },
                        onCopy = {
                            if (computerMacAddress.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(computerMacAddress))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.mac_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    Hairline()
                    AddressRow(
                        label = "IPv4",
                        value = computerLocalIpv4.ifBlank { stringResource(R.string.not_specified) },
                        onCopy = {
                            if (computerLocalIpv4.isNotBlank()) {
                                clipboardManager.setText(AnnotatedString(computerLocalIpv4))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.ip_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                MasterWakeTriggerButton(
                    enabled = !isLoading && computerMacAddress.isNotBlank(),
                    isLoading = isLoading,
                    targetName = currentSelectedDevice?.name ?: "",
                    targetMac = computerMacAddress,
                    onClick = { sendCommand("WAKE:${computerMacAddress.trim()}") }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Five power tiles.
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
                },
                onLock = { sendCommand("LOCK:${computerLocalIpv4.trim()}") }
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Target computers: quick switcher + add.
            PrecisionSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.target_device_list),
                            color = FrostTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = FrostSurfaceInset,
                            border = BorderStroke(1.dp, FrostBorderSubtle)
                        ) {
                            Text(
                                text = pluralStringResource(R.plurals.device_count_plural, devices.size, devices.size),
                                color = FrostTextSecondary,
                                style = FrostMicro,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(FrostAccentDim)
                            .clickable { showAddDeviceDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_computer),
                            tint = FrostAccent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.add_computer),
                        color = FrostTextTertiary,
                        style = FrostCaption
                    )
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(devices) { device ->
                            TargetDeviceCapsule(
                                name = device.name,
                                mac = device.mac.ifEmpty { stringResource(R.string.no_mac) },
                                isSelected = device.id == selectedDeviceId,
                                onClick = {
                                    selectedDeviceId = device.id
                                    deviceManager.setSelectedDeviceId(device.id)
                                    computerMacAddress = device.mac
                                    computerLocalIpv4 = device.ip
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Connection parameters.
            PrecisionSurfaceCard {
                Text(
                    text = stringResource(R.string.connection_settings),
                    color = FrostTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(14.dp))

                if (!localLanMode) {
                    PrecisionOutlinedTextField(
                        value = helperIpv6Address,
                        onValueChange = { helperIpv6Address = it },
                        label = stringResource(R.string.helper_ipv6_label),
                        icon = Icons.Default.Dns,
                        modifier = Modifier.onFocusChangedCompat {
                            deviceManager.setHelperIpv6(helperIpv6Address)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                PrecisionOutlinedTextField(
                    value = computerMacAddress,
                    onValueChange = {
                        computerMacAddress = it
                        syncCurrentDevice(mac = it)
                    },
                    label = stringResource(R.string.target_mac_label),
                    icon = Icons.Default.Lan,
                    modifier = Modifier.onFocusChangedCompat { syncCurrentDevice() }
                )

                Spacer(modifier = Modifier.height(12.dp))

                PrecisionOutlinedTextField(
                    value = computerLocalIpv4,
                    onValueChange = {
                        computerLocalIpv4 = it
                        syncCurrentDevice(ip = it)
                    },
                    label = stringResource(R.string.target_ipv4_label),
                    icon = Icons.Default.Computer,
                    modifier = Modifier.onFocusChangedCompat { syncCurrentDevice() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Activity card.
            TerminalHudStatusCard(
                statusMessage = statusMessage,
                isStatusError = isStatusError,
                isLoading = isLoading
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Confirmation dialog for destructive power actions.
        if (confirmAction != null) {
            ConfirmPowerDialog(
                actionName = if (confirmAction == "SHUTDOWN") {
                    stringResource(R.string.power_shutdown)
                } else {
                    stringResource(R.string.power_reboot)
                },
                targetName = currentSelectedDevice?.name ?: stringResource(R.string.default_target_name),
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

        if (showAboutDialog) {
            AboutGlassDialog(
                versionName = versionName,
                onDismiss = { showAboutDialog = false }
            )
        }

        if (showThemeDialog) {
            ThemeDialog(onDismiss = { showThemeDialog = false })
        }

        // Shared secrets setup (helper + PC daemon)
        if (showSecurityDialog) {
            SecuritySettingsDialog(onDismiss = { showSecurityDialog = false })
        }

        // Add Device Dialog

        if (showAddDeviceDialog) {
            AddDeviceDialog(
                onDismiss = { showAddDeviceDialog = false },
                onConfirm = { name, mac, ip, pcSecret ->
                    val newDev = deviceManager.addDevice(name, mac, ip, pcSecret)
                    devices = deviceManager.getDevices()
                    selectedDeviceId = newDev.id
                    computerMacAddress = newDev.mac
                    computerLocalIpv4 = newDev.ip
                    showAddDeviceDialog = false
                }
            )
        }

        if (showEditDeviceDialog && deviceToEdit != null) {
            EditDeviceDialog(
                device = deviceToEdit!!,
                canDelete = devices.size > 1,
                onDismiss = {
                    showEditDeviceDialog = false
                    deviceToEdit = null
                },
                onSave = { name, mac, ip, pcSecret ->
                    val updated = deviceToEdit!!.copy(
                        name = name,
                        mac = mac,
                        ip = ip,
                        pcSecret = pcSecret
                    )
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
private fun ConnectionStatusCapsule(
    localLanMode: Boolean,
    isLoading: Boolean
) {
    Surface(
        shape = CircleShape,
        color = FrostSurfaceElevated,
        border = BorderStroke(1.dp, FrostBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedStatusDot(
                color = if (localLanMode) FrostSuccess else FrostAccent,
                size = 6.dp
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = if (localLanMode) {
                    stringResource(R.string.mode_lan_direct)
                } else {
                    stringResource(R.string.mode_wan_helper)
                },
                color = FrostTextSecondary,
                style = FrostMicro.copy(fontSize = 12.sp),
                maxLines = 1
            )
            if (isLoading) {
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = FrostAccent,
                    strokeWidth = 1.5.dp
                )
            }
        }
    }
}

@Composable
private fun SelectedProfileCapsule(
    device: DeviceProfile,
    deviceCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = FrostSurfaceGlass,
        border = BorderStroke(1.dp, FrostBorderStandard)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(FrostAccentDim, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = FrostAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = FrostTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.current_selected_target),
                    color = FrostTextSecondary,
                    style = FrostMicro.copy(fontSize = 11.sp),
                    maxLines = 1
                )
            }
            Text(
                text = pluralStringResource(R.plurals.device_count_plural, deviceCount, deviceCount),
                color = FrostTextTertiary,
                style = FrostMicro
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = stringResource(R.string.edit_device_settings),
                tint = FrostTextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddressRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCopy)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = FrostTextSecondary,
            style = FrostCaption.copy(fontSize = 12.sp),
            modifier = Modifier.width(44.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            color = FrostTextPrimary,
            style = FrostMicro.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = null,
            tint = FrostTextTertiary,
            modifier = Modifier.size(13.dp)
        )
    }
}

@Composable
private fun TargetDeviceCapsule(
    name: String,
    mac: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick),
        shape = CircleShape,
        color = if (isSelected) FrostSurfaceSolid else FrostSurfaceInset,
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) FrostAccentBorder else FrostBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isSelected) FrostAccent else FrostTextTertiary, CircleShape)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column {
                Text(
                    text = name,
                    color = FrostTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = mac,
                    color = if (isSelected) FrostTextSecondary else FrostTextTertiary,
                    style = FrostMicro.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = FrostSurfaceElevated,
        border = BorderStroke(1.dp, FrostBorderStandard)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = FrostTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * White glass sheet used by every dialog: 28 dp radius, hairline border and
 * a soft ambient shadow.
 */
@Composable
private fun FrostSheet(
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .shadow(
                    elevation = 28.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = FrostShadow,
                    spotColor = FrostShadow
                ),
            shape = shape,
            color = FrostSurfaceSolid,
            border = BorderStroke(1.dp, FrostBorderStandard),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                content = content
            )
        }
    }
}

@Composable
private fun AboutGlassDialog(
    versionName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentLanguageTag = remember { AppLocale.currentTag(context) }
    val updateManager = remember { UpdateManager(context) }
    var isChecking by remember { mutableStateOf(false) }
    var updateAvailable by remember { mutableStateOf<String?>(null) }
    var updateMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FrostSheet {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(FrostAccentDim, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = FrostAccent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.about_title),
                        color = FrostTextPrimary,
                        style = FrostHeadline
                    )
                    Text(
                        text = stringResource(R.string.about_app_subtitle),
                        color = FrostTextSecondary,
                        style = FrostCaption
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.about_version, versionName),
                color = FrostTextTertiary,
                style = FrostMicro,
                modifier = Modifier.padding(start = 56.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Hairline()

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.language_title),
                color = FrostTextSecondary,
                style = FrostCaption.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(FrostSurfaceInset)
                    .border(1.dp, FrostBorderSubtle, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val languageOptions = listOf(
                    AppLocale.TAG_SYSTEM to stringResource(R.string.language_system),
                    AppLocale.TAG_ZH_TW to stringResource(R.string.language_zh_tw),
                    AppLocale.TAG_EN to stringResource(R.string.language_en)
                )
                languageOptions.forEach { (tag, label) ->
                    val isSelected = tag == currentLanguageTag
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                if (!isSelected) {
                                    AppLocale.set(context, tag)
                                }
                            },
                        shape = RoundedCornerShape(11.dp),
                        color = if (isSelected) FrostSurfaceSolid else Color.Transparent,
                        border = if (isSelected) {
                            BorderStroke(1.dp, FrostBorderSubtle)
                        } else {
                            null
                        }
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) FrostTextPrimary else FrostTextSecondary,
                            style = FrostMicro.copy(fontSize = 12.sp),
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            updateMessage?.let { message ->
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = message,
                    color = if (updateAvailable != null) FrostAccent else FrostTextSecondary,
                    style = FrostCaption
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            if (updateAvailable != null) {
                PrimaryCapsuleButton(
                    label = stringResource(R.string.download_and_install),
                    onClick = {
                        updateManager.downloadAndInstall(updateAvailable!!)
                        Toast.makeText(
                            context,
                            context.getString(R.string.downloading_update_toast),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            } else {
                SecondaryCapsuleButton(
                    enabled = !isChecking,
                    onClick = {
                        isChecking = true
                        updateMessage = context.getString(R.string.checking_update)
                        updateManager.checkForUpdate(versionName) { hasUpdate, downloadUrl, tag ->
                            isChecking = false
                            if (hasUpdate && downloadUrl != null) {
                                updateAvailable = downloadUrl
                                updateMessage = context.getString(R.string.update_found, tag ?: "")
                            } else {
                                updateMessage = context.getString(R.string.already_latest)
                            }
                        }
                    }
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = FrostAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.check_latest_version),
                            color = FrostTextPrimary,
                            style = FrostCallout
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/pinchiu/wake-on-lan-for-android/releases")
                    )
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.view_on_github),
                    color = FrostTextSecondary,
                    style = FrostCaption
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.close),
                    color = FrostTextSecondary,
                    style = FrostCallout
                )
            }
        }
    }
}

@Composable
private fun ThemeDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentMode = remember { ThemePreference(context).mode }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FrostSheet {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(FrostAccentDim, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Brightness6,
                        contentDescription = null,
                        tint = FrostAccent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.theme_title),
                    color = FrostTextPrimary,
                    style = FrostHeadline
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(FrostSurfaceInset)
                    .border(1.dp, FrostBorderSubtle, RoundedCornerShape(16.dp))
            ) {
                val options = listOf(
                    AppThemeMode.SYSTEM to stringResource(R.string.theme_system),
                    AppThemeMode.LIGHT to stringResource(R.string.theme_light),
                    AppThemeMode.DARK to stringResource(R.string.theme_dark)
                )
                options.forEachIndexed { index, (mode, label) ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp)
                                .height(1.dp)
                                .background(FrostBorderSubtle)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                ThemePreference(context).mode = mode
                                onDismiss()
                                if (mode != currentMode) {
                                    (context as? Activity)?.recreate()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            color = if (mode == currentMode) FrostAccent else FrostTextPrimary,
                            style = FrostCallout,
                            fontWeight = if (mode == currentMode) FontWeight.SemiBold else FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        if (mode == currentMode) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = FrostAccent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(FrostBorderSubtle)
    )
}

@Composable
private fun PrimaryCapsuleButton(
    label: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FrostAccent,
            contentColor = Color.White
        )
    ) {
        Text(
            text = label,
            style = FrostCallout.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SecondaryCapsuleButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = FrostSurfaceElevated,
            contentColor = FrostTextPrimary,
            disabledContainerColor = FrostSurfaceInset,
            disabledContentColor = FrostTextTertiary
        ),
        border = BorderStroke(1.dp, FrostBorderStandard)
    ) {
        content()
    }
}

@Composable
fun PrecisionCard(content: @Composable () -> Unit) {
    PrecisionSurfaceCard {
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
            color = FrostTextSecondary,
            style = FrostCaption.copy(fontSize = 12.sp),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = FrostTextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FrostAccent,
                unfocusedBorderColor = FrostBorderStandard,
                focusedTextColor = FrostTextPrimary,
                unfocusedTextColor = FrostTextPrimary,
                cursorColor = FrostAccent,
                focusedContainerColor = FrostSurfaceElevated,
                unfocusedContainerColor = FrostSurfaceElevated,
                focusedLeadingIconColor = FrostAccent,
                unfocusedLeadingIconColor = FrostTextTertiary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = FrostTextPrimary,
                fontSize = 15.sp
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = modifier.fillMaxWidth()
        )
    }
}

/**
 * Password-style field for the optional per-profile PC secret, with an eye
 * toggle and a short explanatory caption.
 */
@Composable
private fun PrecisionSecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    caption: String,
    visible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Column {
        Text(
            text = label,
            color = FrostTextSecondary,
            style = FrostCaption.copy(fontSize = 12.sp),
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = FrostTextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                IconButton(onClick = onToggleVisibility, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (visible) {
                            Icons.Default.VisibilityOff
                        } else {
                            Icons.Default.Visibility
                        },
                        contentDescription = stringResource(
                            if (visible) R.string.hide_secret else R.string.show_secret
                        ),
                        tint = FrostTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = FrostAccent,
                unfocusedBorderColor = FrostBorderStandard,
                focusedTextColor = FrostTextPrimary,
                unfocusedTextColor = FrostTextPrimary,
                cursorColor = FrostAccent,
                focusedContainerColor = FrostSurfaceElevated,
                unfocusedContainerColor = FrostSurfaceElevated,
                focusedLeadingIconColor = FrostAccent,
                unfocusedLeadingIconColor = FrostTextTertiary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = FrostTextPrimary,
                fontSize = 15.sp
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = caption,
            color = FrostTextTertiary,
            style = FrostCaption.copy(fontSize = 11.sp),
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        )
    }
}

/**
 * Small helper so field persistence only runs on focus loss, matching the
 * previous onFocusChanged usage.
 */
private fun Modifier.onFocusChangedCompat(onFocusLost: () -> Unit): Modifier =
    this.then(
        Modifier.onFocusChanged { focusState ->
            if (!focusState.isFocused) onFocusLost()
        }
    )

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
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "buttonScale")
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .scale(scale)
            .clip(shape)
            .border(1.dp, FrostBorderSubtle, shape)
            .clickable(enabled = enabled) {
                pressed = true
                onClick()
            },
        shape = shape,
        color = FrostSurfaceElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        if (isDestructive) FrostDanger.copy(alpha = 0.12f) else FrostAccentDim,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isDestructive) FrostDanger else FrostTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = if (isDestructive) FrostDanger else FrostTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = FrostTextSecondary,
                    style = FrostCaption.copy(fontSize = 12.sp)
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
    onConfirm: (name: String, mac: String, ip: String, pcSecret: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var ip by remember { mutableStateOf("") }
    var pcSecret by remember { mutableStateOf("") }
    var pcSecretVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FrostSheet {
            Text(
                text = stringResource(R.string.add_device_title),
                color = FrostTextPrimary,
                style = FrostHeadline.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(18.dp))

            PrecisionOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.device_name_hint),
                icon = Icons.Default.Computer
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionOutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                label = stringResource(R.string.mac_hint),
                icon = Icons.Default.Lan
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionOutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = stringResource(R.string.lan_ipv4_optional),
                icon = Icons.Default.Dns
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionSecretField(
                value = pcSecret,
                onValueChange = { pcSecret = it },
                label = stringResource(R.string.pc_secret_optional_label),
                caption = stringResource(R.string.pc_secret_optional_caption),
                visible = pcSecretVisible,
                onToggleVisibility = { pcSecretVisible = !pcSecretVisible }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostSurfaceElevated,
                        contentColor = FrostTextPrimary
                    ),
                    border = BorderStroke(1.dp, FrostBorderStandard)
                ) {
                    Text(stringResource(R.string.cancel), style = FrostCallout)
                }
                Button(
                    onClick = {
                        if (mac.isNotBlank()) {
                            onConfirm(
                                name.ifBlank { context.getString(R.string.new_device_default_name) },
                                mac.trim(),
                                ip.trim(),
                                pcSecret.trim()
                            )
                        }
                    },
                    enabled = mac.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostAccent,
                        contentColor = Color.White,
                        disabledContainerColor = FrostAccentDim,
                        disabledContentColor = FrostTextTertiary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.add),
                        style = FrostCallout.copy(fontWeight = FontWeight.SemiBold)
                    )
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
    onSave: (name: String, mac: String, ip: String, pcSecret: String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(device.name) }
    var mac by remember { mutableStateOf(device.mac) }
    var ip by remember { mutableStateOf(device.ip) }
    var pcSecret by remember { mutableStateOf(device.pcSecret) }
    var pcSecretVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        FrostSheet {
            Text(
                text = stringResource(R.string.edit_device_title),
                color = FrostTextPrimary,
                style = FrostHeadline.copy(fontSize = 20.sp)
            )
            Spacer(modifier = Modifier.height(18.dp))

            PrecisionOutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.device_name_label),
                icon = Icons.Default.Computer
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionOutlinedTextField(
                value = mac,
                onValueChange = { mac = it },
                label = stringResource(R.string.mac_label),
                icon = Icons.Default.Lan
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionOutlinedTextField(
                value = ip,
                onValueChange = { ip = it },
                label = stringResource(R.string.lan_ipv4_optional),
                icon = Icons.Default.Dns
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrecisionSecretField(
                value = pcSecret,
                onValueChange = { pcSecret = it },
                label = stringResource(R.string.pc_secret_optional_label),
                caption = stringResource(R.string.pc_secret_optional_caption),
                visible = pcSecretVisible,
                onToggleVisibility = { pcSecretVisible = !pcSecretVisible }
            )

            if (canDelete) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = FrostDanger,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.delete),
                        color = FrostDanger,
                        style = FrostCallout
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostSurfaceElevated,
                        contentColor = FrostTextPrimary
                    ),
                    border = BorderStroke(1.dp, FrostBorderStandard)
                ) {
                    Text(stringResource(R.string.cancel), style = FrostCallout)
                }
                Button(
                    onClick = {
                        if (mac.isNotBlank()) {
                            onSave(
                                name.ifBlank { context.getString(R.string.device_fallback_name) },
                                mac.trim(),
                                ip.trim(),
                                pcSecret.trim()
                            )
                        }
                    },
                    enabled = mac.isNotBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FrostAccent,
                        contentColor = Color.White,
                        disabledContainerColor = FrostAccentDim,
                        disabledContentColor = FrostTextTertiary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.save),
                        style = FrostCallout.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
