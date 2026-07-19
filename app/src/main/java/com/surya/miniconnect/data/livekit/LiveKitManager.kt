package com.surya.miniconnect.data.livekit

import android.content.Context
import android.util.Log
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.domain.model.ParticipantState
import io.livekit.android.AudioOptions
import io.livekit.android.LiveKit
import io.livekit.android.LiveKitOverrides
import io.livekit.android.RoomOptions
import io.livekit.android.audio.NoAudioHandler
import io.livekit.android.e2ee.BaseKeyProvider
import io.livekit.android.e2ee.E2EEOptions
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.participant.ConnectionQuality
import io.livekit.android.room.participant.Participant
import io.livekit.android.util.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.surya.miniconnect.domain.model.Participant as DomainParticipant

/**
 * Wraps a LiveKit [Room] and exposes its state as domain flows the ViewModel/UI
 * already understand. Replaces the hand-written WebRTC mesh + signaling.
 *
 * - Group voice via the LiveKit SFU (scales past mesh).
 * - Auto-reconnect handled by the SDK.
 * - E2EE via SFrame with a shared per-room key (SFU can't hear the audio).
 * - Uses [NoAudioHandler] so our AudioRouteController owns audio routing.
 */
class LiveKitManager(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var room: Room? = null
    private var collectorJob: Job? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isReconnecting = MutableStateFlow(false)
    val isReconnecting: StateFlow<Boolean> = _isReconnecting.asStateFlow()

    private val _participants = MutableStateFlow<List<DomainParticipant>>(emptyList())
    val participants: StateFlow<List<DomainParticipant>> = _participants.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _dataReceived = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    /** Raw data-channel payloads from other participants (used for group chat). */
    val dataReceived: SharedFlow<ByteArray> = _dataReceived.asSharedFlow()

    private val _needsRejoin = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emitted when the room drops unexpectedly (not a user hang-up) — the
     *  ViewModel re-fetches a token and reconnects instead of ending the call. */
    val needsRejoin: SharedFlow<Unit> = _needsRejoin.asSharedFlow()

    /** True while connected to a room — the internet chat path is available. */
    val isInRoom: Boolean get() = room?.state == Room.State.CONNECTED

    /** Set when the local user hung up, so an unexpected DISCONNECT can be told apart. */
    private var userInitiatedDisconnect = false

    /** Create the room, connect, and publish the mic. E2EE if [e2eeKey] != null. Throws on failure. */
    suspend fun connect(url: String, token: String, e2eeKey: String?) {
        userInitiatedDisconnect = false
        release()

        val overrides = LiveKitOverrides(
            audioOptions = AudioOptions(audioHandler = NoAudioHandler())
        )
        val keyProvider: BaseKeyProvider? = if (e2eeKey != null) {
            // The frame-cryptor native symbols live in LiveKit's WebRTC .so, which
            // isn't loaded until LiveKit.create(). Force-load it first so creating
            // the key provider doesn't hit UnsatisfiedLinkError.
            try {
                System.loadLibrary("lkjingle_peerconnection_so")
            } catch (e: Throwable) {
                Log.w(TAG, "WebRTC native preload failed", e)
            }
            BaseKeyProvider()
        } else null
        val roomOptions = if (keyProvider != null) {
            RoomOptions(e2eeOptions = E2EEOptions(keyProvider = keyProvider))
        } else {
            RoomOptions()
        }
        val newRoom = LiveKit.create(appContext, options = roomOptions, overrides = overrides)
        if (keyProvider != null && e2eeKey != null) keyProvider.setSharedKey(e2eeKey)
        room = newRoom
        _callState.value = CallState.Connecting

        startCollectors(newRoom)

        newRoom.connect(url, token)
        newRoom.localParticipant.setMicrophoneEnabled(true)
        _isMuted.value = false
    }

    private fun startCollectors(r: Room) {
        collectorJob = scope.launch {
            launch {
                r::state.flow.collect { state ->
                    when (state) {
                        Room.State.CONNECTING -> {
                            _callState.value = CallState.Connecting
                            _isReconnecting.value = false
                        }
                        Room.State.CONNECTED -> {
                            _callState.value = CallState.InRoom
                            _isReconnecting.value = false
                        }
                        Room.State.RECONNECTING -> {
                            _callState.value = CallState.InRoom
                            _isReconnecting.value = true
                        }
                        Room.State.DISCONNECTED -> {
                            if (userInitiatedDisconnect) {
                                _isReconnecting.value = false
                                if (_callState.value is CallState.InRoom) _callState.value = CallState.Ended
                            } else if (_callState.value is CallState.InRoom) {
                                // Unexpected drop — LiveKit's own retry gave up. Stay
                                // "reconnecting" and let the ViewModel re-fetch a token
                                // and reconnect (recovers across full network loss).
                                _isReconnecting.value = true
                                _needsRejoin.tryEmit(Unit)
                            } else {
                                _isReconnecting.value = false
                            }
                        }
                    }
                }
            }
            launch {
                combine(r::remoteParticipants.flow, r::activeSpeakers.flow) { remotes, speakers ->
                    val speakingIds = speakers.mapNotNull { it.identity?.value }.toSet()
                    remotes.values.map { p -> p.toDomain(speakingIds.contains(p.identity?.value)) }
                }.collect { _participants.value = it }
            }
            launch {
                r::activeSpeakers.flow.collect { speakers ->
                    val localId = r.localParticipant.identity?.value
                    _isSpeaking.value = localId != null && speakers.any { it.identity?.value == localId }
                }
            }
            launch {
                r.events.collect { event ->
                    if (event is RoomEvent.DataReceived && event.topic == DATA_TOPIC_CHAT) {
                        _dataReceived.tryEmit(event.data)
                    }
                }
            }
        }
    }

    /** Publishes [bytes] to every participant over the reliable data channel. */
    fun sendData(bytes: ByteArray) {
        val r = room ?: return
        scope.launch {
            try {
                r.localParticipant.publishData(bytes, topic = DATA_TOPIC_CHAT)
            } catch (e: Exception) {
                Log.w(TAG, "publishData failed", e)
            }
        }
    }

    fun setMicEnabled(enabled: Boolean) {
        _isMuted.value = !enabled
        scope.launch { room?.localParticipant?.setMicrophoneEnabled(enabled) }
    }

    fun disconnect() {
        userInitiatedDisconnect = true
        room?.disconnect()
        _callState.value = CallState.Ended
    }

    fun release() {
        collectorJob?.cancel()
        collectorJob = null
        room?.disconnect()
        room?.release()
        room = null
        _participants.value = emptyList()
        _isMuted.value = false
        _isSpeaking.value = false
        _isReconnecting.value = false
    }

    private fun Participant.toDomain(speaking: Boolean): DomainParticipant {
        val id = identity?.value ?: sid?.value ?: "?"
        return DomainParticipant(
            peerId = id,
            name = name?.takeIf { it.isNotBlank() } ?: identity?.value ?: "Rider",
            state = ParticipantState.CONNECTED,
            quality = connectionQuality.toScore(),
            isSpeaking = speaking
        )
    }

    private fun ConnectionQuality.toScore(): Int = when (this) {
        ConnectionQuality.EXCELLENT -> 100
        ConnectionQuality.GOOD -> 70
        ConnectionQuality.POOR -> 30
        else -> 100
    }

    companion object {
        private const val TAG = "LiveKitManager"
        private const val DATA_TOPIC_CHAT = "mc-chat"
    }
}
