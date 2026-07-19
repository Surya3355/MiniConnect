package com.surya.miniconnect.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.surya.miniconnect.domain.model.AudioRoute

private val BubbleGreen = Color(0xFF16A34A)
private val BubbleGreenDim = Color(0xFF0F7A37)
private val BubbleRed = Color(0xFFDC2626)

/**
 * The interactive green call bubble modeled on the RiderConnect ride UI.
 *
 * Collapsed: mic status + decorative wave + current output source.
 * Tapping expands three action bubbles (mute, output, end) above it.
 * Controlled component — the host owns [expanded] so it can render a
 * tap-to-dismiss scrim behind the bubble.
 */
@Composable
fun CallBubble(
    isMuted: Boolean,
    isSpeaking: Boolean,
    audioRoute: AudioRoute,
    isReconnecting: Boolean,
    isInterrupted: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleMute: () -> Unit,
    onCycleOutput: () -> Unit,
    onEndCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically { it / 2 } + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + slideOutVertically { it / 2 } + scaleOut(targetScale = 0.7f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.padding(bottom = 14.dp)
            ) {
                ActionBubble(
                    icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    container = if (isMuted) BubbleRed else Color.White,
                    content = if (isMuted) Color.White else BubbleGreen,
                    onClick = onToggleMute
                )
                ActionBubble(
                    icon = audioRoute.icon(),
                    label = audioRoute.label(),
                    container = Color.White,
                    content = BubbleGreen,
                    onClick = onCycleOutput
                )
                ActionBubble(
                    icon = Icons.Filled.CallEnd,
                    label = "End",
                    container = BubbleRed,
                    content = Color.White,
                    onClick = onEndCall
                )
            }
        }

        CollapsedPill(
            isMuted = isMuted,
            isSpeaking = isSpeaking,
            audioRoute = audioRoute,
            isReconnecting = isReconnecting,
            isInterrupted = isInterrupted,
            onClick = { onExpandedChange(!expanded) }
        )
    }
}

@Composable
private fun CollapsedPill(
    isMuted: Boolean,
    isSpeaking: Boolean,
    audioRoute: AudioRoute,
    isReconnecting: Boolean,
    isInterrupted: Boolean,
    onClick: () -> Unit
) {
    val container = if (isMuted || isInterrupted) BubbleGreenDim else BubbleGreen

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = container,
        shadowElevation = 8.dp,
        modifier = Modifier
            .height(64.dp)
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

            Column {
                Text(
                    text = when {
                        isInterrupted -> "PAUSED"
                        isReconnecting -> "RECONNECTING"
                        isMuted -> "MUTED"
                        else -> "MIC LIVE"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = when {
                        isInterrupted -> "phone call in progress"
                        isReconnecting -> "restoring connection…"
                        isMuted -> "tap to open controls"
                        else -> "voice active"
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            WaveBars(
                active = isSpeaking && !isMuted && !isInterrupted,
                color = Color.White
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = audioRoute.icon(),
                    contentDescription = audioRoute.label(),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun WaveBars(active: Boolean, color: Color, barCount: Int = 5) {
    val transition = rememberInfiniteTransition(label = "wave")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(28.dp)
    ) {
        repeat(barCount) { i ->
            val animated by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500 + (i % 3) * 120),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 90)
                ),
                label = "bar$i"
            )
            // When idle, settle to a short flat bar
            val fraction by animateFloatAsState(
                targetValue = if (active) animated else 0.22f,
                animationSpec = tween(250),
                label = "barFraction$i"
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((28 * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ActionBubble(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = CircleShape,
            color = container,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = content, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color.Black.copy(alpha = 0.55f)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

private fun AudioRoute.icon(): ImageVector = when (this) {
    AudioRoute.EARPIECE -> Icons.Filled.Hearing
    AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
}

private fun AudioRoute.label(): String = when (this) {
    AudioRoute.EARPIECE -> "Earpiece"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth"
}
