package com.surya.miniconnect.presentation.chat

import com.surya.miniconnect.domain.model.ChatMessage
import com.surya.miniconnect.domain.model.ConnectionState
import com.surya.miniconnect.domain.model.TransferState

/**
 * UI state for the chat screen.
 *
 * @property connectionState Current connection state displayed in the header.
 * @property messages All messages exchanged in the current session.
 * @property transferState Current file transfer state for the progress indicator.
 */
data class ChatUiState(
    val connectionState: ConnectionState = ConnectionState.Idle,
    val messages: List<ChatMessage> = emptyList(),
    val transferState: TransferState = TransferState.Idle
)
