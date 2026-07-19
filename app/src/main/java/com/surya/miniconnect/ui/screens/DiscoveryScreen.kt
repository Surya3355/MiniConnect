package com.surya.miniconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surya.miniconnect.domain.model.Peer
import com.surya.miniconnect.presentation.discovery.DiscoveryUiState
import com.surya.miniconnect.ui.components.EmptyState
import com.surya.miniconnect.ui.components.LoadingView
import com.surya.miniconnect.ui.components.PeerCard

/**
 * Stateless Discovery screen.
 *
 * @param uiState Current UI state.
 * @param isConnected Whether a Wi-Fi Direct connection is currently active.
 * @param connectingToAddress Device address being connected to, or null.
 * @param connectionError Error message from a failed connection attempt, or null.
 * @param onDiscover Invoked when user requests discovery.
 * @param onRefresh Invoked when user requests a refresh.
 * @param onPeerClick Invoked when user taps a peer to connect.
 * @param onResumeChat Invoked when user taps the resume-chat banner.
 * @param onErrorDismissed Invoked when the error snackbar is dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    uiState: DiscoveryUiState,
    isConnected: Boolean,
    connectingToAddress: String?,
    connectionError: String?,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onPeerClick: (Peer) -> Unit,
    onResumeChat: () -> Unit,
    onErrorDismissed: () -> Unit,
    onVoiceCall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(connectionError) {
        if (connectionError != null) {
            snackbarHostState.showSnackbar(connectionError)
            onErrorDismissed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "MiniConnect") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onVoiceCall,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Call, contentDescription = "Voice Call")
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (isConnected) {
                Card(
                    onClick = onResumeChat,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Active Connection",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap to return to chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            when (uiState) {
                is DiscoveryUiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(onClick = onDiscover, modifier = Modifier.padding(16.dp)) {
                            Text("Discover Devices")
                        }
                    }
                }
                is DiscoveryUiState.Loading -> {
                    LoadingView()
                }
                is DiscoveryUiState.Empty -> {
                    EmptyState(
                        message = "No devices found",
                        onRefresh = onRefresh
                    )
                }
                is DiscoveryUiState.Error -> {
                    EmptyState(
                        message = uiState.message,
                        onRefresh = onRefresh
                    )
                }
                is DiscoveryUiState.Success -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(uiState.peers) { peer: Peer ->
                            PeerCard(
                                peer = peer,
                                isConnecting = connectingToAddress == peer.address,
                                onClick = { onPeerClick(peer) }
                            )
                        }
                    }
                }
            }
        }
    }
}
