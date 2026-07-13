package com.surya.miniconnect.data.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager

/**
 * BroadcastReceiver dedicated to Wi‑Fi P2P events. It only translates system
 * broadcasts into calls on the provided [Listener].
 */
class WifiDirectBroadcastReceiver(
    private val listener: Listener
) : BroadcastReceiver() {

    interface Listener {
        fun onPeersChanged()
        fun onStateChanged(enabled: Boolean)
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
            else -> {
                // other actions are intentionally ignored here; manager may request peers on connect change
            }
        }
    }
}

