package com.surya.miniconnect.presentation.voice

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.surya.miniconnect.data.audio.AudioRouteController
import com.surya.miniconnect.data.chat.ChatRouter
import com.surya.miniconnect.data.livekit.LiveKitConfig
import com.surya.miniconnect.data.livekit.LiveKitManager
import com.surya.miniconnect.data.livekit.TokenService
import com.surya.miniconnect.data.mesh.MeshMessenger
import com.surya.miniconnect.data.mesh.NearbyMeshTransport
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.service.VoiceCallService
import com.surya.miniconnect.util.ConnectivityObserver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class VoiceCallViewModel(
    private val app: Application,
    private val liveKit: LiveKitManager,
    private val tokenService: TokenService
) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(VoiceCallUiState())
    val uiState: StateFlow<VoiceCallUiState> = _uiState.asStateFlow()

    private val audioRouteController = AudioRouteController(app)

    // In-call group chat: same code as the call, internet-first with mesh
    // fallback. Shares the call's LiveKitManager so it rides the same room.
    private val chatRouter = ChatRouter(
        liveKit = liveKit,
        messenger = MeshMessenger(NearbyMeshTransport(app), viewModelScope),
        connectivity = ConnectivityObserver(app),
        scope = viewModelScope
    )

    private var timerJob: Job? = null
    private var rejoinJob: Job? = null
    private var serviceRunning = false
    private var userEnded = false
    private var userMuted = false
    private var chatStarted = false
    /** Count of incoming (not-from-me) messages the user has already seen. */
    private var readIncoming = 0

    init {
        observeLiveKit()
        observeAudioRoute()
        observeChat()
    }

    private fun observeLiveKit() {
        liveKit.callState.onEach { state ->
            _uiState.update {
                // Once terminal (Failed set locally, or user ended), don't override.
                if (it.callState is CallState.Failed && state !is CallState.Idle) it
                else it.copy(callState = state)
            }
        }.launchIn(viewModelScope)

        liveKit.isReconnecting.onEach { reconnecting ->
            _uiState.update { it.copy(isReconnecting = reconnecting) }
        }.launchIn(viewModelScope)

        liveKit.participants.onEach { list ->
            _uiState.update { it.copy(participants = list) }
            if (list.isNotEmpty()) startTimer()
        }.launchIn(viewModelScope)

        liveKit.isMuted.onEach { muted ->
            _uiState.update { it.copy(isMuted = muted) }
        }.launchIn(viewModelScope)

        liveKit.isSpeaking.onEach { speaking ->
            _uiState.update { it.copy(isSpeaking = speaking) }
        }.launchIn(viewModelScope)

        liveKit.errors.onEach { message ->
            if (!userEnded) {
                stopService()
                _uiState.update { it.copy(callState = CallState.Failed(message), isReconnecting = false) }
            }
        }.launchIn(viewModelScope)

        // The room dropped unexpectedly (LiveKit's own retry gave up). Re-fetch a
        // token and reconnect — recovers across a full network loss / Wi-Fi↔data
        // switch, instead of silently ending the call.
        liveKit.needsRejoin.onEach { attemptRejoin() }.launchIn(viewModelScope)
    }

    private fun attemptRejoin() {
        if (userEnded || rejoinJob?.isActive == true) return
        val roomCode = _uiState.value.roomCode
        val userName = _uiState.value.userName
        if (roomCode.isEmpty()) return

        rejoinJob = viewModelScope.launch {
            _uiState.update { it.copy(isReconnecting = true) }
            var attempt = 0
            while (!userEnded && attempt < MAX_REJOIN_ATTEMPTS) {
                attempt++
                // Wait until the device actually has internet before trying.
                if (_uiState.value.isOffline) {
                    delay(REJOIN_BACKOFF_MS)
                    continue
                }
                // Rejoin with create=true so the token server doesn't 404 while the
                // room momentarily has no other participants.
                val result = tokenService.fetchToken(roomCode, userName, create = true)
                val token = result.getOrNull()
                if (token != null) {
                    try {
                        val e2eeKey = if (LiveKitConfig.E2EE_ENABLED) LiveKitConfig.e2eeKeyFor(roomCode) else null
                        liveKit.connect(LiveKitConfig.URL, token.token, e2eeKey)
                        return@launch // reconnected; state collectors take over
                    } catch (e: Exception) {
                        // fall through to backoff + retry
                    }
                }
                delay(REJOIN_BACKOFF_MS)
            }
            if (!userEnded) {
                stopService()
                _uiState.update {
                    it.copy(callState = CallState.Failed("Lost connection. Tap back and rejoin."), isReconnecting = false)
                }
            }
        }
    }

    private fun observeAudioRoute() {
        audioRouteController.currentRoute.onEach { route ->
            _uiState.update { it.copy(audioRoute = route) }
        }.launchIn(viewModelScope)

        audioRouteController.availableRoutes.onEach { routes ->
            _uiState.update { it.copy(availableRoutes = routes) }
        }.launchIn(viewModelScope)

        // A phone/VoIP call took (or released) audio focus: pause/resume our mic,
        // respecting the user's manual mute state on resume.
        audioRouteController.isInterrupted.onEach { interrupted ->
            _uiState.update { it.copy(isInterrupted = interrupted) }
            if (interrupted) {
                liveKit.setMicEnabled(false)
            } else {
                liveKit.setMicEnabled(!userMuted)
            }
        }.launchIn(viewModelScope)
    }

    private fun observeChat() {
        chatRouter.messages.onEach { list ->
            val incoming = list.count { !it.isFromMe }
            _uiState.update {
                it.copy(chatMessages = list, chatUnread = (incoming - readIncoming).coerceAtLeast(0))
            }
        }.launchIn(viewModelScope)

        chatRouter.internetPathAvailable.onEach { online ->
            _uiState.update { it.copy(chatOnline = online && !it.meshTestMode) }
        }.launchIn(viewModelScope)

        chatRouter.meshPeers.onEach { peers ->
            _uiState.update { it.copy(chatMeshPeerCount = peers.size) }
        }.launchIn(viewModelScope)

        chatRouter.isOnline.onEach { online ->
            _uiState.update { it.copy(isOffline = !online || it.meshTestMode) }
        }.launchIn(viewModelScope)
    }

    /** Debug: force the chat to use the mesh only, so it can be tested with
     *  Wi-Fi still on. Also surfaces the offline quick-signals UI. */
    fun toggleMeshTestMode() {
        val enabled = !_uiState.value.meshTestMode
        chatRouter.setMeshTestMode(enabled)
        val reallyOnline = chatRouter.isOnline.value
        _uiState.update {
            it.copy(
                meshTestMode = enabled,
                isOffline = enabled || !reallyOnline,
                chatOnline = !enabled && reallyOnline && liveKit.isInRoom
            )
        }
    }

    fun sendChat(text: String) {
        if (text.isBlank()) return
        chatRouter.send(text.trim())
    }

    /** Called when the chat sheet is opened — clears the unread badge. */
    fun markChatRead() {
        readIncoming = _uiState.value.chatMessages.count { !it.isFromMe }
        _uiState.update { it.copy(chatUnread = 0) }
    }

    fun joinRoom(roomCode: String, userName: String, create: Boolean) {
        userEnded = false
        userMuted = false
        _uiState.update {
            it.copy(roomCode = roomCode, userName = userName, callState = CallState.Connecting)
        }
        // Keep the process alive while connecting / sharing the code.
        startService()

        // Start chat on the same code — works over the mesh even if the voice
        // call never connects (offline ride).
        if (!chatStarted) {
            chatRouter.start(roomCode, userName)
            chatStarted = true
        }

        viewModelScope.launch {
            val result = tokenService.fetchToken(roomCode, userName, create)
            result.onSuccess { token ->
                try {
                    audioRouteController.start()
                    val e2eeKey = if (LiveKitConfig.E2EE_ENABLED) LiveKitConfig.e2eeKeyFor(roomCode) else null
                    liveKit.connect(LiveKitConfig.URL, token.token, e2eeKey)
                } catch (e: Exception) {
                    // Keep the foreground service alive if the mesh is running —
                    // it keeps the process (and Nearby) alive in the background.
                    if (!chatStarted) stopService()
                    _uiState.update {
                        it.copy(callState = CallState.Failed(e.message ?: "Couldn't join the call"))
                    }
                }
            }.onFailure { e ->
                // Offline: token fetch failed, but the mesh is live — keep the
                // service so Nearby isn't throttled when the app backgrounds.
                if (!chatStarted) stopService()
                _uiState.update {
                    it.copy(callState = CallState.Failed(e.message ?: "Couldn't join the call"))
                }
            }
        }
    }

    fun toggleMute() {
        userMuted = !userMuted
        liveKit.setMicEnabled(!userMuted)
    }

    /** Cycle the output through the currently available routes. */
    fun cycleAudioRoute() {
        val routes = _uiState.value.availableRoutes
        if (routes.isEmpty()) return
        val idx = routes.indexOf(_uiState.value.audioRoute).coerceAtLeast(0)
        audioRouteController.setRoute(routes[(idx + 1) % routes.size])
    }

    fun endCall() {
        userEnded = true
        rejoinJob?.cancel()
        rejoinJob = null
        liveKit.disconnect()
        audioRouteController.stop()
        chatRouter.stop()
        chatStarted = false
        readIncoming = 0
        stopTimer()
        stopService()
        _uiState.update {
            it.copy(
                callState = CallState.Ended,
                participants = emptyList(),
                isReconnecting = false,
                chatMessages = emptyList(),
                chatUnread = 0
            )
        }
    }

    private fun startService() {
        if (serviceRunning) return
        VoiceCallService.start(app, _uiState.value.roomCode.ifEmpty { "Group call" })
        serviceRunning = true
    }

    private fun stopService() {
        if (serviceRunning) {
            VoiceCallService.stop(app)
            serviceRunning = false
        }
    }

    private fun startTimer() {
        if (timerJob != null) return
        _uiState.update { it.copy(callDurationSeconds = 0) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        liveKit.release()
        audioRouteController.stop()
        chatRouter.stop()
    }

    private companion object {
        const val MAX_REJOIN_ATTEMPTS = 30
        const val REJOIN_BACKOFF_MS = 3000L
    }
}
