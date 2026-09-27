package com.parlo.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Audio focus (voice communication) plus earbud / Bluetooth routing.
 * Reacts to headset connect/disconnect mid-session by re-picking the best communication device.
 */
class AudioRouteManager(
    context: Context,
    private val onFocusChange: (FocusState) -> Unit,
) {
    enum class FocusState { GAINED, LOST_TRANSIENT, LOST }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var previousMode = AudioManager.MODE_NORMAL
    private var deviceCallback: AudioDeviceCallback? = null
    private var active = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> onFocusChange(FocusState.GAINED)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onFocusChange(FocusState.LOST_TRANSIENT)
            AudioManager.AUDIOFOCUS_LOSS -> onFocusChange(FocusState.LOST)
        }
    }

    fun begin(): Boolean {
        if (active) return true
        previousMode = audioManager.mode
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        val granted = requestFocus()
        applyBestRoute()
        registerDeviceCallback()
        active = true
        return granted
    }

    fun end() {
        if (!active) return
        active = false
        deviceCallback?.let { runCatching { audioManager.unregisterAudioDeviceCallback(it) } }
        deviceCallback = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { audioManager.clearCommunicationDevice() }
        } else {
            @Suppress("DEPRECATION")
            runCatching {
                if (audioManager.isBluetoothScoOn) { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false }
                audioManager.isSpeakerphoneOn = false
            }
        }
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
        audioManager.mode = previousMode
    }

    private fun requestFocus(): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusListener, Handler(Looper.getMainLooper()))
            .build()
        focusRequest = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun registerDeviceCallback() {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = applyBestRoute()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = applyBestRoute()
        }
        deviceCallback = cb
        audioManager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
    }

    /** Prefer BT headset > wired headset > USB headset > built-in earpiece. */
    private fun applyBestRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val pick = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET || it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
                ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                ?: devices.firstOrNull()
            pick?.let {
                val ok = runCatching { audioManager.setCommunicationDevice(it) }.getOrDefault(false)
                Log.i(TAG, "route -> ${it.productName} (${it.type}) ok=$ok")
            }
        } else {
            @Suppress("DEPRECATION")
            run {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val bt = outputs.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
                if (bt) {
                    audioManager.startBluetoothSco(); audioManager.isBluetoothScoOn = true
                } else {
                    if (audioManager.isBluetoothScoOn) { audioManager.stopBluetoothSco(); audioManager.isBluetoothScoOn = false }
                }
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    private companion object { const val TAG = "AudioRoute" }
}
