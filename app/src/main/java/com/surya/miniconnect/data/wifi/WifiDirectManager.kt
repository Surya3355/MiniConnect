@file:Suppress("unused")

package com.surya.miniconnect.data.wifi

import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import com.surya.miniconnect.domain.model.DiscoveryState
import com.surya.miniconnect.domain.model.Peer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume

/**
 * Manager responsible for interacting with Android Wi‑Fi Direct APIs.
 *
 * This class owns the WifiP2pManager, the Channel and converts callback-based
 * Android APIs into coroutine-friendly, StateFlow-backed observable state.
 *
 * Important: This class must not hold an Activity context. All context usage
 * uses applicationContext. Android callbacks never escape this class.
 */
class WifiDirectManager {

    private var wifiP2pManager: WifiP2pManager? = null

    private var channel: WifiP2pManager.Channel? = null

    private var initialized: Boolean = false

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private var receiver: WifiDirectBroadcastReceiver? = null
    private var registered: Boolean = false

    /**
     * Initializes the underlying WifiP2pManager and Channel. Safe to call multiple times.
     */
    fun initialize(context: Context): Result<Unit> {
        if (initialized) return Result.success(Unit)

        val appContext = context.applicationContext
        val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
            ?: return Result.failure(IllegalStateException("WifiP2pManager not available"))

        val newChannel = manager.initialize(appContext, Looper.getMainLooper(), null)
            ?: return Result.failure(IllegalStateException("Failed to initialize WifiP2pManager.Channel"))

        wifiP2pManager = manager
        channel = newChannel
        initialized = true

        return Result.success(Unit)
    }

    /**
     * Register a BroadcastReceiver to listen for Wi‑Fi Direct events.
     * Registration uses the provided context's applicationContext to avoid leaking an Activity.
     */
    fun registerReceiver(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val mgr = wifiP2pManager ?: return

        @Suppress("MissingPermission")
        val listener = object : WifiDirectBroadcastReceiver.Listener {
            override fun onPeersChanged() {
                // Request peer list and update state
                mgr.requestPeers(channel) { peerList ->
                    handlePeerList(peerList.deviceList)
                }
            }

            override fun onStateChanged(enabled: Boolean) {
                if (!enabled) {
                    _peers.value = emptyList()
                    _discoveryState.value = DiscoveryState.Error("Wi‑Fi P2P disabled")
                }
            }
        }

        receiver = WifiDirectBroadcastReceiver(listener)

        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        appContext.registerReceiver(receiver, filter)
        registered = true
    }

    /**
     * Unregister the previously registered BroadcastReceiver. Safe to call multiple times.
     */
    fun unregisterReceiver(context: Context) {
        if (!registered) return
        val appContext = context.applicationContext
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
        registered = false
    }

    /**
     * Start peer discovery. Returns Result.success on request accepted, or failure.
     */
    @Suppress("MissingPermission")
    suspend fun discoverPeers(): Result<Unit> {
        val mgr = wifiP2pManager ?: return Result.failure(IllegalStateException("WifiP2pManager not initialized"))
        val ch = channel ?: return Result.failure(IllegalStateException("WifiP2pManager.Channel not initialized"))

        _discoveryState.value = DiscoveryState.Discovering

        return suspendCancellableCoroutine { cont ->
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // Discovery started. The peers will be delivered via the peers changed broadcast.
                    Log.d("WifiDirectManager", "discoverPeers onSuccess: discovery started")
                    _discoveryState.value = DiscoveryState.Discovering
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    val msg = "discoverPeers failed: $reason"
                    Log.e("WifiDirectManager", msg)
                    _discoveryState.value = DiscoveryState.Error(msg)
                    if (!cont.isCompleted) cont.resume(Result.failure(IllegalStateException(msg)))
                }
            })
        }
    }

    private fun handlePeerList(deviceList: Collection<WifiP2pDevice>) {
        val mapped = deviceList.map {
            Peer(
                name = it.deviceName ?: "",
                address = it.deviceAddress ?: "",
                status = it.status
            )
        }

        Log.d("WifiDirectManager", "handlePeerList: ${mapped.size} peers")
        _peers.value = mapped

        _discoveryState.value = when {
            mapped.isEmpty() -> DiscoveryState.Empty
            else -> DiscoveryState.Success
        }
    }

    /**
     * Returns whether the Wi‑Fi Direct infrastructure has been successfully initialized.
     */
    fun isInitialized(): Boolean = initialized
}

