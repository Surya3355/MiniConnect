package com.surya.miniconnect.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surya.miniconnect.data.wifi.WifiDirectManager
import com.surya.miniconnect.domain.model.DiscoveryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel that drives the Discovery screen. It bridges the WifiDirectManager
 * to a Compose-friendly [DiscoveryUiState].
 *
 * @param manager WifiDirectManager instance provided by the Activity.
 */
class DiscoveryViewModel(
    private val manager: WifiDirectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Idle)
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    init {
        // Combine peers and discoveryState into a single UI state
        combine(
            manager.peers,
            manager.discoveryState
        ) { peers, discoveryState ->
            when (discoveryState) {
                is DiscoveryState.Discovering -> DiscoveryUiState.Loading
                is DiscoveryState.Error -> DiscoveryUiState.Error(discoveryState.message)
                is DiscoveryState.Success -> if (peers.isEmpty()) DiscoveryUiState.Empty else DiscoveryUiState.Success(peers)
                is DiscoveryState.Empty -> DiscoveryUiState.Empty
                is DiscoveryState.Idle -> if (peers.isEmpty()) DiscoveryUiState.Idle else DiscoveryUiState.Success(peers)
            }
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
    }

    /**
     * Start discovery. Results (loading, success, empty, error) are propagated to [uiState].
     */
    fun discoverPeers() {
        viewModelScope.launch {
            _uiState.value = DiscoveryUiState.Loading
            val result = manager.discoverPeers()
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Failed to start discovery"
                _uiState.value = DiscoveryUiState.Error(msg)
            }
            // On success, manager will update peers through broadcasts which are observed above.
        }
    }

    /**
     * Convenience for user-triggered refresh.
     */
    fun refresh() {
        discoverPeers()
    }
}


