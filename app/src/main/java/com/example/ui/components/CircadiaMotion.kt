package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class GeminiLoaderSize(
    val dotDiameter: Dp,
    val dotSpacing: Dp,
    val bounceHeight: Dp,
    val containerHeight: Dp
) {
    Hero(
        dotDiameter = 12.dp,
        dotSpacing = 12.dp,
        bounceHeight = 9.dp,
        containerHeight = 40.dp
    ),
    Compact(
        dotDiameter = 7.dp,
        dotSpacing = 6.dp,
        bounceHeight = 4.5.dp,
        containerHeight = 22.dp
    )
}

/**
 * Gemini-inspired bouncing three-dot wave indicator in Circadia's theme accent colors
 * (Warm Amber, Soft Teal, Sleep Indigo).
 */
@Composable
fun GeminiThreeDotsLoader(
    size: GeminiLoaderSize = GeminiLoaderSize.Hero,
    modifier: Modifier = Modifier,
    description: String = "Generating response"
) {
    val infiniteTransition = rememberInfiniteTransition(label = "geminiThreeDotsWave")
    val bouncePx = with(LocalDensity.current) { size.bounceHeight.toPx() }
    val dotColors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    Row(
        modifier = modifier
            .height(size.containerHeight)
            .semantics { contentDescription = description }
            .testTag("gemini_three_dots_loader"),
        horizontalArrangement = Arrangement.spacedBy(size.dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dotColors.forEachIndexed { index, dotColor ->
            val phase by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1050
                        0f at 0 using FastOutSlowInEasing
                        1f at 260 using FastOutSlowInEasing
                        0f at 540 using FastOutSlowInEasing
                        0f at 1050
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(
                        offsetMillis = index * 140,
                        offsetType = StartOffsetType.FastForward
                    )
                ),
                label = "geminiDotPhase_$index"
            )

            GeminiWaveDot(
                color = dotColor,
                diameter = size.dotDiameter,
                phase = phase,
                bouncePx = bouncePx
            )
        }
    }
}

@Composable
private fun GeminiWaveDot(
    color: Color,
    diameter: Dp,
    phase: Float,
    bouncePx: Float
) {
    val scale = 0.86f + (0.32f * phase)
    val dotAlpha = 0.55f + (0.45f * phase)
    val haloAlpha = 0.28f * phase

    Box(
        modifier = Modifier
            .size(diameter * 1.7f)
            .graphicsLayer {
                translationY = -bouncePx * phase
            },
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial aura behind the bouncing dot
        Box(
            modifier = Modifier
                .size(diameter * 1.55f)
                .graphicsLayer {
                    scaleX = scale * 1.15f
                    scaleY = scale * 1.15f
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
        // Core dot
        Box(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = dotAlpha
                }
                .clip(CircleShape)
                .background(color)
        )
    }
}

/**
 * Elevated compact status card pairing the 3-dot wave with active sync/operation copy.
 */
@Composable
fun GeminiSyncStatusBanner(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("gemini_sync_status_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GeminiThreeDotsLoader(
                size = GeminiLoaderSize.Compact,
                description = message
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun rememberPressScale(
    pressedScale: Float = 0.96f
): Pair<MutableInteractionSource, Float> {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    return interactionSource to scale
}
