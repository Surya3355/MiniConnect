package com.surya.miniconnect.domain.model

/**
 * Represents the state of a file transfer operation.
 */
sealed interface TransferState {
    /** No transfer is in progress. */
    data object Idle : TransferState

    /** A file is currently being sent or received. */
    data class InProgress(
        val fileName: String,
        val bytesTransferred: Long,
        val totalBytes: Long
    ) : TransferState {
        /** Progress as a fraction between 0.0 and 1.0. */
        val progress: Float
            get() = if (totalBytes > 0) (bytesTransferred.toFloat() / totalBytes) else 0f
    }

    /** Transfer completed successfully. */
    data class Success(val fileName: String) : TransferState

    /** Transfer failed. */
    data class Failed(val message: String) : TransferState
}
