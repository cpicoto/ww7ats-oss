package net.wwats.ww7ats.viewmodel

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.wwats.ww7ats.data.Settings
import net.wwats.ww7ats.data.SettingsManager
import net.wwats.ww7ats.media.StreamingManager
import net.wwats.ww7ats.model.ConnectionState
import net.wwats.ww7ats.model.ConnectionStatus
import net.wwats.ww7ats.model.StreamQuality
import net.wwats.ww7ats.service.StreamingService

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val settingsManager = SettingsManager(application)
    val streamingManager = StreamingManager(application)

    val settings: StateFlow<Settings> = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val connectionStatus: StateFlow<ConnectionStatus> = streamingManager.connectionStatus
    val cameraEnabled: StateFlow<Boolean> = streamingManager.cameraEnabled
    val isFrontCamera: StateFlow<Boolean> = streamingManager.isFrontCamera
    val cwIdDisplayText: StateFlow<String> = streamingManager.cwIdDisplayText
    val slideshowActive: StateFlow<Boolean> = streamingManager.slideshowActive
    val slideshowIndex: StateFlow<Int> = streamingManager.slideshowIndex
    val slideshowCount: StateFlow<Int> = streamingManager.slideshowCount
    val currentSlideBitmap: StateFlow<Bitmap?> = streamingManager.currentSlideBitmap

    fun toggleStreaming() {
        val current = connectionStatus.value
        val currentSettings = settings.value

        if (current.isStreaming) {
            val callsign = if (currentSettings.morseCWIDEnabled) currentSettings.callsign else ""
            streamingManager.stopStreaming(callsign, currentSettings.morseWPM)
            stopStreamingService()
        } else {
            val rtmpUrl = settingsManager.validateRtmpEndpoint(

                currentSettings.streamKey
            )
            if (rtmpUrl == null) {
                // Show error - invalid RTMP URL
                return
            }
            streamingManager.startStreaming(rtmpUrl, currentSettings.streamQuality, currentSettings.callsign)
            startStreamingService()
        }
    }

    fun switchCamera() {
        streamingManager.switchCamera()
    }

    fun toggleCamera(callsign: String = "") {
        streamingManager.toggleCamera(callsign)
    }

    fun startSlideshow(uris: List<Uri>) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val bitmaps = uris.mapNotNull { uri ->
                try {
                    val bitmap = app.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    } ?: return@mapNotNull null
                    // Apply EXIF orientation so images display upright
                    val rotation = app.contentResolver.openInputStream(uri)?.use { stream ->
                        val exif = ExifInterface(stream)
                        when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> -1f
                            ExifInterface.ORIENTATION_FLIP_VERTICAL -> -2f
                            ExifInterface.ORIENTATION_TRANSPOSE -> -3f
                            ExifInterface.ORIENTATION_TRANSVERSE -> -4f
                            else -> 0f
                        }
                    } ?: 0f
                    if (rotation > 0f) {
                        val matrix = Matrix().apply { postRotate(rotation) }
                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                    } else {
                        bitmap
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (bitmaps.isNotEmpty()) {
                streamingManager.startSlideshow(bitmaps)
            }
        }
    }

    fun stopSlideshow() {
        streamingManager.stopSlideshow()
    }

    // Settings updates
    fun updateCallsign(value: String) {
        viewModelScope.launch { settingsManager.updateCallsign(value) }
    }

    fun updateStreamKey(value: String) {
        viewModelScope.launch { settingsManager.updateStreamKey(value) }
    }

    fun updateStreamQuality(quality: StreamQuality) {
        viewModelScope.launch { settingsManager.updateStreamQuality(quality) }
    }

    fun updateMorseCWIDEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.updateMorseCWIDEnabled(enabled) }
    }

    fun updateMorseWPM(wpm: Int) {
        viewModelScope.launch { settingsManager.updateMorseWPM(wpm) }
    }

    private fun startStreamingService() {
        val intent = Intent(getApplication(), StreamingService::class.java)
        getApplication<Application>().startForegroundService(intent)
    }

    private fun stopStreamingService() {
        val intent = Intent(getApplication(), StreamingService::class.java)
        getApplication<Application>().stopService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        streamingManager.release()
    }
}
