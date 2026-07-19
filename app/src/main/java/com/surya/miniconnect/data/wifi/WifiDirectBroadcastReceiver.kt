package com.surya.miniconnect.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pManager

/**
 * BroadcastReceiver dedicated to Wi-Fi P2P events. Translates system
 * broadcasts into calls on the provided [Listener].
 */
class WifiDirectBroadcastReceiver(
    private val listener: Listener
) : BroadcastReceiver() {

    /** Callback interface for Wi-Fi Direct system events. */
    interface Listener {
        /** Peer list has changed; request an updated list. */
        fun onPeersChanged()

        /** Wi-Fi P2P has been enabled or disabled on the device. */
        fun onStateChanged(enabled: Boolean)

        /** Connection state changed; [connected] is true when a group is formed. */
        fun onConnectionChanged(connected: Boolean)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                listener.onPeersChanged()
            }
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                listener.onStateChanged(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                @Suppress("DEPRECATION")
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                listener.onConnectionChanged(networkInfo?.isConnected == true)
            }
        }
    }
}
