package com.surya.miniconnect.domain.model

/** Delivery status of a group chat message (v1: local send lifecycle only). */
enum class MeshMessageStatus {
    SENDING,
    SENT
}

/**
 * A group chat message carried over the internet path (LiveKit data channel)
 * or the offline mesh. The same [id] is used on both paths so duplicates are
 * collapsed when a message arrives twice.
 *
 * @param hops Remaining relay hops when travelling over the mesh; decremented
 * on each re-broadcast so messages don't circulate forever.
 */
data class MeshMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val status: MeshMessageStatus = MeshMessageStatus.SENT,
    val hops: Int = DEFAULT_HOPS,
    val isFromMe: Boolean = false
) {
    companion object {
        const val DEFAULT_HOPS = 5
    }
}
