package com.example.wakeonwanremotephone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.widget.Toast
import com.example.wakeonwanremotephone.DeviceProfile
import com.example.wakeonwanremotephone.ui.theme.*

/**
 * Precision Hardware Card Surface with subtle hairline gradient border
 */
@Composable
fun PrecisionSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        color = DarkSurface,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle 1px hairline highlight at top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

/**
 * Animated Ping Dot indicator for online/standby telemetry
 */
@Composable
fun AnimatedStatusDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusDotPing")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "alpha"
        )

        // Expanding radio wave
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .background(color.copy(alpha = alpha), CircleShape)
        )

        // Core solid dot
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

/**
 * Segmented Hardware Mode Switcher (LAN WoL Direct vs WAN IPv6 Gateway)
 */
@Composable
fun SegmentedModeSelector(
    localLanMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceElevated)
                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Local LAN Option
            val isLanSelected = localLanMode
            val lanBg = if (isLanSelected) AccentEmeraldDim else Color.Transparent
            val lanBorder = if (isLanSelected) AccentEmeraldBorder else Color.Transparent
            val lanTextCol = if (isLanSelected) AccentEmerald else Slate400

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lanBg)
                    .border(1.dp, lanBorder, RoundedCornerShape(8.dp))
                    .clickable { onModeChange(true) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lan,
                        contentDescription = null,
                        tint = lanTextCol,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "區域網路直連",
                        color = if (isLanSelected) Slate50 else Slate400,
                        fontSize = 12.sp,
                        fontWeight = if (isLanSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            // Remote WAN Option
            val isWanSelected = !localLanMode
            val wanBg = if (isWanSelected) SemanticInfoDim else Color.Transparent
            val wanBorder = if (isWanSelected) SemanticInfo.copy(alpha = 0.35f) else Color.Transparent
            val wanTextCol = if (isWanSelected) SemanticInfo else Slate400

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(wanBg)
                    .border(1.dp, wanBorder, RoundedCornerShape(8.dp))
                    .clickable { onModeChange(false) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = wanTextCol,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "遠端助手 (WAN)",
                        color = if (isWanSelected) Slate50 else Slate400,
                        fontSize = 12.sp,
                        fontWeight = if (isWanSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mode Explanatory Subtitle
        Text(
            text = if (localLanMode)
                "內網模式：發送 UDP 255.255.255.255:9 WoL 廣播封包"
            else
                "外網模式：透過 TCP Port 9876 轉發給常駐家用助手",
            color = if (localLanMode) AccentEmerald.copy(alpha = 0.8f) else SemanticInfo.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/**
 * Master Tactical Hero Wake Button with ambient breathing glow and spring bounce
 */
@Composable
fun MasterWakeTriggerButton(
    enabled: Boolean,
    isLoading: Boolean,
    targetName: String,
    targetMac: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "masterWakeScale"
    )

    // Breathing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "wakeGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        // Ambient glow halo behind button
        if (enabled && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .blur(20.dp)
                    .background(
                        AccentEmerald.copy(alpha = glowAlpha),
                        RoundedCornerShape(16.dp)
                    )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = if (enabled) AccentEmeraldBorder else BorderSubtle,
                    shape = RoundedCornerShape(16.dp)
                )
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                ),
            color = if (enabled) AccentEmerald else DarkSurfaceElevated,
            tonalElevation = 6.dp
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Black,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "正在傳送喚醒封包...",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (enabled) Color.Black.copy(alpha = 0.18f) else DarkSurface,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = if (enabled) Color.Black else Slate500,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (targetName.isNotBlank()) "喚醒 $targetName" else "一鍵喚醒電腦 (WAKE)",
                                    color = if (enabled) Color.Black else Slate500,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp
                                )
                                Text(
                                    text = if (targetMac.isNotBlank()) "MAC: $targetMac" else "尚未設定 MAC",
                                    color = if (enabled) Color.Black.copy(alpha = 0.75f) else Slate600,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (enabled) Color.Black.copy(alpha = 0.8f) else Slate600,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Tactical 2x2 Bento Grid for Secondary PC Power Commands
 */
@Composable
fun ModernPowerCommandGrid(
    enabled: Boolean,
    onSleep: () -> Unit,
    onHibernate: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Sleep & Hibernate (Amber/Warning)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = "電腦睡眠",
                    subtitle = "CMD: SLEEP",
                    icon = Icons.Rounded.Bedtime,
                    accentColor = SemanticWarning,
                    enabled = enabled,
                    onClick = onSleep
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = "電腦休眠",
                    subtitle = "CMD: HIBERNATE",
                    icon = Icons.Rounded.AcUnit,
                    accentColor = SemanticWarning,
                    enabled = enabled,
                    onClick = onHibernate
                )
            }
        }

        // Row 2: Reboot & Shutdown (Sky / Crimson)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = "重新開機",
                    subtitle = "CMD: REBOOT",
                    icon = Icons.Rounded.Refresh,
                    accentColor = SemanticInfo,
                    enabled = enabled,
                    onClick = onReboot
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = "電腦關機",
                    subtitle = "CMD: SHUTDOWN",
                    icon = Icons.Rounded.PowerSettingsNew,
                    accentColor = SemanticDanger,
                    isDestructive = true,
                    enabled = enabled,
                    onClick = onShutdown
                )
            }
        }
    }
}

