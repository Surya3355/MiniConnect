package com.surya.miniconnect.data.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper
import android.util.Log
import com.surya.miniconnect.domain.model.ConnectionState
import com.surya.miniconnect.domain.model.DiscoveryState
import com.surya.miniconnect.domain.model.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Manager responsible for interacting with Android Wi-Fi Direct APIs.
 *
 * This class owns the WifiP2pManager and Channel, converting callback-based
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
    /** Observable list of discovered peers. */
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _discoveryState = MutableStateFlow<DiscoveryState>(DiscoveryState.Idle)
    /** Observable discovery lifecycle state. */
    val discoveryState: StateFlow<DiscoveryState> = _discoveryState.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    /** Observable connection lifecycle state. */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

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
     * Registers a BroadcastReceiver to listen for Wi-Fi Direct events.
     * Uses applicationContext to avoid leaking an Activity.
     */
    fun registerReceiver(context: Context) {
        if (registered) return
        val appContext = context.applicationContext
        val mgr = wifiP2pManager ?: return

        @Suppress("MissingPermission")
        val listener = object : WifiDirectBroadcastReceiver.Listener {
            override fun onPeersChanged() {
                mgr.requestPeers(channel) { peerList ->
                    handlePeerList(peerList.deviceList)
                }
            }

            override fun onStateChanged(enabled: Boolean) {
                if (!enabled) {
                    _peers.value = emptyList()
                    _discoveryState.value = DiscoveryState.Error("Wi-Fi P2P disabled")
                }
            }

            override fun onConnectionChanged(connected: Boolean) {
                if (connected) {
                    requestConnectionInfo()
                } else if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
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
     * Unregisters the previously registered BroadcastReceiver. Safe to call multiple times.
     */
    fun unregisterReceiver(context: Context) {
        if (!registered) return
        val appContext = context.applicationContext
        receiver?.let { appContext.unregisterReceiver(it) }
        receiver = null
        registered = false
    }

    /**
     * Starts peer discovery. Returns Result.success on request accepted, or failure.
     */
    @Suppress("MissingPermission")
    suspend fun discoverPeers(): Result<Unit> {
        val mgr = wifiP2pManager
            ?: return Result.failure(IllegalStateException("WifiP2pManager not initialized"))
        val ch = channel
            ?: return Result.failure(IllegalStateException("Channel not initialized"))

        _discoveryState.value = DiscoveryState.Discovering

        return suspendCancellableCoroutine { cont ->
            mgr.discoverPeers(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "discoverPeers started")
                    _discoveryState.value = DiscoveryState.Discovering
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    val msg = "Discovery failed (reason=$reason)"
                    Log.e(TAG, msg)
                    _discoveryState.value = DiscoveryState.Error(msg)
                    if (!cont.isCompleted) cont.resume(Result.failure(IllegalStateException(msg)))
                }
            })
        }
    }

    /**
     * Initiates a connection to the specified [peer].
     * Clears stale persistent groups before connecting to avoid invitation failures.
     */
    @Suppress("MissingPermission")
    suspend fun connect(peer: Peer): Result<Unit> {
        val mgr = wifiP2pManager
            ?: return Result.failure(IllegalStateException("WifiP2pManager not initialized"))
        val ch = channel
            ?: return Result.failure(IllegalStateException("Channel not initialized"))

        _connectionState.value = ConnectionState.Connecting

        cancelPendingOperations(mgr, ch)
        clearPersistentGroups(mgr, ch)

        val config = WifiP2pConfig().apply {
            deviceAddress = peer.address
        }

        return suspendCancellableCoroutine { cont ->
            mgr.connect(ch, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Connection initiated to ${peer.address}")
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    val msg = "Connection failed (reason=$reason)"
                    Log.e(TAG, msg)
                    _connectionState.value = ConnectionState.Failed(msg)
                    if (!cont.isCompleted) cont.resume(Result.failure(IllegalStateException(msg)))
                }
            })
        }
    }

    /**
     * Disconnects from the current Wi-Fi Direct group.
     */
    @Suppress("MissingPermission")
    suspend fun disconnect(): Result<Unit> {
        val mgr = wifiP2pManager
            ?: return Result.failure(IllegalStateException("WifiP2pManager not initialized"))
        val ch = channel
            ?: return Result.failure(IllegalStateException("Channel not initialized"))

        return suspendCancellableCoroutine { cont ->
            mgr.removeGroup(ch, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "Disconnected from group")
                    _connectionState.value = ConnectionState.Disconnected
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "removeGroup failed (reason=$reason)")
                    _connectionState.value = ConnectionState.Disconnected
                    if (!cont.isCompleted) cont.resume(Result.success(Unit))
                }
            })
        }
    }

    /**
     * Releases all Wi-Fi Direct resources. Call during Activity teardown.
     */
    fun cleanup() {
        _peers.value = emptyList()
        _discoveryState.value = DiscoveryState.Idle
        _connectionState.value = ConnectionState.Idle
    }

    /** Returns whether the Wi-Fi Direct infrastructure has been successfully initialized. */
    fun isInitialized(): Boolean = initialized

    @Suppress("MissingPermission")
    private fun requestConnectionInfo() {
        val mgr = wifiP2pManager ?: return
        val ch = channel ?: return

        mgr.requestConnectionInfo(ch) { info ->
            if (info != null && info.groupFormed) {
                val ownerAddress = info.groupOwnerAddress?.hostAddress ?: ""
                _connectionState.value = ConnectionState.Connected(
                    groupOwnerAddress = ownerAddress,
                    isGroupOwner = info.isGroupOwner
                )
                Log.d(TAG, "Connected: groupOwner=${info.isGroupOwner}, ownerAddr=$ownerAddress")
            }
        }
    }

    private fun handlePeerList(deviceList: Collection<WifiP2pDevice>) {
        val mapped = deviceList.map { device ->
            Peer(
                name = device.deviceName ?: "",
                address = device.deviceAddress ?: "",
                status = device.status
            )
        }
        Log.d(TAG, "handlePeerList: ${mapped.size} peers")
        _peers.value = mapped
        _discoveryState.value = if (mapped.isEmpty()) DiscoveryState.Empty else DiscoveryState.Success
    }

    @Suppress("MissingPermission")
    private fun cancelPendingOperations(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        try {
            mgr.cancelConnect(ch, null)
        } catch (e: Exception) {
            Log.w(TAG, "cancelConnect failed", e)
        }
        try {
            mgr.removeGroup(ch, null)
        } catch (e: Exception) {
            Log.w(TAG, "removeGroup failed", e)
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun clearPersistentGroups(mgr: WifiP2pManager, ch: WifiP2pManager.Channel) {
        try {
            val method = WifiP2pManager::class.java.getMethod(
                "deletePersistentGroup",
                WifiP2pManager.Channel::class.java,
                Int::class.javaPrimitiveType,
                WifiP2pManager.ActionListener::class.java
            )
            for (netId in 0..31) {
                method.invoke(mgr, ch, netId, null)
            }
            Log.d(TAG, "Cleared persistent groups")
        } catch (e: Exception) {
            Log.w(TAG, "clearPersistentGroups not available on this device", e)
        }
    }

    companion object {
        private const val TAG = "WifiDirectManager"
    }
}
