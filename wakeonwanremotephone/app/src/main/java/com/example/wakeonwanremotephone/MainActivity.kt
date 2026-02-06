package com.example.wakeonwanremotephone

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet6Address

// Custom Colors
val DeepBlack = Color(0xFF050505)
val Charcoal = Color(0xFF121212)
val GlassWhite = Color(0x1AFFFFFF)
val NeonBlue = Color(0xFF00E5FF)
val NeonGreen = Color(0xFF00FF91)
val NeonPurple = Color(0xFFD500F9)
val NeonRed = Color(0xFFFF1744)
val NeonOrange = Color(0xFFFF9100)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltimateTheme {
                UltimateRemoteScreen()
            }
        }
    }
}

// Constants for saving settings
private const val PREFS_NAME = "RemoteControlPrefs"
private const val KEY_IPV6 = "helperIpv6Address"
private const val KEY_MAC = "computerMacAddress"
private const val KEY_IPV4 = "computerLocalIpv4"

@Composable
fun UltimateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DeepBlack,
            surface = Charcoal,
            primary = NeonBlue,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun UltimateRemoteScreen() {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // State
    var helperIpv6Address by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV6, "") ?: "") }
    var computerMacAddress by remember { mutableStateOf(sharedPrefs.getString(KEY_MAC, "") ?: "") }
    var computerLocalIpv4 by remember { mutableStateOf(sharedPrefs.getString(KEY_IPV4, "") ?: "") }

    var statusMessage by remember { mutableStateOf("") }
    var isStatusError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun sendCommand(command: String) {
        isLoading = true
        statusMessage = "Sending..."
        isStatusError = false
        coroutineScope.launch {
            // Fake delay for UI feedback feeling
            delay(300)
            val result = sendUdpCommand(helperIpv6Address, command)
            statusMessage = result
            isStatusError = result.startsWith("Send failed") || result.startsWith("Address")
            isLoading = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepBlack, Color(0xFF1A1A1A))
                )
            )
    ) {
        // Background Ambient Glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-50).dp)
                .blur(100.dp)
                .background(NeonBlue.copy(alpha = 0.15f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 50.dp)
                .blur(100.dp)
                .background(NeonPurple.copy(alpha = 0.15f), CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ULTIMATE",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "REMOTE",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(if (isLoading) NeonOrange else NeonGreen, CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Configuration Section
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CONFIGURATION",
                        color = NeonBlue,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    NeonTextField(
                        value = helperIpv6Address,
                        onValueChange = {
                            helperIpv6Address = it
                            sharedPrefs.edit().putString(KEY_IPV6, it).apply()
                        },
                        label = "Helper IPv6",
                        icon = Icons.Default.Dns
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerMacAddress,
                        onValueChange = {
                            computerMacAddress = it
                            sharedPrefs.edit().putString(KEY_MAC, it).apply()
                        },
                        label = "Target MAC",
                        icon = Icons.Default.Lan
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    NeonTextField(
                        value = computerLocalIpv4,
                        onValueChange = {
                            computerLocalIpv4 = it
                            sharedPrefs.edit().putString(KEY_IPV4, it).apply()
                        },
                        label = "Target IPv4",
                        icon = Icons.Default.Computer
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                item {
                    CommandButton(
                        text = "WAKE",
                        icon = Icons.Rounded.Bolt,
                        color = NeonGreen,
                        onClick = { sendCommand("WAKE:${computerMacAddress.trim()}") },
                        enabled = !isLoading
                    )
                }
                item {
                    CommandButton(
                        text = "SLEEP",
                        icon = Icons.Rounded.Bedtime,
                        color = NeonBlue,
                        onClick = { sendCommand("SLEEP:${computerLocalIpv4.trim()}") },
                        enabled = !isLoading
                    )
                }
                item {
                    CommandButton(
                        text = "REBOOT",
                        icon = Icons.Rounded.Refresh,
                        color = NeonOrange,
                        onClick = { sendCommand("REBOOT:${computerLocalIpv4.trim()}") },
                        enabled = !isLoading
                    )
                }
                item {
                    CommandButton(
                        text = "HIBERNATE",
                        icon = Icons.Rounded.AcUnit,
                        color = Color(0xFF00B0FF),
                        onClick = { sendCommand("HIBERNATE:${computerLocalIpv4.trim()}") },
                        enabled = !isLoading
                    )
                }
                item {
                    CommandButton(
                        text = "SHUTDOWN",
                        icon = Icons.Rounded.PowerSettingsNew,
                        color = NeonRed,
                        onClick = { sendCommand("SHUTDOWN:${computerLocalIpv4.trim()}") },
                        enabled = !isLoading
                    )
                }
            }

            // Status Footer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                contentAlignment = Alignment.Center
            ) {
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        color = if (isStatusError) NeonRed else NeonGreen,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun GlassCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
    ) {
        content()
    }
}

@Composable
fun NeonTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.7f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = NeonBlue) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NeonBlue,
            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = NeonBlue,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun CommandButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "buttonScale")

    Button(
        onClick = {
            pressed = true
            onClick()
            // Reset pressed state after a short delay to simulate click
            // In a real app, this would be handled better by interaction source
        },
        enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier
            .aspectRatio(1.2f)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .background(GlassWhite)
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
    // Reset pressed state helper
    LaunchedEffect(pressed) {
        if (pressed) {
            delay(100)
            pressed = false
        }
    }
}

private suspend fun sendUdpCommand(host: String, command: String): String {
    return withContext(Dispatchers.IO) {
        if (host.isBlank() || command.isBlank()) {
            return@withContext "Address or command cannot be empty!"
        }
        try {
            val serverAddr = Inet6Address.getByName(host)
            val commandBytes = command.toByteArray()
            val packet = DatagramPacket(commandBytes, commandBytes.size, serverAddr, 9876)
            
            // Check for IPv6 format to avoid common errors if possible, 
            // but for now just trusting the input as per original app
            
            DatagramSocket().use { socket ->
                socket.send(packet)
            }
            "Command sent: $command"
        } catch (e: Exception) {
            "Send failed: ${e.message}"
        }
    }
}