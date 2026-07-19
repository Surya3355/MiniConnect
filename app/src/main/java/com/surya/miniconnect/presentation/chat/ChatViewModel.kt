package com.surya.miniconnect.presentation.chat

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.surya.miniconnect.data.socket.SocketManager
import com.surya.miniconnect.data.wifi.WifiDirectManager
import com.surya.miniconnect.domain.model.ConnectionState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel that drives the Chat screen. Manages socket lifecycle,
 * message sending/receiving, and file transfer.
 *
 * @param application Application context for file storage paths.
 * @param wifiManager WifiDirectManager to observe connection state and disconnect.
 * @param socketManager SocketManager for TCP communication.
 */
class ChatViewModel(
    application: Application,
    private val wifiManager: WifiDirectManager,
    private val socketManager: SocketManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChatUiState())
    /** Observable chat UI state. */
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var receiveJob: Job? = null
    private var socketSetupJob: Job? = null

    private val downloadDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "MiniConnect"
        )

    init {
        observeConnectionState()
        observeIncomingMessages()
        observeTransferState()
    }

    private fun observeConnectionState() {
        wifiManager.connectionState
            .onEach { state ->
                _uiState.update { it.copy(connectionState = state) }

                if (state is ConnectionState.Connected) {
                    setupSocket(state)
                }

                if (state is ConnectionState.Disconnected) {
                    tearDownSocket()
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeIncomingMessages() {
        socketManager.incomingMessages
            .onEach { message ->
                _uiState.update { current ->
                    current.copy(messages = current.messages + message)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeTransferState() {
        socketManager.transferState
            .onEach { state ->
                _uiState.update { it.copy(transferState = state) }
            }
            .launchIn(viewModelScope)
    }

    private fun setupSocket(connState: ConnectionState.Connected) {
        socketSetupJob?.cancel()
        socketSetupJob = viewModelScope.launch {
            if (connState.isGroupOwner) {
                socketManager.startServer()
            } else {
                socketManager.connect(connState.groupOwnerAddress)
            }
            startReceiving()
        }
    }

    private fun startReceiving() {
        receiveJob?.cancel()
        receiveJob = viewModelScope.launch {
            socketManager.receiveLoop(downloadDir)
        }
    }

    private fun tearDownSocket() {
        receiveJob?.cancel()
        socketSetupJob?.cancel()
        viewModelScope.launch {
            socketManager.disconnect()
        }
    }

    /**
     * Sends a text message to the connected peer.
     */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val result = socketManager.sendMessage(text.trim())
            result.onSuccess { message ->
                _uiState.update { current ->
                    current.copy(messages = current.messages + message)
                }
            }
        }
    }

    /**
     * Sends a file to the connected peer.
     */
    fun sendFile(file: File) {
        viewModelScope.launch {
            val result = socketManager.sendFile(file)
            result.onSuccess { message ->
                _uiState.update { current ->
                    current.copy(messages = current.messages + message)
                }
            }
        }
    }

    /**
     * Disconnects from the peer and cleans up socket resources.
     */
    fun disconnect() {
        viewModelScope.launch {
            socketManager.disconnect()
            wifiManager.disconnect()
        }
    }

    /** Clears chat history when starting a fresh session. */
    fun clearHistory() {
        _uiState.update { it.copy(messages = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        receiveJob?.cancel()
        socketSetupJob?.cancel()
    }
}
