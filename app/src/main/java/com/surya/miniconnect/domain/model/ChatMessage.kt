package com.surya.miniconnect.domain.model

/**
 * Represents a single chat message exchanged between connected peers.
 *
 * @property id Unique identifier for the message.
 * @property content The text content of the message, or a description for file messages.
 * @property isFromMe True if this device sent the message.
 * @property timestamp Epoch millis when the message was created.
 * @property type Whether this is a text message or a file transfer notification.
 * @property fileName Original file name, present only for [MessageType.FILE] messages.
 * @property filePath Local path to the received file, present only on the receiving side.
 */
data class ChatMessage(
    val id: String,
    val content: String,
    val isFromMe: Boolean,
    val timestamp: Long,
    val type: MessageType = MessageType.TEXT,
    val fileName: String? = null,
    val filePath: String? = null
)

/** Discriminates between text chat and file transfer notifications. */
enum class MessageType {
    TEXT,
    FILE
}
