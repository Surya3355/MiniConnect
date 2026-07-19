package com.surya.miniconnect.data.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.surya.miniconnect.domain.model.AudioRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns audio output routing for a voice call: earpiece / speaker / bluetooth.
 *
 * - Keeps the phone in MODE_IN_COMMUNICATION for the call's lifetime.
 * - Auto-switches to a Bluetooth headset when one connects, and falls back to
 *   earpiece when it disconnects.
 * - Exposes the current route and the list of available routes as flows.
 */
class AudioRouteController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentRoute = MutableStateFlow(AudioRoute.EARPIECE)
    val currentRoute: StateFlow<AudioRoute> = _currentRoute.asStateFlow()

    private val _availableRoutes = MutableStateFlow(listOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER))
    val availableRoutes: StateFlow<List<AudioRoute>> = _availableRoutes.asStateFlow()

    /** True while another app (a phone/VoIP call) has taken audio focus. */
    private val _isInterrupted = MutableStateFlow(false)
    val isInterrupted: StateFlow<Boolean> = _isInterrupted.asStateFlow()

    private var active = false
    private var userChoseRoute = false

    private var audioFocusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> _isInterrupted.value = true
            AudioManager.AUDIOFOCUS_GAIN -> _isInterrupted.value = false
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            // Auto-switch to a Bluetooth headset the moment it appears
            if (hasBluetooth() && !userChoseRoute) {
                setRoute(AudioRoute.BLUETOOTH, userInitiated = false)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
            if (_currentRoute.value == AudioRoute.BLUETOOTH && !hasBluetooth()) {
                setRoute(AudioRoute.EARPIECE, userInitiated = false)
            }
        }
    }

    /** Begin managing routing for a call. */
    fun start() {
        if (active) return
        active = true
        userChoseRoute = false
        _isInterrupted.value = false
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        requestAudioFocus()
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
        refreshDevices()
        // Prefer bluetooth if a headset is already connected, else earpiece
        val initial = if (hasBluetooth()) AudioRoute.BLUETOOTH else AudioRoute.EARPIECE
        setRoute(initial, userInitiated = false)
    }

    /** Stop managing routing and reset the audio mode. */
    fun stop() {
        if (!active) return
        active = false
        audioManager.unregisterAudioDeviceCallback(deviceCallback)
        abandonAudioFocus()
        clearRouting()
        audioManager.mode = AudioManager.MODE_NORMAL
        _currentRoute.value = AudioRoute.EARPIECE
        _isInterrupted.value = false
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
    }

    /** Manual route selection from the UI. */
    fun setRoute(route: AudioRoute, userInitiated: Boolean = true) {
        if (userInitiated) userChoseRoute = true
        if (route == AudioRoute.BLUETOOTH && !hasBluetooth()) return
        _currentRoute.value = route
        applyRoute(route)
    }

    @Suppress("DEPRECATION")
    private fun applyRoute(route: AudioRoute) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val type = when (route) {
                AudioRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                AudioRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                AudioRoute.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            val device = audioManager.availableCommunicationDevices.firstOrNull { it.type == type }
            if (device != null) {
                audioManager.clearCommunicationDevice()
                audioManager.setCommunicationDevice(device)
            } else {
                Log.w(TAG, "No communication device for $route")
            }
        } else {
            when (route) {
                AudioRoute.SPEAKER -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = true
                }
                AudioRoute.EARPIECE -> {
                    audioManager.stopBluetoothSco()
                    audioManager.isBluetoothScoOn = false
                    audioManager.isSpeakerphoneOn = false
                }
                AudioRoute.BLUETOOTH -> {
                    audioManager.isSpeakerphoneOn = false
                    audioManager.startBluetoothSco()
                    audioManager.isBluetoothScoOn = true
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun clearRouting() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = false
        }
    }

    private fun refreshDevices() {
        val routes = mutableListOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER)
        if (hasBluetooth()) routes.add(AudioRoute.BLUETOOTH)
        _availableRoutes.value = routes
    }

    private fun hasBluetooth(): Boolean {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
    }

    companion object {
        private const val TAG = "AudioRouteController"
    }
}
