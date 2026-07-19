package com.surya.miniconnect.presentation.voice

import com.surya.miniconnect.domain.model.AudioRoute
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.domain.model.MeshMessage
import com.surya.miniconnect.domain.model.Participant
import com.surya.miniconnect.domain.model.ParticipantState
import com.surya.miniconnect.domain.model.VoiceMode

data class VoiceCallUiState(
    val callState: CallState = CallState.Idle,
    val roomCode: String = "",
    val userName: String = "",
    val participants: List<Participant> = emptyList(),
    val isMuted: Boolean = false,
    val audioRoute: AudioRoute = AudioRoute.EARPIECE,
    val availableRoutes: List<AudioRoute> = listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val voiceMode: VoiceMode = VoiceMode.CONTINUOUS,
    val isPushToTalkActive: Boolean = false,
    val isReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val isInterrupted: Boolean = false,
    val isSpeaking: Boolean = false,
    val callDurationSeconds: Long = 0,
    // In-call group chat (LiveKit data channel when online, Nearby mesh offline).
    val chatMessages: List<MeshMessage> = emptyList(),
    val chatOnline: Boolean = false,
    val chatMeshPeerCount: Int = 0,
    val chatUnread: Int = 0,
    // True when the device has no validated internet — surfaces the offline
    // quick-signals button so riders can still ping nearby phones over the mesh.
    val isOffline: Boolean = false,
    // Debug: force mesh-only routing while real internet stays on, so the
    // offline mesh can be tested without toggling Wi-Fi.
    val meshTestMode: Boolean = false
) {
    /** Worst quality among connected participants (drives the overall badge). */
    val overallQuality: Int
        get() = participants.filter { it.quality > 0 }.minOfOrNull { it.quality } ?: 100

    val connectedCount: Int
        get() = participants.count { it.state == ParticipantState.CONNECTED }

    /** True when the mic is actually transmitting right now. */
    val micLive: Boolean
        get() = callState is CallState.InRoom &&
            !isMuted && !isInterrupted &&
            (voiceMode == VoiceMode.CONTINUOUS || isPushToTalkActive)
}
