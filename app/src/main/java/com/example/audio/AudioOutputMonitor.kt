package com.example.audio

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.example.model.OutputDeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputMonitor(private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    private val _deviceDetailName = MutableStateFlow("Device Speakers")
    val deviceDetailName: StateFlow<String> = _deviceDetailName.asStateFlow()

    private val _currentDevice = MutableStateFlow(OutputDeviceType.PHONE_SPEAKER)
    val currentDevice: StateFlow<OutputDeviceType> = _currentDevice.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refresh()
        }
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    fun refresh() {
        _currentDevice.value = detectCurrentOutputDevice()
    }

    private fun detectCurrentOutputDevice(): OutputDeviceType {
        val isCarMode = uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_CAR
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()

        val usbDac = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usbDac != null) {
            _deviceDetailName.value = usbDac.productName?.toString()?.ifBlank { "USB Audio DAC" } ?: "USB Audio DAC"
            return OutputDeviceType.USB_DAC
        }

        val btA2dp = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        if (btA2dp != null) {
            val name = btA2dp.productName?.toString().orEmpty()
            _deviceDetailName.value = name.ifBlank { "Bluetooth Audio" }
            val looksLikeCar = isCarMode ||
                name.contains("car", ignoreCase = true) ||
                name.contains("auto", ignoreCase = true) ||
                name.contains("mazda", ignoreCase = true) ||
                name.contains("toyota", ignoreCase = true) ||
                name.contains("honda", ignoreCase = true) ||
                name.contains("ford", ignoreCase = true) ||
                name.contains("bmw", ignoreCase = true) ||
                name.contains("audi", ignoreCase = true)
            return if (looksLikeCar) OutputDeviceType.CAR_AUDIO else OutputDeviceType.HEADPHONES_BLUETOOTH
        }

        if (isCarMode) {
            _deviceDetailName.value = "Android Auto Vehicle Audio"
            return OutputDeviceType.CAR_AUDIO
        }

        val wired = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
        }
        if (wired != null) {
            _deviceDetailName.value = wired.productName?.toString()?.ifBlank { "Wired Headphones" } ?: "Wired Headphones"
            return OutputDeviceType.HEADPHONES_BLUETOOTH
        }

        val external = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_HDMI ||
                it.type == AudioDeviceInfo.TYPE_LINE_ANALOG ||
                it.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
                it.type == AudioDeviceInfo.TYPE_AUX_LINE
        }
        if (external != null) {
            _deviceDetailName.value = external.productName?.toString()?.ifBlank { "External Speaker" } ?: "External Speaker"
            return OutputDeviceType.EXTERNAL_SPEAKER
        }

        _deviceDetailName.value = "Smartphone Speakers"
        return OutputDeviceType.PHONE_SPEAKER
    }
}
