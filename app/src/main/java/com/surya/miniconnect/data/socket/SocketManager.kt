package com.surya.miniconnect.data.socket

import android.util.Log
import com.surya.miniconnect.domain.model.ChatMessage
import com.surya.miniconnect.domain.model.MessageType
import com.surya.miniconnect.domain.model.TransferState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/**
 * Manages TCP socket communication between two Wi-Fi Direct peers.
 *
 * The group owner acts as the server, and the client connects to it.
 * Messages are framed using a type-length protocol:
 *   - Type byte: 0x01 = text message, 0x02 = file header, 0x03 = file chunk
 *   - Followed by type-specific payload
 */
class SocketManager {

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    /** Observable transfer state for file operations. */
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
    /** Flow of messages received from the remote peer (text and file notifications). */
    val incomingMessages: SharedFlow<ChatMessage> = _incomingMessages.asSharedFlow()

    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var dataOutput: DataOutputStream? = null
    private var dataInput: DataInputStream? = null

    @Volatile
    private var connected = false

    /**
     * Starts a TCP server socket and waits for a client connection.
     * Call this on the group owner side.
     */
    suspend fun startServer(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(PORT).apply {
                reuseAddress = true
            }
            Log.d(TAG, "Server listening on port $PORT")
            val socket = serverSocket!!.accept()
            setupStreams(socket)
            Log.d(TAG, "Client connected")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Server error", e)
            Result.failure(e)
        }
    }

    /**
     * Connects to a TCP server at the given [hostAddress].
     * Call this on the client (non-group-owner) side.
     */
    suspend fun connect(hostAddress: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            socket.connect(InetSocketAddress(hostAddress, PORT), CONNECT_TIMEOUT_MS)
            setupStreams(socket)
            Log.d(TAG, "Connected to server at $hostAddress")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Client connect error", e)
            Result.failure(e)
        }
    }

    private fun setupStreams(socket: Socket) {
        clientSocket = socket
        dataOutput = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        dataInput = DataInputStream(BufferedInputStream(socket.getInputStream()))
        connected = true
    }

    /**
     * Sends a text message to the connected peer.
     */
    suspend fun sendMessage(text: String): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val output = dataOutput
        if (!connected || output == null) {
            return@withContext Result.failure(IllegalStateException("Not connected"))
        }
        try {
            val bytes = text.toByteArray(Charsets.UTF_8)
            synchronized(output) {
                output.writeByte(TYPE_MESSAGE.toInt())
                output.writeInt(bytes.size)
                output.write(bytes)
                output.flush()
            }
            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = text,
                isFromMe = true,
                timestamp = System.currentTimeMillis()
            )
            Result.success(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Send message error", e)
            Result.failure(e)
        }
    }

    /**
     * Sends a file to the connected peer. Returns a [ChatMessage] of type FILE on success
     * so the caller can add it to the chat history.
     *
     * @param file The file to send.
     */
    suspend fun sendFile(file: File): Result<ChatMessage> = withContext(Dispatchers.IO) {
        val output = dataOutput
        if (!connected || output == null) {
            return@withContext Result.failure(IllegalStateException("Not connected"))
        }
        try {
            val fileName = file.name
            val fileSize = file.length()
            val nameBytes = fileName.toByteArray(Charsets.UTF_8)

            _transferState.value = TransferState.InProgress(fileName, 0, fileSize)

            synchronized(output) {
                output.writeByte(TYPE_FILE_HEADER.toInt())
                output.writeInt(nameBytes.size)
                output.write(nameBytes)
                output.writeLong(fileSize)
                output.flush()
            }

            FileInputStream(file).use { fis ->
                val buffer = ByteArray(BUFFER_SIZE)
                var totalSent = 0L
                var bytesRead: Int

                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    synchronized(output) {
                        output.writeByte(TYPE_FILE_CHUNK.toInt())
                        output.writeInt(bytesRead)
                        output.write(buffer, 0, bytesRead)
                        output.flush()
                    }
                    totalSent += bytesRead
                    _transferState.value = TransferState.InProgress(fileName, totalSent, fileSize)
                }
            }

            _transferState.value = TransferState.Success(fileName)

            val message = ChatMessage(
                id = UUID.randomUUID().toString(),
                content = "Sent file: $fileName",
                isFromMe = true,
                timestamp = System.currentTimeMillis(),
                type = MessageType.FILE,
                fileName = fileName,
                filePath = file.absolutePath
            )
            Result.success(message)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.e(TAG, "Send file error", e)
            _transferState.value = TransferState.Failed(e.message ?: "File transfer failed")
            Result.failure(e)
        }
    }

    /**
     * Continuously reads incoming data from the socket.
     * Call this in a long-lived coroutine after connection is established.
     *
     * @param downloadDir Directory where received files will be saved.
     */
    suspend fun receiveLoop(downloadDir: File) = withContext(Dispatchers.IO) {
        val input = dataInput ?: return@withContext
        var pendingFileName: String? = null
        var pendingFileSize = 0L
        var pendingBytesReceived = 0L
        var pendingFileStream: FileOutputStream? = null
        var pendingTargetFile: File? = null

        try {
            while (connected) {
                val type = input.readByte()
                when (type) {
                    TYPE_MESSAGE -> {
                        val length = input.readInt()
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        val text = String(bytes, Charsets.UTF_8)
                        val message = ChatMessage(
                            id = UUID.randomUUID().toString(),
                            content = text,
                            isFromMe = false,
                            timestamp = System.currentTimeMillis()
                        )
                        _incomingMessages.emit(message)
                    }
                    TYPE_FILE_HEADER -> {
                        val nameLength = input.readInt()
                        val nameBytes = ByteArray(nameLength)
                        input.readFully(nameBytes)
                        pendingFileName = String(nameBytes, Charsets.UTF_8)
                        pendingFileSize = input.readLong()
                        pendingBytesReceived = 0L

                        downloadDir.mkdirs()
                        val targetFile = File(downloadDir, pendingFileName!!)
                        pendingTargetFile = targetFile
                        pendingFileStream = FileOutputStream(targetFile)
                        _transferState.value = TransferState.InProgress(pendingFileName!!, 0, pendingFileSize)
                    }
                    TYPE_FILE_CHUNK -> {
                        val chunkSize = input.readInt()
                        val chunkBytes = ByteArray(chunkSize)
                        input.readFully(chunkBytes)

                        pendingFileStream?.write(chunkBytes)
                        pendingBytesReceived += chunkSize

                        _transferState.value = TransferState.InProgress(
                            pendingFileName ?: "unknown",
                            pendingBytesReceived,
                            pendingFileSize
                        )

                        if (pendingBytesReceived >= pendingFileSize) {
                            pendingFileStream?.close()
                            pendingFileStream = null
                            _transferState.value = TransferState.Success(pendingFileName ?: "unknown")

                            val fileMessage = ChatMessage(
                                id = UUID.randomUUID().toString(),
                                content = "Received file: ${pendingFileName ?: "unknown"}",
                                isFromMe = false,
                                timestamp = System.currentTimeMillis(),
                                type = MessageType.FILE,
                                fileName = pendingFileName,
                                filePath = pendingTargetFile?.absolutePath
                            )
                            _incomingMessages.emit(fileMessage)

                            pendingFileName = null
                            pendingTargetFile = null
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (connected) {
                Log.e(TAG, "Receive loop error", e)
            }
        } finally {
            pendingFileStream?.close()
        }
    }

    /**
     * Disconnects and releases all socket resources.
     */
    suspend fun disconnect() = withContext(Dispatchers.IO) {
        connected = false
        try { dataOutput?.close() } catch (_: IOException) {}
        try { dataInput?.close() } catch (_: IOException) {}
        try { clientSocket?.close() } catch (_: IOException) {}
        try { serverSocket?.close() } catch (_: IOException) {}
        dataOutput = null
        dataInput = null
        clientSocket = null
        serverSocket = null
        _transferState.value = TransferState.Idle
        Log.d(TAG, "Disconnected")
    }

    /** Returns whether the socket is currently connected. */
    fun isConnected(): Boolean = connected

    companion object {
        private const val TAG = "SocketManager"
        private const val PORT = 8988
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val BUFFER_SIZE = 8192
        private const val TYPE_MESSAGE: Byte = 0x01
        private const val TYPE_FILE_HEADER: Byte = 0x02
        private const val TYPE_FILE_CHUNK: Byte = 0x03
    }
}
