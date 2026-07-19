package com.surya.miniconnect.data.mesh

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A peer connected through the mesh transport.
 *
 * @param id Transport-specific endpoint id (used to address sends).
 * @param name Human-readable display name advertised by the peer.
 */
data class MeshPeer(
    val id: String,
    val name: String
)

/** Events emitted by a [MeshTransport]. */
sealed interface MeshEvent {
    data class PeerConnected(val peer: MeshPeer) : MeshEvent
    data class PeerLost(val peerId: String) : MeshEvent
    data class PayloadReceived(val fromPeerId: String, val bytes: ByteArray) : MeshEvent
}

/**
 * Radio-independent device-to-device transport for the offline mesh.
 *
 * Implementations (Nearby Connections today, BLE later for cross-platform)
 * handle discovery, connection, and raw byte exchange. Everything above this
 * interface (message framing, dedup, relay) is transport-agnostic.
 *
 * Connections must be fully hands-free: implementations advertise, discover,
 * and accept automatically — the shared [start] groupCode is the trust signal.
 */
interface MeshTransport {

    /** Peers currently connected directly to this device. */
    val connectedPeers: StateFlow<Set<MeshPeer>>

    /** Connection and payload events. */
    val events: SharedFlow<MeshEvent>

    /**
     * Starts advertising + discovering for peers in the same [groupCode].
     * Safe to call again after [stop].
     */
    fun start(groupCode: String, myName: String)

    /** Stops all advertising/discovery and disconnects from all peers. */
    fun stop()

    /**
     * Sends [bytes] to [toPeerId], or broadcasts to every directly-connected
     * peer when [toPeerId] is null.
     */
    fun send(bytes: ByteArray, toPeerId: String? = null)
}
