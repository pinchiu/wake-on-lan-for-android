package com.example.wakeonwanremotephone.ui.components

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonwanremotephone.R
import com.example.wakeonwanremotephone.ui.theme.FrostAccent
import com.example.wakeonwanremotephone.ui.theme.FrostAccentDim
import com.example.wakeonwanremotephone.ui.theme.FrostAccentGlow
import com.example.wakeonwanremotephone.ui.theme.FrostBg
import com.example.wakeonwanremotephone.ui.theme.FrostBgSecondary
import com.example.wakeonwanremotephone.ui.theme.FrostBlobCool
import com.example.wakeonwanremotephone.ui.theme.FrostBlobWarm
import com.example.wakeonwanremotephone.ui.theme.FrostBlobWhite
import com.example.wakeonwanremotephone.ui.theme.FrostBorderStandard
import com.example.wakeonwanremotephone.ui.theme.FrostBorderStrong
import com.example.wakeonwanremotephone.ui.theme.FrostBorderSubtle
import com.example.wakeonwanremotephone.ui.theme.FrostCallout
import com.example.wakeonwanremotephone.ui.theme.FrostCaption
import com.example.wakeonwanremotephone.ui.theme.FrostDanger
import com.example.wakeonwanremotephone.ui.theme.FrostHeadline
import com.example.wakeonwanremotephone.ui.theme.FrostInfo
import com.example.wakeonwanremotephone.ui.theme.FrostLog
import com.example.wakeonwanremotephone.ui.theme.FrostMicro
import com.example.wakeonwanremotephone.ui.theme.FrostSuccess
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceElevated
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceGlass
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceInset
import com.example.wakeonwanremotephone.ui.theme.FrostSurfaceSolid
import com.example.wakeonwanremotephone.ui.theme.FrostTextPrimary
import com.example.wakeonwanremotephone.ui.theme.FrostTextSecondary
import com.example.wakeonwanremotephone.ui.theme.FrostTextTertiary
import com.example.wakeonwanremotephone.ui.theme.FrostTrack
import com.example.wakeonwanremotephone.ui.theme.FrostWarning

/**
 * Frost app background: a vertical warm-grey gradient with three very soft,
 * static blurred blobs (white, cool grey and warm grey).
 */
@Composable
fun FrostBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FrostBg, FrostBgSecondary)))
    ) {
        FrostBlob(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 96.dp, y = (-84).dp),
            size = 330.dp,
            color = FrostBlobWhite
        )
        FrostBlob(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-120).dp, y = 40.dp),
            size = 290.dp,
            color = FrostBlobCool
        )
        FrostBlob(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 72.dp, y = 120.dp),
            size = 350.dp,
            color = FrostBlobWarm
        )
        content()
    }
}

@Composable
private fun FrostBlob(
    modifier: Modifier,
    size: Dp,
    color: Color
) {
    Box(
        modifier = modifier
            .size(size)
            .blur(64.dp)
            .background(
                brush = Brush.radialGradient(listOf(color, Color.Transparent)),
                shape = CircleShape
            )
    )
}

/**
 * Frosted glass card with a hairline border, top inner highlight and a soft
 * ambient shadow. This is the shared card recipe for the whole app.
 */
