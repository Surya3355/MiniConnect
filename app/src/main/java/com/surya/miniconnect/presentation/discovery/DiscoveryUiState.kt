package com.surya.miniconnect.presentation.discovery

import com.surya.miniconnect.domain.model.Peer

/**
 * UI state for the discovery screen. This is a sealed hierarchy representing
 * the mutually exclusive UI states the Compose screen should render.
 */
sealed interface DiscoveryUiState {
    data object Idle : DiscoveryUiState
    data object Loading : DiscoveryUiState
    data class Success(val peers: List<Peer>) : DiscoveryUiState
    data object Empty : DiscoveryUiState
    data class Error(val message: String) : DiscoveryUiState
}
