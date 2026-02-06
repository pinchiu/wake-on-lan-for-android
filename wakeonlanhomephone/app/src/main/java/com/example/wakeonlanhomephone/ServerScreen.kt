package com.example.wakeonlanhomephone

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wakeonlanhomephone.ui.theme.*
import com.example.wakeonlanhomephone.ui.components.*
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ServerScreen(
    isRunning: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    logs: List<String>,
    onClearLogs: () -> Unit = {},
    onSettings: () -> Unit
) {
    val statusColor = if (isRunning) NeonGreen else Slate500
    val ipv6Address = remember { getDeviceIPv6() ?: "No IPv6 Available" }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackroundDark)
    ) {
        // Background grid pattern (simulated with subtle lines)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NeonBlue.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        startY = 0f,
                        endY = 800f
                    )
                )
        )
        
        // Ambient glow at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-100).dp)
                .size(500.dp)
                .blur(120.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryBlue.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 56.dp, start = 24.dp, end = 24.dp, bottom = 160.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "WOL Manager",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "IPV6 ENABLED",
                        color = Slate400,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
            }

            // Status Circle
            item {
                Box(
                    modifier = Modifier.size(240.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing glow background
                    if (isRunning) {
                        PulsingGlow(
                            color = NeonGreen,
                            size = 240.dp
                        )
                    }
                    
                    // Spinning border ring
                    if (isRunning) {
                        SpinningBorderRing(
                            color = NeonGreen,
                            size = 192.dp
                        )
                    }
                    
                    // Main circle
                    Box(
                        modifier = Modifier
                            .size(192.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, statusColor.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Power icon
                            Box(
                                modifier = Modifier.size(48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            Text(
                                if (isRunning) "RUNNING" else "STOPPED",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            )
                            
                            Spacer(Modifier.height(4.dp))
                            
                            // Online status with ping dot
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isRunning) {
                                    AnimatedPingDot(color = NeonGreen, size = 8.dp)
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Slate500, CircleShape)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isRunning) "Online" else "Offline",
                                    color = statusColor.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
            
            // IPv6 Address Pill
            item {
                Box(
                    modifier = Modifier
                        .background(
                            PrimaryBlue.copy(alpha = 0.1f),
                            RoundedCornerShape(50)
                        )
                        .border(
                            1.dp,
                            PrimaryBlue.copy(alpha = 0.2f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ipv6Address,
                            color = NeonBlue.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            
            // Control Buttons
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Start Button
                    ControlButton(
                        onClick = onStartService,
                        label = "Start",
                        subtitle = "Broadcast Magic Packet",
                        accentColor = NeonGreen,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Stop Button
                    ControlButton(
                        onClick = onStopService,
                        label = "Stop",
                        subtitle = "Terminate Service",
                        accentColor = NeonRed,
                        isStop = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            
            // Logs Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Real-Time Log",
                        color = Slate400,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Clear Log",
                        color = NeonBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onClearLogs() }
                    )
                }
            }
            
            // Log Items
            items(logs.asReversed().take(10)) { log ->
                LogItem(log = log)
            }
        }
    }
}

@Composable
fun ControlButton(
    onClick: () -> Unit,
    label: String,
    subtitle: String,
    accentColor: Color,
    isStop: Boolean = false,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color.White.copy(alpha = 0.1f)
        ),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(accentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isStop) {
                    // Stop icon (square)
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(accentColor, RoundedCornerShape(2.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            
            Text(
                subtitle,
                color = Slate400,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
fun LogItem(log: String) {
    val timestamp = remember {
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceGlass,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(NeonBlue)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // From address
                Row {
                    Text(
                        "FROM:",
                        color = Slate500,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    log.substringBefore(":").ifEmpty { "Local" },
                    color = NeonBlue.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Message and time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Log Entry",
                        color = Slate400,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        timestamp,
                        color = Slate500,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
                
                Text(
                    log,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                
                Spacer(Modifier.height(8.dp))
                
                // Status indicator
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(NeonBlue, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Status: WOL Packet Broadcasted",
                        color = NeonBlue,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Get device IPv6 address
 */
private fun getDeviceIPv6(): String? {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val networkInterface = interfaces.nextElement()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (!address.isLoopbackAddress && address.hostAddress?.contains(":") == true) {
                    // Found IPv6 address, clean it up
                    val ipv6 = address.hostAddress?.split("%")?.get(0) ?: continue
                    if (!ipv6.startsWith("fe80")) { // Skip link-local
                        return ipv6
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}
