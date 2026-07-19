package com.surya.miniconnect.data.mesh

import android.content.Context
import android.util.Log
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [MeshTransport] backed by Google Nearby Connections (P2P_CLUSTER).
 *
 * Fully hands-free: every device both advertises and discovers with the same
 * service id, using an endpoint name of "groupCode|displayName". A connection
 * is only requested when the remote's group code matches ours, and incoming
 * connections are auto-accepted (the shared group code is the trust signal —
 * riders on the same ride). No dialogs or pairing prompts.
 */
class NearbyMeshTransport(context: Context) : MeshTransport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)

    private val _connectedPeers = MutableStateFlow<Set<MeshPeer>>(emptySet())
    override val connectedPeers: StateFlow<Set<MeshPeer>> = _connectedPeers.asStateFlow()

    private val _events = MutableSharedFlow<MeshEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<MeshEvent> = _events.asSharedFlow()

    private var groupCode: String = ""
    private var myEndpointName: String = ""
    private var started = false

    /** Names of endpoints we've initiated or accepted, keyed by endpoint id. */
    private val pendingNames = mutableMapOf<String, String>()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            _events.tryEmit(MeshEvent.PayloadReceived(endpointId, bytes))
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Byte payloads arrive whole; nothing to track.
        }
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val (code, name) = parseEndpointName(info.endpointName)
            if (code != groupCode) {
                Log.d(TAG, "Rejecting $endpointId: group mismatch")
                client.rejectConnection(endpointId)
                return
            }
            // Hands-free: matching group code is the authorization.
            pendingNames[endpointId] = name
            client.acceptConnection(endpointId, payloadCallback)
            Log.d(TAG, "Auto-accepting $endpointId ($name)")
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    val peer = MeshPeer(endpointId, pendingNames[endpointId] ?: "Rider")
                    _connectedPeers.value = _connectedPeers.value + peer
                    _events.tryEmit(MeshEvent.PeerConnected(peer))
                    Log.d(TAG, "Connected to ${peer.name} ($endpointId), peers=${_connectedPeers.value.size}")
                }
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    Log.d(TAG, "Already connected to $endpointId")
                }
                else -> {
                    pendingNames.remove(endpointId)
                    Log.w(TAG, "Connection to $endpointId failed: ${result.status.statusCode}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            pendingNames.remove(endpointId)
            _connectedPeers.value = _connectedPeers.value.filterNot { it.id == endpointId }.toSet()
            _events.tryEmit(MeshEvent.PeerLost(endpointId))
            Log.d(TAG, "Disconnected from $endpointId, peers=${_connectedPeers.value.size}")
            // Nearby still "knows" the endpoint after a connection drop, so
            // onEndpointFound won't re-fire. Restart discovery to force a fresh
            // detection → reconnect.
            if (started) restartDiscovery()
        }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val (code, name) = parseEndpointName(info.endpointName)
            if (code != groupCode) return
            if (_connectedPeers.value.any { it.id == endpointId }) return
            // Both sides discover each other; only the lexicographically-smaller
            // name initiates so the request isn't duplicated from both ends.
            if (myEndpointName >= info.endpointName) return

            Log.d(TAG, "Found $name ($endpointId), requesting connection")
            pendingNames[endpointId] = name
            client.requestConnection(myEndpointName, endpointId, connectionCallback)
                .addOnFailureListener { e ->
                    Log.w(TAG, "requestConnection to $endpointId failed", e)
                }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Endpoint lost: $endpointId")
        }
    }

    override fun start(groupCode: String, myName: String) {
        if (started) stop()
        this.groupCode = groupCode
        // Endpoint names must be unique per device for the initiation tie-break;
        // a short random suffix avoids two riders with the same name deadlocking.
        this.myEndpointName = "$groupCode|$myName|${(1000..9999).random()}"
        started = true

        val strategy = Strategy.P2P_CLUSTER
        client.startAdvertising(
            myEndpointName,
            SERVICE_ID,
            connectionCallback,
            AdvertisingOptions.Builder().setStrategy(strategy).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising as $myEndpointName")
        }.addOnFailureListener { e ->
            Log.e(TAG, "startAdvertising failed", e)
        }

        startDiscovery()
    }

    private fun startDiscovery() {
        client.startDiscovery(
            SERVICE_ID,
            discoveryCallback,
            DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
        ).addOnSuccessListener {
            Log.d(TAG, "Discovering on $SERVICE_ID")
        }.addOnFailureListener { e ->
            Log.e(TAG, "startDiscovery failed", e)
        }
    }

    /** Stop + start discovery so Nearby re-detects an endpoint after a drop. */
    private fun restartDiscovery() {
        client.stopDiscovery()
        startDiscovery()
    }

    override fun stop() {
        if (!started) return
        started = false
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        pendingNames.clear()
        _connectedPeers.value = emptySet()
        Log.d(TAG, "Stopped")
    }

    override fun send(bytes: ByteArray, toPeerId: String?) {
        val targets = if (toPeerId != null) {
            listOf(toPeerId)
        } else {
            _connectedPeers.value.map { it.id }
        }
        if (targets.isEmpty()) return
        client.sendPayload(targets, Payload.fromBytes(bytes))
            .addOnFailureListener { e -> Log.w(TAG, "sendPayload failed", e) }
    }

    /** Splits "groupCode|name|suffix" → (groupCode, name). */
    private fun parseEndpointName(endpointName: String): Pair<String, String> {
        val parts = endpointName.split('|')
        return when {
            parts.size >= 2 -> parts[0] to parts[1]
            else -> "" to endpointName
        }
    }

    companion object {
        private const val TAG = "NearbyMeshTransport"
        private const val SERVICE_ID = "com.surya.miniconnect.mesh"
    }
}
