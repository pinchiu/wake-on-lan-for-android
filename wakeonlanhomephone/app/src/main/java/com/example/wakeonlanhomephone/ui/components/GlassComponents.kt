package com.example.wakeonlanhomephone.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.example.wakeonlanhomephone.ui.theme.*

/**
 * Precision hardware surface panel with subtle hairline border and top highlight
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
        color = DarkSurface.copy(alpha = 0.85f),
        tonalElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Subtle 1px hairline highlight at top
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
 * Restrained subtle ambient glow for status indicators
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
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .blur(40.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha * 0.35f),
                        Color.Transparent
                    )
                ),
                CircleShape
            )
    )
}

/**
 * Subtle status ring with linear rotation
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
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .size(size)
            .rotate(rotation)
            .border(
                width = strokeWidth,
                brush = Brush.sweepGradient(
                    colors = listOf(
                        color.copy(alpha = 0.3f),
                        Color.Transparent,
                        Color.Transparent,
                        color.copy(alpha = 0.1f)
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Animated ping dot for online/active status
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
            targetValue = 2.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pingScale"
        )
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.6f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "pingAlpha"
        )
        
        // Ping expansion wave
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .background(color.copy(alpha = alpha), CircleShape)
        )
        
        // Solid center core
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}

/**
 * Compact floating navigation bar
 */
@Composable
fun GlassNavBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, BorderStandard, RoundedCornerShape(20.dp)),
            color = DarkSurface.copy(alpha = 0.92f),
            tonalElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Subtle top hairline accent
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AccentEmerald.copy(alpha = 0.3f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

/**
 * Refined Navigation bar item with tactile feedback
 */
@Composable
fun GlassNavItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
    selectedColor: Color = AccentEmerald,
    modifier: Modifier = Modifier
) {
    val activeColor = if (selected) selectedColor else Slate400
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .then(
                    if (selected) {
                        Modifier
                            .background(selectedColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                            .border(1.dp, selectedColor.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides activeColor) {
                icon()
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Slate50 else Slate400,
            maxLines = 1
        )
    }
}
