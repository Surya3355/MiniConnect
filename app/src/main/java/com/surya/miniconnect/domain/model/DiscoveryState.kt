package com.surya.miniconnect.domain.model

/**
 * Represents the current discovery lifecycle/state.
 */
sealed interface DiscoveryState {
    object Idle : DiscoveryState
    object Discovering : DiscoveryState
    object Success : DiscoveryState
    object Empty : DiscoveryState
    data class Error(val message: String) : DiscoveryState
}

