package com.surya.miniconnect.domain.model

/**
 * Represents the state of the Wi-Fi Direct connection lifecycle.
 */
sealed interface ConnectionState {
    /** No connection attempt has been made. */
    data object Idle : ConnectionState

    /** A connection attempt is in progress. */
    data object Connecting : ConnectionState

    /** Connected to a peer. [isGroupOwner] indicates if this device is the group owner. */
    data class Connected(
        val groupOwnerAddress: String,
        val isGroupOwner: Boolean
    ) : ConnectionState

    /** The connection attempt or existing connection failed. */
    data class Failed(val message: String) : ConnectionState

    /** The connection was intentionally closed. */
    data object Disconnected : ConnectionState
}