@Composable
fun PrecisionSurfaceCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x14000000),
                spotColor = Color(0x14000000)
            )
            .clip(shape)
            .background(FrostSurfaceGlass)
            .border(1.dp, FrostBorderStandard, shape)
    ) {
        // 1 dp inner top highlight — the "glass edge" of the panel.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, FrostBorderStrong, Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Soft status dot with a slow, low-alpha halo. No neon, no hard pulse.
 */
@Composable
fun AnimatedStatusDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusDotHalo")
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "statusDotHaloScale"
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.26f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "statusDotHaloAlpha"
    )

    Box(
        modifier = modifier.size(size * 2.2f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(haloScale)
                .alpha(haloAlpha)
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

/**
 * iOS-style segmented control: grey capsule track, white selected segment with
 * a quiet shadow and 12 dp inner radius.
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
                .clip(CircleShape)
                .background(FrostTrack.copy(alpha = 0.72f))
                .border(1.dp, FrostBorderSubtle, CircleShape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SegmentedOption(
                selected = localLanMode,
                icon = androidx.compose.material.icons.Icons.Default.Lan,
                label = stringResource(R.string.mode_lan_direct),
                onClick = { onModeChange(true) },
                modifier = Modifier.weight(1f)
            )
            SegmentedOption(
                selected = !localLanMode,
                icon = androidx.compose.material.icons.Icons.Default.Dns,
                label = stringResource(R.string.mode_wan_helper),
                onClick = { onModeChange(false) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (localLanMode) {
                stringResource(R.string.mode_lan_description)
            } else {
                stringResource(R.string.mode_wan_description)
            },
            color = FrostTextSecondary,
            style = FrostCaption.copy(fontSize = 12.sp),
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )
    }
}

@Composable
private fun SegmentedOption(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .shadow(
                            elevation = 4.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = Color(0x1F000000),
                            spotColor = Color(0x1F000000)
                        )
                        .background(FrostSurfaceSolid, shape)
                        .border(1.dp, FrostBorderSubtle, shape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) FrostAccent else FrostTextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) FrostTextPrimary else FrostTextSecondary,
                style = FrostCaption.copy(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1
            )
        }
    }
}

/**
 * Frosted circular wake control with a white ring, a soft blue tint and a
 * label underneath.
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
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "masterWakeScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(180),
        label = "masterWakeAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                FrostAccent.copy(alpha = if (enabled) 0.13f else 0.04f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            Surface(
                modifier = Modifier
                    .size(148.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        clip = false,
                        ambientColor = FrostAccent.copy(alpha = 0.22f),
                        spotColor = FrostAccent.copy(alpha = 0.22f)
                    )
                    .clickable(
                        enabled = enabled && !isLoading,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    ),
                shape = CircleShape,
                color = if (enabled) FrostSurfaceSolid else FrostSurfaceElevated,
                border = BorderStroke(1.5.dp, FrostBorderStrong)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = FrostAccent,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = stringResource(R.string.wake_computer_one_tap),
                            tint = if (enabled) FrostAccent else FrostTextTertiary,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isLoading) {
                stringResource(R.string.sending_wake_packet)
            } else if (targetName.isNotBlank()) {
                stringResource(R.string.wake_target, targetName)
            } else {
                stringResource(R.string.wake_computer_one_tap)
            },
            color = FrostTextPrimary,
            style = FrostHeadline,
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (targetMac.isNotBlank()) {
                stringResource(R.string.mac_prefix, targetMac)
            } else {
                stringResource(R.string.mac_not_set)
            },
            color = FrostTextSecondary,
            style = FrostMicro.copy(fontFamily = FontFamily.Monospace)
        )
    }
}

/**
 * Five grey-glass power tiles in a two-column grid, with a full-width Lock
 * tile underneath. Colour lives only in the small glyphs.
 */
@Composable
fun ModernPowerCommandGrid(
    enabled: Boolean,
    onSleep: () -> Unit,
    onHibernate: () -> Unit,
    onReboot: () -> Unit,
    onShutdown: () -> Unit,
    onLock: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = stringResource(R.string.power_sleep),
                    subtitle = stringResource(R.string.power_sleep_caption),
                    icon = Icons.Rounded.Bedtime,
                    accentColor = FrostWarning,
                    enabled = enabled,
                    onClick = onSleep
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = stringResource(R.string.power_hibernate),
                    subtitle = stringResource(R.string.power_hibernate_caption),
                    icon = Icons.Rounded.AcUnit,
                    accentColor = FrostInfo,
                    enabled = enabled,
                    onClick = onHibernate
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = stringResource(R.string.power_reboot),
                    subtitle = stringResource(R.string.power_reboot_caption),
                    icon = Icons.Rounded.Refresh,
                    accentColor = FrostAccent,
                    enabled = enabled,
                    onClick = onReboot
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                ModernTacticalTile(
                    title = stringResource(R.string.power_shutdown),
                    subtitle = stringResource(R.string.power_shutdown_caption),
                    icon = Icons.Rounded.PowerSettingsNew,
                    accentColor = FrostDanger,
                    isDestructive = true,
                    enabled = enabled,
                    onClick = onShutdown
                )
            }
        }

        ModernTacticalTile(
            title = stringResource(R.string.power_lock),
            subtitle = stringResource(R.string.power_lock_caption),
            icon = Icons.Rounded.Lock,
            accentColor = FrostSuccess,
            enabled = enabled,
            onClick = onLock
        )
    }
}

