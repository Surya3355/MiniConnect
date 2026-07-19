package com.surya.miniconnect

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.surya.miniconnect.data.livekit.LiveKitManager
import com.surya.miniconnect.data.livekit.TokenService
import com.surya.miniconnect.data.socket.SocketManager
import com.surya.miniconnect.data.wifi.WifiDirectManager
import com.surya.miniconnect.domain.model.CallState
import com.surya.miniconnect.presentation.chat.ChatViewModel
import com.surya.miniconnect.presentation.discovery.DiscoveryViewModel
import com.surya.miniconnect.presentation.voice.VoiceCallViewModel
import com.surya.miniconnect.ui.screens.CallEntryScreen
import com.surya.miniconnect.ui.screens.ChatScreen
import com.surya.miniconnect.ui.screens.CreateCallScreen
import com.surya.miniconnect.ui.screens.DiscoveryScreen
import com.surya.miniconnect.ui.components.OngoingCallBar
import com.surya.miniconnect.ui.screens.JoinCallScreen
import com.surya.miniconnect.ui.screens.RideCallScreen
import com.surya.miniconnect.ui.theme.MiniConnectTheme
import com.surya.miniconnect.util.PermissionManager
import java.io.File
import java.io.FileOutputStream

private enum class Screen { HOME, DISCOVERY, CHAT, CREATE_CALL, JOIN_CALL, VOICE_CALL }

class MainActivity : ComponentActivity() {

    private val wifiManager = WifiDirectManager()
    private val socketManager = SocketManager()
    private val permissionManager = PermissionManager()
    private val tokenService = TokenService()
    private lateinit var liveKitManager: LiveKitManager

    private val discoveryViewModel: DiscoveryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoveryViewModel(wifiManager) as T
            }
        }
    }

    private val chatViewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(application, wifiManager, socketManager) as T
            }
        }
    }

    private val voiceCallViewModel: VoiceCallViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VoiceCallViewModel(application, liveKitManager, tokenService) as T
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            initializeWifiDirect()
        } else {
            Toast.makeText(this, "Permissions are required for MiniConnect", Toast.LENGTH_LONG).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { handlePickedFile(it) }
    }

    private var currentScreen by mutableStateOf(Screen.HOME)

    private fun initializeWifiDirect() {
        val result = wifiManager.initialize(applicationContext)
        if (result.isSuccess) {
            wifiManager.registerReceiver(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        liveKitManager = LiveKitManager(applicationContext)

        if (permissionManager.hasAllPermissions(this)) {
            initializeWifiDirect()
        } else {
            permissionLauncher.launch(permissionManager.requiredPermissions())
        }

        setContent {
            MiniConnectTheme {
                val showChat by discoveryViewModel.navigateToChat.collectAsStateWithLifecycle()
                val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
                val chatState by chatViewModel.uiState.collectAsStateWithLifecycle()
                val isConnected by discoveryViewModel.isConnected.collectAsStateWithLifecycle()
                val connectingTo by discoveryViewModel.connectingToAddress.collectAsStateWithLifecycle()
                val connectionError by discoveryViewModel.connectionError.collectAsStateWithLifecycle()
                val voiceState by voiceCallViewModel.uiState.collectAsStateWithLifecycle()

                if (showChat && currentScreen == Screen.DISCOVERY) {
                    currentScreen = Screen.CHAT
                }

                val callActive = voiceState.callState is CallState.InRoom ||
                    voiceState.callState is CallState.Connecting || voiceState.isReconnecting

                Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    Screen.HOME -> {
                        CallEntryScreen(
                            // If a call is already active, Create/Join take you back
                            // into it rather than starting a second call.
                            onCreateCall = {
                                currentScreen = if (callActive) Screen.VOICE_CALL else Screen.CREATE_CALL
                            },
                            onJoinCall = {
                                currentScreen = if (callActive) Screen.VOICE_CALL else Screen.JOIN_CALL
                            },
                            onDiscover = { currentScreen = Screen.DISCOVERY }
                        )
                    }
                    Screen.DISCOVERY -> {
                        BackHandler { currentScreen = Screen.HOME }
                        DiscoveryScreen(
                            uiState = discoveryState,
                            isConnected = isConnected,
                            connectingToAddress = connectingTo,
                            connectionError = connectionError,
                            onDiscover = { discoveryViewModel.discoverPeers() },
                            onRefresh = { discoveryViewModel.refresh() },
                            onPeerClick = { peer -> discoveryViewModel.connectToPeer(peer) },
                            onResumeChat = {
                                discoveryViewModel.openChat()
                                currentScreen = Screen.CHAT
                            },
                            onErrorDismissed = { discoveryViewModel.clearConnectionError() },
                            onVoiceCall = { currentScreen = Screen.HOME }
                        )
                    }
                    Screen.CHAT -> {
                        BackHandler {
                            discoveryViewModel.onNavigatedToChat()
                            currentScreen = Screen.DISCOVERY
                        }
                        ChatScreen(
                            uiState = chatState,
                            onSendMessage = { chatViewModel.sendMessage(it) },
                            onAttach = { filePickerLauncher.launch("*/*") },
                            onBack = {
                                discoveryViewModel.onNavigatedToChat()
                                currentScreen = Screen.DISCOVERY
                            },
                            onDisconnect = {
                                chatViewModel.disconnect()
                                discoveryViewModel.onNavigatedToChat()
                                currentScreen = Screen.DISCOVERY
                            }
                        )
                    }
                    Screen.CREATE_CALL -> {
                        BackHandler { currentScreen = Screen.HOME }
                        CreateCallScreen(
                            onStartCall = { roomCode, userName ->
                                voiceCallViewModel.joinRoom(roomCode, userName, create = true)
                                currentScreen = Screen.VOICE_CALL
                            },
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                    Screen.JOIN_CALL -> {
                        BackHandler { currentScreen = Screen.HOME }
                        JoinCallScreen(
                            onJoin = { roomCode, userName ->
                                voiceCallViewModel.joinRoom(roomCode, userName, create = false)
                                currentScreen = Screen.VOICE_CALL
                            },
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                    Screen.VOICE_CALL -> {
                        // Back keeps the call alive and returns to Home; an
                        // ongoing-call bar lets the user jump back into the call.
                        BackHandler { currentScreen = Screen.HOME }
                        RideCallScreen(
                            uiState = voiceState,
                            onToggleMute = { voiceCallViewModel.toggleMute() },
                            onCycleOutput = { voiceCallViewModel.cycleAudioRoute() },
                            onEndCall = {
                                voiceCallViewModel.endCall()
                                currentScreen = Screen.HOME
                            },
                            onBack = { currentScreen = Screen.HOME },
                            onSendChat = { voiceCallViewModel.sendChat(it) },
                            onChatOpened = { voiceCallViewModel.markChatRead() },
                            onToggleMeshTest = { voiceCallViewModel.toggleMeshTestMode() }
                        )
                    }
                }

                    if (callActive && currentScreen != Screen.VOICE_CALL) {
                        OngoingCallBar(
                            roomCode = voiceState.roomCode,
                            durationSeconds = voiceState.callDurationSeconds,
                            isReconnecting = voiceState.isReconnecting,
                            onReturn = { currentScreen = Screen.VOICE_CALL },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        wifiManager.unregisterReceiver(applicationContext)
        wifiManager.cleanup()
    }

    private fun handlePickedFile(uri: Uri) {
        val fileName = getFileName(uri) ?: "shared_file"
        val cacheFile = File(cacheDir, fileName)

        try {
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(cacheFile).use { output ->
                    input.copyTo(output)
                }
            }
            chatViewModel.sendFile(cacheFile)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
