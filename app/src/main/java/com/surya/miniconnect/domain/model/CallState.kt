package com.surya.miniconnect.domain.model

/** Overall state of the group voice room (not per-participant). */
sealed interface CallState {
    data object Idle : CallState
    data object Connecting : CallState
    /** Successfully in the room. Participants may be empty (waiting) or populated. */
    data object InRoom : CallState
    data class Failed(val message: String) : CallState
    data object Ended : CallState
}