/**
 * Single grey glass power tile: 18 dp radius, hairline border, coloured glyph,
 * dark label and a quiet caption.
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
    val shape = RoundedCornerShape(18.dp)

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.45f
            isPressed -> 0.9f
            else -> 1f
        },
        animationSpec = tween(180),
        label = "tileAlpha"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .scale(scale)
            .alpha(contentAlpha)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                clip = false,
                ambientColor = Color(0x0F000000),
                spotColor = Color(0x0F000000)
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = shape,
        color = FrostSurfaceElevated,
        border = BorderStroke(1.dp, FrostBorderSubtle)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accentColor.copy(alpha = 0.12f), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = title,
                    color = if (isDestructive) FrostDanger else FrostTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = FrostTextSecondary,
                    style = FrostCaption.copy(fontSize = 11.sp),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Calm activity card. The function name is kept for compatibility; visually it
 * is a normal Frost glass card whose log rows are the only monospace text.
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
    val statusColor = when {
        isStatusError -> FrostDanger
        isLoading -> FrostWarning
        else -> FrostSuccess
    }

    PrecisionSurfaceCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedStatusDot(color = statusColor, size = 7.dp)
                Text(
                    text = stringResource(R.string.activity_title),
                    color = FrostTextPrimary,
                    style = FrostCallout.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(statusMessage))
                    Toast.makeText(
                        context,
                        context.getString(R.string.status_log_copied),
                        Toast.LENGTH_SHORT
                    ).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringResource(R.string.copy_status_log),
                    tint = FrostTextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.activity_last_result),
            color = FrostTextSecondary,
            style = FrostCaption.copy(fontSize = 12.sp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(FrostSurfaceInset)
                .border(1.dp, FrostBorderSubtle, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 11.dp)
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = FrostAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.sending_control_packet),
                        color = FrostTextSecondary,
                        style = FrostCaption.copy(fontSize = 13.sp)
                    )
                }
            } else {
                Text(
                    text = statusMessage,
                    color = if (isStatusError) FrostDanger else FrostTextPrimary,
                    style = FrostLog
                )
            }
        }
    }
}

/**
 * Safeguard sheet for high-impact power commands: white glass, 28 dp radius,
 * capsule buttons. Keeps the `confirm_power_action` test tag.
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
        val shape = RoundedCornerShape(28.dp)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 28.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = Color(0x1F000000),
                    spotColor = Color(0x1F000000)
                ),
            shape = shape,
            color = FrostSurfaceSolid,
            border = BorderStroke(1.dp, FrostBorderStandard),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(
                            if (isDestructive) FrostDanger.copy(alpha = 0.12f) else FrostAccentDim,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isDestructive) Icons.Default.Warning else Icons.Default.Refresh,
                        contentDescription = null,
                        tint = if (isDestructive) FrostDanger else FrostAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.confirm_action_title, actionName),
                    color = FrostTextPrimary,
                    style = FrostHeadline.copy(fontSize = 20.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.confirm_action_message, targetName, targetIp),
                    color = FrostTextSecondary,
                    style = FrostCaption.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

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
                        border = BorderStroke(1.dp, FrostBorderStandard),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = FrostCallout
                        )
                    }

                    Button(
                        // onConfirm is responsible for executing the pending action and dismissing the dialog.
                        // Must not call onDismiss first, or the parent state will clear the action before execution.
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .testTag("confirm_power_action"),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDestructive) FrostDanger else FrostAccent,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.confirm_execute),
                            style = FrostCallout.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}
