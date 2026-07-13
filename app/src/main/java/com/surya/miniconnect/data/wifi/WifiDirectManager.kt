package com.surya.miniconnect.data.wifi

import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.os.Looper

class WifiDirectManager {

    private var wifiP2pManager: WifiP2pManager? = null

    private var channel: WifiP2pManager.Channel? = null

    private var initialized: Boolean = false

    /**
     * Initializes the Wi-Fi Direct infrastructure required for future operations.
     *
     * This method obtains the system [WifiP2pManager] and creates a corresponding
     * [WifiP2pManager.Channel]. It must be called before any Wi-Fi Direct operation
     * that depends on the underlying framework.
     *
     * The method is safe to call multiple times. If initialization has already
     * completed successfully, it returns [Result.success] without recreating the
     * manager or channel.
     *
     * [Result.success] represents a successful initialization. [Result.failure]
     * represents an initialization failure, such as the manager being unavailable
     * or the channel failing to initialize.
     *
     * @param context A context used to access the application context and system service.
     * @return A successful result when initialization completes, or a failure result when it does not.
     */
    fun initialize(context: Context): Result<Unit> {
        if (initialized) {
            return Result.success(Unit)
        }

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
     * Returns whether the Wi-Fi Direct infrastructure has been successfully initialized.
     *
     * @return true when initialization has completed successfully, false otherwise.
     */
    fun isInitialized(): Boolean = initialized
}