/**
 * Tactical Single Power Tile with spring bounce and status badge
 */
@Composable
fun ModernTacticalTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isDestructive: Boolean = false,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileScale"
    )

    val bgColor = if (isDestructive) SemanticDangerDim else DarkSurfaceElevated
    val borderColor = if (isDestructive) SemanticDangerBorder else BorderSubtle

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        color = bgColor
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
                        accentColor.copy(alpha = 0.12f),
                        RoundedCornerShape(10.dp)
                    )
                    .border(
                        1.dp,
                        accentColor.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    color = if (isDestructive) SemanticDanger else Slate100,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = Slate400,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}

/**
 * Terminal HUD Status Console with retro-modern telemetry output and copy capability
 */
@Composable
fun TerminalHudStatusCard(
    statusMessage: String,
    isStatusError: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    if (statusMessage.isEmpty() && !isLoading) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isStatusError) SemanticDangerBorder else AccentEmeraldBorder,
                RoundedCornerShape(12.dp)
            ),
        color = DarkBgSecondary
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Terminal Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(9.dp).background(SemanticDanger, CircleShape))
                    Box(modifier = Modifier.size(9.dp).background(SemanticWarning, CircleShape))
                    Box(modifier = Modifier.size(9.dp).background(AccentEmerald, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TERMINAL PROTOCOL OUTPUT",
                        color = Slate400,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(statusMessage))
                        Toast.makeText(context, "已複製狀態日誌至剪貼簿", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "複製狀態日誌",
                        tint = Slate400,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Terminal Body
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = ">>> ",
                    color = if (isStatusError) SemanticDanger else AccentEmerald,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isLoading) "正在傳送網路控制封包，請稍候..." else statusMessage,
                    color = if (isStatusError) SemanticDanger else Slate100,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Safeguard Dialog for High-Impact Power Commands (Shutdown & Reboot)
 */
@Composable
fun ConfirmPowerDialog(
    actionName: String,
    targetName: String,
    targetIp: String,
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                if (isDestructive) SemanticDangerDim else SemanticInfoDim,
                                RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDestructive) Icons.Default.Warning else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isDestructive) SemanticDanger else SemanticInfo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "確認執行「$actionName」？",
                        color = Slate50,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "即將向目標電腦「$targetName」($targetIp:9877) 發送 UDP 控制封包。請確保電腦已儲存未完成的工作。",
                    color = Slate300,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消", color = Slate400, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onDismiss()
                            onConfirm()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) SemanticDanger else SemanticInfo
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "確認執行",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
