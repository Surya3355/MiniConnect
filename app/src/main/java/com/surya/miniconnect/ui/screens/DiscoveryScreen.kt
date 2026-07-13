package com.surya.miniconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
 * @param uiState current UI state
 * @param onDiscover invoked when user requests discovery
 * @param onRefresh invoked when user requests a refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    uiState: DiscoveryUiState,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
    onPeerClick: (com.surya.miniconnect.domain.model.Peer) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "MiniConnect") },
                modifier = Modifier
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        when (uiState) {
            is DiscoveryUiState.Idle -> {
                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Button(onClick = onDiscover, modifier = Modifier.padding(16.dp)) {
                        Text("Discover")
                    }
                }
            }
            is DiscoveryUiState.Loading -> {
                LoadingView(modifier = Modifier.padding(innerPadding))
            }
            is DiscoveryUiState.Empty -> {
                EmptyState(
                    message = "No devices found",
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is DiscoveryUiState.Error -> {
                EmptyState(
                    message = uiState.message,
                    onRefresh = onRefresh,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            is DiscoveryUiState.Success -> {
                LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(innerPadding)) {
                    items(uiState.peers) { peer: Peer ->
                        PeerCard(peer = peer, onClick = { onPeerClick(peer) })
                    }
                }
            }
        }
    }
}


