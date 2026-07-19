package com.surya.miniconnect.presentation.discovery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surya.miniconnect.data.wifi.WifiDirectManager
import com.surya.miniconnect.domain.model.ConnectionState
import com.surya.miniconnect.domain.model.DiscoveryState
import com.surya.miniconnect.domain.model.Peer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel that drives the Discovery screen. Bridges the WifiDirectManager
 * to a Compose-friendly [DiscoveryUiState].
 *
 * @param manager WifiDirectManager instance provided by the Activity.
 */
class DiscoveryViewModel(
    private val manager: WifiDirectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiscoveryUiState>(DiscoveryUiState.Idle)
    /** Observable discovery UI state. */
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    private val _navigateToChat = MutableStateFlow(false)
    /** Emits true when the UI should navigate to the chat screen. */
    val navigateToChat: StateFlow<Boolean> = _navigateToChat.asStateFlow()

    /** True when a Wi-Fi Direct connection is active (can resume chat). */
    val isConnected: StateFlow<Boolean> get() = _isConnected.asStateFlow()
    private val _isConnected = MutableStateFlow(false)

    private val _connectingToAddress = MutableStateFlow<String?>(null)
    /** The device address we are currently connecting to, or null. */
    val connectingToAddress: StateFlow<String?> = _connectingToAddress.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    /** A connection error message to display, or null. */
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private var lastKnownPeers: List<Peer> = emptyList()
    private var connectionTimeoutJob: Job? = null

    init {
        observeDiscoveryState()
        observeConnectionState()
    }

    private fun observeDiscoveryState() {
        combine(
            manager.peers,
            manager.discoveryState
        ) { peers, discoveryState ->
            if (peers.isNotEmpty()) {
                lastKnownPeers = peers
            }
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

    private fun observeConnectionState() {
        manager.connectionState
            .onEach { state ->
                _isConnected.value = state is ConnectionState.Connected

                when (state) {
                    is ConnectionState.Connecting -> {
                        _connectionError.value = null
                    }
                    is ConnectionState.Connected -> {
                        connectionTimeoutJob?.cancel()
                        _connectingToAddress.value = null
                        _connectionError.value = null
                        _navigateToChat.value = true
                    }
                    is ConnectionState.Failed -> {
                        _connectingToAddress.value = null
                        _connectionError.value = state.message
                    }
                    is ConnectionState.Disconnected -> {
                        _connectingToAddress.value = null
                        if (lastKnownPeers.isNotEmpty()) {
                            _uiState.value = DiscoveryUiState.Success(lastKnownPeers)
                        }
                    }
                    is ConnectionState.Idle -> {
                        _connectingToAddress.value = null
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Starts peer discovery. */
    fun discoverPeers() {
        viewModelScope.launch {
            _uiState.value = DiscoveryUiState.Loading
            val result = manager.discoverPeers()
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Failed to start discovery"
                _uiState.value = DiscoveryUiState.Error(msg)
            }
        }
    }

    /** Convenience for user-triggered refresh. */
    fun refresh() {
        discoverPeers()
    }

    /** Initiates a connection to the given [peer]. */
    fun connectToPeer(peer: Peer) {
        _connectingToAddress.value = peer.address
        _connectionError.value = null
        connectionTimeoutJob?.cancel()
        connectionTimeoutJob = viewModelScope.launch {
            delay(CONNECTION_TIMEOUT_MS)
            if (_connectingToAddress.value != null) {
                _connectingToAddress.value = null
                _connectionError.value = "Connection timed out. Try again."
            }
        }
        viewModelScope.launch {
            val result = manager.connect(peer)
            if (result.isFailure) {
                connectionTimeoutJob?.cancel()
                _connectingToAddress.value = null
            }
        }
    }

    /** Navigates to the chat screen (used when resuming an existing connection). */
    fun openChat() {
        _navigateToChat.value = true
    }

    /** Resets the navigation flag after the UI has consumed it. */
    fun onNavigatedToChat() {
        _navigateToChat.value = false
    }

    /** Clears the connection error after the UI has shown it. */
    fun clearConnectionError() {
        _connectionError.value = null
    }

    companion object {
        private const val CONNECTION_TIMEOUT_MS = 15_000L
    }
}
