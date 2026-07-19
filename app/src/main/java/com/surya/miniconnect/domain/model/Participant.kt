package com.surya.miniconnect.domain.model

enum class ParticipantState { CONNECTING, CONNECTED, RECONNECTING, FAILED }

/** A single remote peer in the group voice room. */
data class Participant(
    val peerId: String,
    val name: String,
    val state: ParticipantState = ParticipantState.CONNECTING,
    val quality: Int = 100,
    val roundTripTimeMs: Int = 0,
    val packetLossPercent: Double = 0.0,
    val relayed: Boolean = false,
    val isSpeaking: Boolean = false
)
