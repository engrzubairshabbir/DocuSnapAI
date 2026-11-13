package com.docusnap.ai.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Deep Obsidian Backgrounds with Neon Undertones
val BgDark = Color(0xFF070B14)
val SurfaceDark = Color(0xFF0F172A)
val CardDark = Color(0xFF131D31)
val BorderDark = Color(0xFF1E293B)

// High-Voltage Glowing Neon Palette
val AccentBlue = Color(0xFF0070F3)
val AccentCyan = Color(0xFF00F5FF)     // Ultra Electric Cyan
val AccentEmerald = Color(0xFF00FF9D)  // Hyper Neon Green
val AccentPurple = Color(0xFF9D4EDD)   // Vibrant Cyber Violet
val AccentPink = Color(0xFFFF007A)     // Laser Magenta Pink
val AccentAmber = Color(0xFFFFB800)    // Radiant Solar Amber

// Neon Glow Overlays
val NeonGlowCyan = Color(0x3300F5FF)
val NeonGlowPurple = Color(0x339D4EDD)
val NeonGlowPink = Color(0x33FF007A)
val NeonBorderCyan = Color(0x8000F5FF)
val NeonBorderPurple = Color(0x809D4EDD)

// Typography
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

/**
 * Animated moving linear gradient brush for live glowing neon shades
 */
@Composable
fun rememberAnimatedNeonBrush(
    colors: List<Color> = listOf(AccentCyan, AccentPurple, AccentPink, AccentBlue, AccentCyan),
    durationMillis: Int = 4500
): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "neonShimmer")
    val offsetProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "neonOffset"
    )
    return Brush.linearGradient(
        colors = colors,
        start = Offset(offsetProgress, offsetProgress),
        end = Offset(offsetProgress + 600f, offsetProgress + 600f)
    )
}

/**
 * Animated subtle glowing border brush
 */
@Composable
fun rememberAnimatedNeonBorder(
    durationMillis: Int = 3000
): Brush {
    val infiniteTransition = rememberInfiniteTransition(label = "borderGlow")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )
    return Brush.horizontalGradient(
        colors = listOf(
            AccentCyan.copy(alpha = 0.4f + 0.5f * progress),
            AccentPurple.copy(alpha = 0.6f - 0.2f * progress),
            AccentPink.copy(alpha = 0.3f + 0.5f * progress),
            AccentCyan.copy(alpha = 0.5f)
        )
    )
}
