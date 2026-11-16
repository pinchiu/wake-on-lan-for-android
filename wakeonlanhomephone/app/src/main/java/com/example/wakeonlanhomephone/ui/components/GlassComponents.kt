package com.example.wakeonlanhomephone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.wakeonlanhomephone.R
import com.example.wakeonlanhomephone.ui.theme.*

/**
 * Frost background: a static vertical grey gradient with two or three very
 * soft ambient blobs. Real backdrop blur is not available below Android 12,
 * so the softness comes from radial gradients plus a gentle blur where the
 * platform supports it.
 */
@Composable
fun FrostBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(FrostBg, FrostBgSecondary)
            )
        )
    ) {
        AmbientBlob(
            color = Color.White,
            size = 320.dp,
            alpha = 0.34f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-80).dp)
        )
        AmbientBlob(
            color = Color(0xFFD9DEE8),
            size = 280.dp,
            alpha = 0.26f,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-90).dp, y = (-40).dp)
        )
        AmbientBlob(
            color = Color(0xFFE7E3DD),
            size = 260.dp,
            alpha = 0.30f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 70.dp, y = 60.dp)
        )
        content()
    }
}

@Composable
private fun AmbientBlob(
    color: Color,
    size: Dp,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .blur(64.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.45f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}

/**
 * Frosted card: translucent white fill, hairline border, one-pixel inner top
 * highlight and a soft ambient shadow.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Surface(
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = FrostShadow,
                spotColor = FrostShadow
            )
            .border(1.dp, FrostBorderStandard, shape)
            .clip(shape),
        color = FrostSurfaceGlass,
        shape = shape,
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Inner top highlight, like light catching a glass edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                FrostBorderStrong,
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(18.dp),
                content = content
            )
        }
    }
}

/**
 * Soft ambient blob for status emphasis. Kept extremely low-alpha; the glow is
 * decoration, never the only signal.
 */
@Composable
fun PulsingGlow(
    color: Color,
    size: Dp = 160.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .blur(48.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}

/**
 * Thin neutral progress ring with a restrained accent arc. Only used while a
 * connection is being established.
 */
@Composable
fun SpinningBorderRing(
    color: Color,
    size: Dp = 180.dp,
    strokeWidth: Dp = 1.dp,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spinRotation"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .rotate(rotation)
    ) {
        val strokePx = strokeWidth.toPx()
        val radius = (this.size.minDimension - strokePx) / 2f
        drawCircle(
            color = FrostBorderStandard,
            radius = radius,
            style = Stroke(width = strokePx)
        )
        drawArc(
            color = color.copy(alpha = 0.9f),
            startAngle = -90f,
            sweepAngle = 80f,
            useCenter = false,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
            topLeft = androidx.compose.ui.geometry.Offset(
                center.x - radius,
                center.y - radius
            ),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)
        )
    }
}

/**
 * Status dot with a soft, slow halo.
 */
@Composable
fun AnimatedPingDot(
    color: Color,
    size: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size * 2),
        contentAlignment = Alignment.Center
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "ping")

        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pingScale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.22f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pingAlpha"
        )

        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .background(color.copy(alpha = alpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

/**
 * Floating iOS-style capsule navigation bar.
 */
@Composable
fun GlassNavBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier.padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = shape,
                    ambientColor = FrostShadow,
                    spotColor = FrostShadow
                )
                .border(1.dp, FrostBorderSubtle, shape)
                .clip(shape),
            color = FrostSurfaceSolid,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White,
                                    Color.Transparent
                                )
                            )
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

/**
 * Nav item: icon over an 11 sp label. The selected item gets a soft accent
 * capsule and an accent glyph.
 */
@Composable
fun GlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    selectedColor: Color = FrostAccent,
    modifier: Modifier = Modifier
) {
    val activeColor = if (selected) selectedColor else FrostTextSecondary
    val capsule = RoundedCornerShape(18.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(capsule)
            .background(
                if (selected) selectedColor.copy(alpha = 0.12f) else Color.Transparent,
                capsule
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides activeColor) {
                icon()
            }
        }

        Spacer(Modifier.height(3.dp))

        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) FrostTextPrimary else FrostTextSecondary,
            maxLines = 1,
            letterSpacing = 0.1.sp
        )
    }
}

/**
 * Large frosted wake circle with a label underneath. The circle is 148 dp with
 * a white ring and a faint blue tint; the power glyph is the single accent.
 */
@Composable
fun MasterWakeButton(
    enabled: Boolean,
    deviceName: String,
    deviceMac: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "wakeScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(140),
        label = "wakeAlpha"
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(scale)
                .alpha(contentAlpha)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .size(196.dp)
                            .blur(34.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        FrostAccent.copy(alpha = 0.14f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                }

                Surface(
                    modifier = Modifier
                        .size(148.dp)
                        .shadow(
                            elevation = 16.dp,
                            shape = CircleShape,
                            ambientColor = FrostShadow,
                            spotColor = FrostShadow
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.92f), CircleShape)
                        .clickable(
                            enabled = enabled,
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        ),
                    shape = CircleShape,
                    color = FrostSurfaceElevated,
                    tonalElevation = 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.58f),
                                        (if (enabled) FrostAccent else Color.White)
                                            .copy(alpha = 0.08f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (enabled) FrostAccent else FrostTextTertiary,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = if (deviceName.isNotBlank()) {
                    stringResource(R.string.wake_button_named, deviceName)
                } else {
                    stringResource(R.string.wake_button_default)
                },
                style = FrostHeadline,
                color = if (enabled) FrostTextPrimary else FrostTextTertiary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = if (deviceMac.isNotBlank()) {
                    stringResource(R.string.wake_mac_configured, deviceMac)
                } else {
                    stringResource(R.string.wake_mac_missing)
                },
                style = FrostCaption.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                color = FrostTextSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Confirmation sheet for power actions: white glass, 28 dp radius, capsule
 * buttons. The confirm button keeps its instrumentation tag.
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
                    elevation = 20.dp,
                    shape = shape,
                    ambientColor = FrostShadow,
                    spotColor = FrostShadow
                )
                .border(1.dp, FrostBorderSubtle, shape)
                .clip(shape),
            color = FrostSurfaceSolid,
            shape = shape,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (isDestructive) FrostDangerDim else FrostAccentDim,
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isDestructive) Icons.Default.Warning else Icons.Default.Refresh,
                            contentDescription = null,
                            tint = if (isDestructive) FrostDanger else FrostAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.confirm_power_title, actionName),
                        style = FrostHeadline,
                        color = FrostTextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.confirm_power_body, targetName, targetIp),
                    style = FrostBody.copy(fontSize = 14.sp, lineHeight = 20.sp),
                    color = FrostTextSecondary
                )

                Spacer(modifier = Modifier.height(22.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = CircleShape,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FrostBorderStandard),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FrostTextPrimary
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_cancel),
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
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.action_confirm),
                            style = FrostCallout,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
