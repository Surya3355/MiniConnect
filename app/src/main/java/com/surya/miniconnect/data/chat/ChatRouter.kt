package com.surya.miniconnect.data.chat

import android.util.Log
import com.surya.miniconnect.data.livekit.LiveKitManager
import com.surya.miniconnect.data.mesh.MeshMessenger
import com.surya.miniconnect.data.mesh.MeshPeer
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.domain.model.MeshMessage
import com.surya.miniconnect.domain.model.MeshMessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.surya.miniconnect.util.ConnectivityObserver
import java.util.UUID

/**
 * Single send/receive API for group chat, routing over two paths:
 *
 *  - Internet first: when online and connected to the LiveKit room, messages
 *    go over the room's reliable data channel (any distance, no extra server).
 *  - Mesh fallback: broadcast over the Nearby mesh when peers are in range —
 *    also alongside the internet path, covering a nearby rider who just lost
 *    internet.
 *  - Neither: queue locally, flush when either path comes back.
 *
 * Messages keep the same id on both paths; a seen-id set collapses duplicates
 * so a message arriving via internet AND mesh is shown once.
 */
class ChatRouter(
    private val liveKit: LiveKitManager,
    private val messenger: MeshMessenger,
    private val connectivity: ConnectivityObserver,
    private val scope: CoroutineScope
) {

    private val _messages = MutableStateFlow<List<MeshMessage>>(emptyList())
    /** Full chat history (own + received), oldest first. */
    val messages: StateFlow<List<MeshMessage>> = _messages.asStateFlow()

    /** Riders connected through the offline mesh. */
    val meshPeers: StateFlow<Set<MeshPeer>> = messenger.peers

    /** True when validated internet is available. */
    val isOnline: StateFlow<Boolean> = connectivity.isOnline

    /** True when the internet chat path (LiveKit room data channel) is usable. */
    val internetPathAvailable: Flow<Boolean> =
        combine(connectivity.isOnline, liveKit.callState) { online, call ->
            online && call is CallState.InRoom
        }

    private val myId = UUID.randomUUID().toString()
    private var myName: String = ""

    /** Debug: when true, chat is sent over the mesh only (internet path skipped),
     *  so the mesh can be tested with real Wi-Fi still on. */
    @Volatile private var meshTestMode: Boolean = false

    fun setMeshTestMode(enabled: Boolean) { meshTestMode = enabled }
    private val seenIds = LinkedHashSet<String>()
    private val pending = ArrayDeque<MeshMessage>()
    private var jobs: Job? = null

    fun start(groupCode: String, myName: String) {
        this.myName = myName
        connectivity.start()
        messenger.start(groupCode, myName)

        jobs?.cancel()
        jobs = scope.launch {
            // Incoming from the mesh.
            launch {
                messenger.incoming.collect { message -> onIncoming(message, Path.MESH) }
            }
            // Incoming from the LiveKit data channel.
            launch {
                liveKit.dataReceived.collect { bytes ->
                    MeshMessenger.fromBytes(bytes)?.let { onIncoming(it, Path.INTERNET) }
                }
            }
            // Flush the queue when either path becomes available.
            launch {
                combine(connectivity.isOnline, messenger.peers) { online, peers ->
                    (online && liveKit.isInRoom) || peers.isNotEmpty()
                }.collect { available ->
                    if (available) flushPending()
                }
            }
        }
    }

    fun stop() {
        jobs?.cancel()
        jobs = null
        messenger.stop()
        connectivity.stop()
        synchronized(this) {
            seenIds.clear()
            pending.clear()
        }
        _messages.value = emptyList()
    }

    /** Sends a chat message, choosing the best available path. */
    fun send(content: String) {
        val message = MeshMessage(
            id = UUID.randomUUID().toString(),
            senderId = myId,
            senderName = myName,
            content = content,
            timestamp = System.currentTimeMillis(),
            status = MeshMessageStatus.SENDING,
            isFromMe = true
        )
        markSeen(message.id)
        val delivered = route(message)
        _messages.value = _messages.value + message.copy(
            status = if (delivered) MeshMessageStatus.SENT else MeshMessageStatus.SENDING
        )
        if (!delivered) {
            synchronized(this) { pending.addLast(message) }
            Log.d(TAG, "No path available; queued ${message.id}")
        }
    }

    /** Returns true if the message left the device on at least one path. */
    private fun route(message: MeshMessage): Boolean {
        var delivered = false
        if (!meshTestMode && connectivity.isOnline.value && liveKit.isInRoom) {
            liveKit.sendData(MeshMessenger.toBytes(message))
            delivered = true
            Log.d(TAG, "Routed ${message.id} via internet")
        }
        if (messenger.peers.value.isNotEmpty()) {
            messenger.send(message)
            delivered = true
            Log.d(TAG, "Routed ${message.id} via mesh (${messenger.peers.value.size} peers)")
        }
        return delivered
    }

    private fun flushPending() {
        val toFlush = synchronized(this) {
            if (pending.isEmpty()) return
            val list = pending.toList()
            pending.clear()
            list
        }
        Log.d(TAG, "Flushing ${toFlush.size} pending messages")
        toFlush.forEach { message ->
            if (route(message)) {
                _messages.value = _messages.value.map {
                    if (it.id == message.id) it.copy(status = MeshMessageStatus.SENT) else it
                }
            } else {
                synchronized(this) { pending.addLast(message) }
            }
        }
    }

    /** Which transport a message arrived on. */
    private enum class Path { MESH, INTERNET }

    private fun onIncoming(message: MeshMessage, from: Path) {
        if (!markSeen(message.id)) return // already have it (dedup across both paths)
        if (message.senderId != myId) {
            _messages.value = _messages.value + message.copy(isFromMe = false)
        }
        bridge(message, from)
    }

    /**
     * Gateway relay: forward a message received on one transport onto the OTHER
     * transport, so a rider who only has internet and a nearby rider who only
     * has the mesh can still reach each other through a phone that has both.
     * Dedup-by-id (above) stops this from looping.
     */
    private fun bridge(message: MeshMessage, from: Path) {
        when (from) {
            Path.MESH -> if (connectivity.isOnline.value && liveKit.isInRoom) {
                liveKit.sendData(MeshMessenger.toBytes(message))
                Log.d(TAG, "Bridged ${message.id} mesh → internet")
            }
            Path.INTERNET -> if (messenger.peers.value.isNotEmpty()) {
                messenger.send(message)
                Log.d(TAG, "Bridged ${message.id} internet → mesh")
            }
        }
    }

    /** Returns true if the id was new (and is now recorded). */
    private fun markSeen(id: String): Boolean = synchronized(this) {
        if (!seenIds.add(id)) return false
        while (seenIds.size > MAX_SEEN_IDS) {
            seenIds.remove(seenIds.first())
        }
        true
    }

    companion object {
        private const val TAG = "ChatRouter"
        private const val MAX_SEEN_IDS = 2000
    }
}
