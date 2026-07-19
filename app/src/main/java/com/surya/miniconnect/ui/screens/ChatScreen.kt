package com.surya.miniconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surya.miniconnect.domain.model.ConnectionState
import com.surya.miniconnect.presentation.chat.ChatUiState
import com.surya.miniconnect.ui.components.ChatInput
import com.surya.miniconnect.ui.components.MessageBubble
import com.surya.miniconnect.ui.components.TransferProgressBar

/**
 * Stateless Chat screen composable.
 *
 * @param uiState Current chat UI state.
 * @param onSendMessage Called when the user sends a text message.
 * @param onAttach Called when the user taps the attachment button.
 * @param onBack Called when the user taps back (navigates without disconnecting).
 * @param onDisconnect Called when the user explicitly disconnects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onSendMessage: (String) -> Unit,
    onAttach: () -> Unit,
    onBack: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isConnected = uiState.connectionState is ConnectionState.Connected
    val listState = rememberLazyListState()
    var showDisconnectDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect") },
            text = { Text("Are you sure you want to disconnect from this device?") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnectDialog = false
                    onDisconnect()
                }) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MiniConnect")
                        Text(
                            text = connectionLabel(uiState.connectionState),
                            style = MaterialTheme.typography.labelSmall,
                            color = connectionColor(uiState.connectionState)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isConnected) {
                        IconButton(onClick = { showDisconnectDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = "Disconnect",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            TransferProgressBar(
                transferState = uiState.transferState
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }
            }

            HorizontalDivider()

            ChatInput(
                onSend = onSendMessage,
                onAttach = onAttach,
                enabled = isConnected
            )
        }
    }
}

@Composable
private fun connectionColor(state: ConnectionState) = when (state) {
    is ConnectionState.Connected -> MaterialTheme.colorScheme.primary
    is ConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
    is ConnectionState.Failed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun connectionLabel(state: ConnectionState): String = when (state) {
    is ConnectionState.Connected -> "Connected"
    is ConnectionState.Connecting -> "Connecting…"
    is ConnectionState.Disconnected -> "Disconnected"
    is ConnectionState.Failed -> "Connection failed"
    is ConnectionState.Idle -> "Not connected"
}
