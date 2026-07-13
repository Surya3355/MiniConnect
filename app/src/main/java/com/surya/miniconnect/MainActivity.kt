package com.surya.miniconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.surya.miniconnect.data.wifi.WifiDirectManager
import com.surya.miniconnect.presentation.discovery.DiscoveryViewModel
import com.surya.miniconnect.ui.screens.DiscoveryScreen
import android.widget.Toast
import com.surya.miniconnect.ui.theme.MiniConnectTheme
import com.surya.miniconnect.util.PermissionManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue

class MainActivity : ComponentActivity() {

    private val wifiManager = WifiDirectManager()
    private val permissionManager = PermissionManager()

    private val viewModel: DiscoveryViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DiscoveryViewModel(wifiManager) as T
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            ensureWifiDirectReady()
        }
    }

    private fun ensureWifiDirectReady() {
        val initResult = wifiManager.initialize(applicationContext)
        if (initResult.isSuccess) {
            wifiManager.registerReceiver(applicationContext)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Kick off initialization if permissions already granted
        if (permissionManager.hasAllPermissions(this)) {
            ensureWifiDirectReady()
        } else {
            // Request permissions; result handled by launcher
            permissionLauncher.launch(permissionManager.requiredPermissions())
        }

        setContent {
            MiniConnectTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                DiscoveryScreen(
                    uiState = uiState,
                    onDiscover = { viewModel.discoverPeers() },
                    onRefresh = { viewModel.refresh() },
                    onPeerClick = { peer ->
                        android.util.Log.d("DiscoveryUI", "peer tapped: ${peer.address}")
                        Toast.makeText(this, "Device selection not implemented in this sprint.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (permissionManager.hasAllPermissions(this)) {
            ensureWifiDirectReady()
        }
    }

    override fun onStop() {
        super.onStop()
        wifiManager.unregisterReceiver(applicationContext)
    }
}
