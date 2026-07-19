package com.surya.miniconnect.data.mesh

import android.util.Log
import com.surya.miniconnect.domain.model.MeshMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

/**
 * Radio-agnostic messaging engine over a [MeshTransport].
 *
 * Responsibilities:
 *  - Serialize [MeshMessage] to/from JSON bytes.
 *  - Dedup: a seen-id set drops messages we've already handled.
 *  - Multi-hop relay: new messages are re-broadcast to our other peers
 *    (with hops decremented) so they reach riders not directly connected.
 *  - Offline queue: outbound messages sent with no peers are queued and
 *    flushed on the next PeerConnected.
 */
class MeshMessenger(
    private val transport: MeshTransport,
    private val scope: CoroutineScope
) {

    private val _incoming = MutableSharedFlow<MeshMessage>(extraBufferCapacity = 64)
    /** Messages received (or relayed) from the mesh, deduped. */
    val incoming: SharedFlow<MeshMessage> = _incoming.asSharedFlow()

    /** Peers currently connected directly to this device. */
    val peers: StateFlow<Set<MeshPeer>> = transport.connectedPeers

    private val seenIds = LinkedHashSet<String>()
    private val outboundQueue = ArrayDeque<MeshMessage>()
    private var collectJob: Job? = null

    fun start(groupCode: String, myName: String) {
        transport.start(groupCode, myName)
        collectJob?.cancel()
        collectJob = scope.launch {
            transport.events.collect { event ->
                when (event) {
                    is MeshEvent.PayloadReceived -> handlePayload(event.fromPeerId, event.bytes)
                    is MeshEvent.PeerConnected -> flushQueue()
                    is MeshEvent.PeerLost -> Unit
                }
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        transport.stop()
        synchronized(this) {
            seenIds.clear()
            outboundQueue.clear()
        }
    }

    /**
     * Broadcasts [message] to all connected mesh peers, or queues it when no
     * peer is in range. Marks the id as seen so a relayed echo isn't re-shown.
     */
    fun send(message: MeshMessage) {
        markSeen(message.id)
        if (transport.connectedPeers.value.isEmpty()) {
            synchronized(this) { outboundQueue.addLast(message) }
            Log.d(TAG, "No peers; queued ${message.id} (queue=${outboundQueue.size})")
            return
        }
        transport.send(toBytes(message))
    }

    private fun handlePayload(fromPeerId: String, bytes: ByteArray) {
        val message = fromBytes(bytes) ?: return
        if (!markSeen(message.id)) return // duplicate (relay echo)

        _incoming.tryEmit(message)

        // Store-and-forward relay: pass it on to everyone except the sender.
        if (message.hops > 0) {
            val relay = toBytes(message.copy(hops = message.hops - 1))
            transport.connectedPeers.value
                .filter { it.id != fromPeerId }
                .forEach { transport.send(relay, it.id) }
        }
    }

    private fun flushQueue() {
        val toFlush = synchronized(this) {
            val list = outboundQueue.toList()
            outboundQueue.clear()
            list
        }
        if (toFlush.isEmpty()) return
        Log.d(TAG, "Flushing ${toFlush.size} queued messages")
        toFlush.forEach { transport.send(toBytes(it)) }
    }

    /** Returns true if the id was new (and is now recorded). */
    private fun markSeen(id: String): Boolean = synchronized(this) {
        if (!seenIds.add(id)) return false
        // Bound memory: drop the oldest ids once the set grows large.
        while (seenIds.size > MAX_SEEN_IDS) {
            seenIds.remove(seenIds.first())
        }
        true
    }

    companion object {
        private const val TAG = "MeshMessenger"
        private const val MAX_SEEN_IDS = 2000

        fun toBytes(message: MeshMessage): ByteArray = JSONObject().apply {
            put("id", message.id)
            put("senderId", message.senderId)
            put("senderName", message.senderName)
            put("content", message.content)
            put("timestamp", message.timestamp)
            put("hops", message.hops)
        }.toString().toByteArray(Charsets.UTF_8)

        fun fromBytes(bytes: ByteArray): MeshMessage? = try {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            MeshMessage(
                id = json.getString("id"),
                senderId = json.getString("senderId"),
                senderName = json.getString("senderName"),
                content = json.getString("content"),
                timestamp = json.getLong("timestamp"),
                hops = json.optInt("hops", 0)
            )
        } catch (e: JSONException) {
            Log.w(TAG, "Dropping malformed payload", e)
            null
        }
    }
}
