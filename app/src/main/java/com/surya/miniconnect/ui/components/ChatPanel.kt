package com.surya.miniconnect.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.surya.miniconnect.domain.model.MeshMessage
import com.surya.miniconnect.domain.model.MeshMessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reusable group-chat panel: a transport-status header, the message list with
 * sender labels, and the composer. Path selection (internet vs mesh) happens in
 * the ChatRouter; this only renders [isOnline] / [meshPeerCount] state.
 */
@Composable
fun ChatPanel(
    messages: List<MeshMessage>,
    isOnline: Boolean,
    meshPeerCount: Int,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        TransportStatus(
            isOnline = isOnline,
            meshPeerCount = meshPeerCount,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        text = "No messages yet. Say hi to your group.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            items(messages, key = { it.id }) { message ->
                GroupMessageBubble(message)
            }
        }
        ChatInput(
            onSend = onSend,
            onAttach = { /* attachments not supported in group chat yet */ },
            enabled = enabled
        )
    }
}

@Composable
private fun TransportStatus(isOnline: Boolean, meshPeerCount: Int, modifier: Modifier = Modifier) {
    val (icon, label, color) = when {
        isOnline -> Triple(
            Icons.Filled.CloudDone,
            if (meshPeerCount > 0) "Online + $meshPeerCount nearby" else "Online",
            MaterialTheme.colorScheme.primary
        )
        meshPeerCount > 0 -> Triple(
            Icons.Filled.CloudOff,
            "Offline mesh — $meshPeerCount nearby",
            MaterialTheme.colorScheme.tertiary
        )
        else -> Triple(
            Icons.Filled.CloudOff,
            "Offline — searching for riders…",
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        Text(text = " $label", style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Message bubble with a sender-name label for group conversations. */
@Composable
private fun GroupMessageBubble(message: MeshMessage, modifier: Modifier = Modifier) {
    val alignment = if (message.isFromMe) Arrangement.End else Arrangement.Start
    val bubbleColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = if (message.isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = if (message.isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = alignment
    ) {
        Surface(shape = shape, color = bubbleColor, modifier = Modifier.widthIn(max = 280.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isFromMe) {
                    Text(
                        text = message.senderName,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = message.content,
                    color = textColor,
                    style = MaterialTheme.typography.bodyLarge
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatTime(message.timestamp),
                        color = textColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    if (message.isFromMe && message.status == MeshMessageStatus.SENDING) {
                        Box(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = "Waiting to send",
                                modifier = Modifier.size(12.dp),
                                tint = textColor.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
