package com.surya.miniconnect.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.domain.model.Participant
import com.surya.miniconnect.domain.model.ParticipantState
import com.surya.miniconnect.presentation.voice.VoiceCallUiState
import com.surya.miniconnect.ui.components.CallBubble
import com.surya.miniconnect.ui.components.ChatPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideCallScreen(
    uiState: VoiceCallUiState,
    onToggleMute: () -> Unit,
    onCycleOutput: () -> Unit,
    onEndCall: () -> Unit,
    onBack: () -> Unit,
    onSendChat: (String) -> Unit,
    onChatOpened: () -> Unit,
    onToggleMeshTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bubbleExpanded by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var chatOpen by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.callState is CallState.Ended) {
        onBack()
        return
    }

    // A call failure while OFFLINE isn't a dead end — the mesh still works, so
    // keep the ride screen up (with quick signals / chat). Only show the hard
    // "Couldn't join" card when we're online (e.g. a bad code).
    val failed = uiState.callState as? CallState.Failed
    if (failed != null && !uiState.isOffline) {
        CallFailed(message = failed.message, onBack = onBack, modifier = modifier)
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Static RiderConnect-style ride screen (non-interactive)
        com.surya.miniconnect.ui.components.RideMapBackdrop()

        // Top overlay: room code (share) + connection details toggle
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CodeChip(
                    code = uiState.roomCode,
                    onCopy = {
                        copyCode(context, uiState.roomCode)
                        Toast.makeText(context, "Code copied!", Toast.LENGTH_SHORT).show()
                    }
                )
                DetailsChip(
                    count = uiState.connectedCount,
                    expanded = detailsExpanded,
                    onClick = { detailsExpanded = !detailsExpanded }
                )
            }
            AnimatedVisibility(visible = detailsExpanded) {
                ConnectionDetails(participants = uiState.participants)
            }
            Spacer(modifier = Modifier.height(6.dp))
            MeshTestChip(enabled = uiState.meshTestMode, onClick = onToggleMeshTest)
        }

        // Tap-outside scrim to collapse the bubble
        if (bubbleExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { bubbleExpanded = false }
            )
        }

        // The green call bubble
        CallBubble(
            isMuted = uiState.isMuted,
            isSpeaking = uiState.isSpeaking,
            audioRoute = uiState.audioRoute,
            isReconnecting = uiState.isReconnecting,
            isInterrupted = uiState.isInterrupted,
            expanded = bubbleExpanded,
            onExpandedChange = { bubbleExpanded = it },
            onToggleMute = onToggleMute,
            onCycleOutput = onCycleOutput,
            onEndCall = onEndCall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        )

        // Chat button — opens the in-call group chat sheet. Unread badge.
        BadgedBox(
            badge = {
                if (uiState.chatUnread > 0) {
                    Badge { Text(if (uiState.chatUnread > 9) "9+" else "${uiState.chatUnread}") }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    chatOpen = true
                    onChatOpened()
                },
                containerColor = Color.White,
                contentColor = Color(0xFF16A34A)
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Group chat")
            }
        }

        // Offline quick signals — when there's no internet, riders can still
        // ping nearby phones over the mesh with one tap.
        if (uiState.isOffline) {
            QuickSignals(
                expanded = quickOpen,
                onToggle = { quickOpen = !quickOpen },
                onSend = { text ->
                    onSendChat(text)
                    quickOpen = false
                    val peers = uiState.chatMeshPeerCount
                    val msg = if (peers > 0) {
                        "Sent \"$text\" to $peers nearby"
                    } else {
                        "No nearby riders yet — \"$text\" will send when one connects"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, bottom = 24.dp)
            )
        }

        if (chatOpen) {
            ModalBottomSheet(
                onDismissRequest = { chatOpen = false },
                sheetState = sheetState
            ) {
                Text(
                    text = "Ride chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
                ChatPanel(
                    messages = uiState.chatMessages,
                    isOnline = uiState.chatOnline,
                    meshPeerCount = uiState.chatMeshPeerCount,
                    onSend = onSendChat,
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .imePadding()
                )
            }
        }
    }
}

@Composable
private fun CodeChip(code: String, onCopy: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onCopy)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(code, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF1E293B))
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = Color(0xFF64748B), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun MeshTestChip(enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (enabled) Color(0xFFF59E0B) else Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Filled.Campaign,
                contentDescription = null,
                tint = if (enabled) Color.White else Color(0xFF64748B),
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (enabled) "Mesh test ON (Wi-Fi still on)" else "Test mesh without Wi-Fi off",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else Color(0xFF64748B)
            )
        }
    }
}

@Composable
private fun DetailsChip(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$count", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF16A34A))
            Text("connected", fontSize = 12.sp, color = Color(0xFF64748B))
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ConnectionDetails(participants: List<Participant>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier.padding(top = 8.dp).widthIn(max = 320.dp)
    ) {
        if (participants.isEmpty()) {
            Text(
                "Waiting for others to join…",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.padding(8.dp).height(if (participants.size > 4) 200.dp else (participants.size * 44).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(participants, key = { it.peerId }) { p ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(28.dp).background(Color(0xFF64748B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B))
                            Text(
                                text = when (p.state) {
                                    ParticipantState.CONNECTING -> "connecting…"
                                    ParticipantState.CONNECTED -> "${p.roundTripTimeMs}ms · %.1f%% loss".format(p.packetLossPercent)
                                    ParticipantState.RECONNECTING -> "reconnecting…"
                                    ParticipantState.FAILED -> "failed"
                                },
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        if (p.state == ParticipantState.CONNECTED && p.relayed) {
                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFFDE68A)) {
                                Text("RELAY", fontSize = 9.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pre-typed signals riders can send with one tap when there's no internet. */
private val QUICK_SIGNALS = listOf("Stop", "Go", "Slow down", "Careful", "Wait", "All good")

@Composable
private fun QuickSignals(
    expanded: Boolean,
    onToggle: () -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val amber = Color(0xFFF59E0B)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_SIGNALS.forEach { text ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFEF3C7),
                        shadowElevation = 3.dp,
                        modifier = Modifier.clickable { onSend(text) }
                    ) {
                        Text(
                            text = text,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onToggle,
            containerColor = amber,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Campaign, contentDescription = null) },
            text = { Text(if (expanded) "Close" else "Quick signal") }
        )
    }
}

private fun copyCode(context: Context, code: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MiniConnect Code", code))
}

@Composable
private fun CallFailed(message: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        com.surya.miniconnect.ui.components.RideMapBackdrop()
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.Center).padding(32.dp).widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.SearchOff,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Couldn't join",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E293B)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    message,
                    fontSize = 13.sp,
                    color = Color(0xFF64748B),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text("Back")
                }
            }
        }
    }
}
