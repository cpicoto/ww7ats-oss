package net.wwats.ww7ats.media

import android.content.Context
import android.graphics.*
import android.graphics.drawable.VectorDrawable
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.TranslateTo
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.common.ConnectChecker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import net.wwats.ww7ats.model.ConnectionState
import net.wwats.ww7ats.model.ConnectionStatus
import net.wwats.ww7ats.model.StreamQuality

/**
 * Manages RTMP streaming using RootEncoder (RtmpCamera2).
 * Handles camera preview, RTMP publish, slideshow compositing, and Morse CW ID.
 */
class StreamingManager(private val context: Context) : ConnectChecker {

    private var rtmpCamera: RtmpCamera2? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _connectionStatus = MutableStateFlow(ConnectionStatus())
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus

    private val _cameraEnabled = MutableStateFlow(true)
    val cameraEnabled: StateFlow<Boolean> = _cameraEnabled

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera

    private val _cwIdDisplayText = MutableStateFlow("")
    val cwIdDisplayText: StateFlow<String> = _cwIdDisplayText

    private val _slideshowActive = MutableStateFlow(false)
    val slideshowActive: StateFlow<Boolean> = _slideshowActive

    private val _slideshowIndex = MutableStateFlow(0)
    val slideshowIndex: StateFlow<Int> = _slideshowIndex

    private val _slideshowCount = MutableStateFlow(0)
    val slideshowCount: StateFlow<Int> = _slideshowCount

    private val _currentSlideBitmap = MutableStateFlow<Bitmap?>(null)
    val currentSlideBitmap: StateFlow<Bitmap?> = _currentSlideBitmap

    private var slideshowImages: List<Bitmap> = emptyList()
    private var slideshowJob: Job? = null
    private var cameraOffFilter: ImageObjectFilterRender? = null
    private var slideshowFilter: SlideshowFilterRender? = null
    private var lastCallsign: String = ""
    private var isPortrait: Boolean = true

    val isStreaming: Boolean get() = _connectionStatus.value.isStreaming

    // --- Camera & RTMP Setup ---

    fun initCamera(openGlView: com.pedro.library.view.OpenGlView) {
        rtmpCamera = RtmpCamera2(openGlView, this)
    }

    fun initCamera() {
        rtmpCamera = RtmpCamera2(context, this)
    }

    fun startPreview(width: Int = 1280, height: Int = 720) {
        val camera = rtmpCamera ?: return
        camera.startPreview(
            if (_isFrontCamera.value) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK,
            width, height
        )
    }

    fun stopPreview() {
        rtmpCamera?.stopPreview()
    }

    fun restartPreview() {
        val camera = rtmpCamera ?: return
        if (camera.isOnPreview) {
            camera.stopPreview()
            camera.startPreview(
                if (_isFrontCamera.value) CameraHelper.Facing.FRONT else CameraHelper.Facing.BACK,
                1280, 720
            )
            // Re-apply active filter after preview restart
            if (_slideshowActive.value) {
                applySlideshowFilter()
            } else if (!_cameraEnabled.value && isStreaming) {
                applyCameraOffFilter(lastCallsign)
            }
        }
    }

    fun startStreaming(rtmpUrl: String, quality: StreamQuality, callsign: String = "") {
        val camera = rtmpCamera ?: return
        lastCallsign = callsign
        _connectionStatus.value = ConnectionStatus(ConnectionState.CONNECTING)

        // prepareVideo(w, h, bitrate) auto-detects rotation via CameraHelper.
        // prepareAudio() uses default mic settings.
        // Both must be called before startStream.
        val audioPrepared = camera.prepareAudio()
        val videoPrepared = camera.prepareVideo(
            quality.width,
            quality.height,
            quality.bitrate
        )

        if (audioPrepared && videoPrepared) {
            camera.startStream(rtmpUrl)
        } else {
            _connectionStatus.value = ConnectionStatus(
                ConnectionState.IDLE,
                errorMessage = "Failed to prepare audio/video encoder"
            )
        }
    }

    fun stopStreaming(callsign: String = "", wpm: Int = 25) {
        if (callsign.isNotBlank()) {
            val camera = rtmpCamera
            val cameraOff = !_cameraEnabled.value
            var imageFilter: ImageObjectFilterRender? = null
            if (camera != null) {
                try {
                    val filter = ImageObjectFilterRender()
                    camera.getGlInterface().setFilter(filter)
                    filter.setImage(renderCWIDText(" ", camera.getStreamWidth(), camera.getStreamHeight(), cameraOff, callsign))
                    filter.setScale(100f, 100f)
                    filter.setPosition(0f, 0f)
                    imageFilter = filter
                    cameraOffFilter = null
                    slideshowFilter = null  // CW ID filter replaces all filters
                    Log.d("StreamingManager", "CW ID filter installed, stream ${camera.getStreamWidth()}x${camera.getStreamHeight()}, cameraOff=$cameraOff")
                } catch (e: Exception) {
                    Log.e("StreamingManager", "Failed to set CW ID filter", e)
                }
            }

            scope.launch(Dispatchers.IO) {
                delay(300)
                sendMorseId(callsign, wpm, imageFilter, camera, cameraOff)
                withContext(Dispatchers.Main) {
                    try {
                        camera?.getGlInterface()?.clearFilters()
                    } catch (_: Exception) {}
                    cameraOffFilter = null
                    slideshowFilter = null
                    doStopStream()
                }
            }
        } else {
            doStopStream()
        }
    }

    private fun doStopStream() {
        rtmpCamera?.stopStream()
        _connectionStatus.value = ConnectionStatus(ConnectionState.IDLE)
    }

    fun switchCamera() {
        rtmpCamera?.switchCamera()
        _isFrontCamera.value = !_isFrontCamera.value
    }

    fun toggleCamera(callsign: String = "") {
        _cameraEnabled.value = !_cameraEnabled.value
        lastCallsign = callsign
        if (_cameraEnabled.value) {
            // Camera on: remove camera-off filter (slideshow filter stays if active)
            if (!_slideshowActive.value) {
                removeCameraOffFilter()
            }
        } else if (isStreaming && !_slideshowActive.value) {
            // Camera off while streaming without slideshow: apply opaque overlay
            applyCameraOffFilter(callsign)
        }
    }

    private fun applyCameraOffFilter(callsign: String) {
        val camera = rtmpCamera ?: return
        try {
            val filter = ImageObjectFilterRender()
            camera.getGlInterface().setFilter(filter)
            filter.setScale(100f, 100f)
            filter.setPosition(0f, 0f)
            cameraOffFilter = filter
            // Delay image set so GL thread can initialize the filter first
            scope.launch {
                delay(200)
                val (w, h) = getFilterDimensions()
                filter.setImage(renderCameraOffBitmap(callsign, w, h))
                Log.d("StreamingManager", "Camera-off filter image set: ${w}x${h}")
            }
        } catch (e: Exception) {
            Log.e("StreamingManager", "Failed to set camera-off filter", e)
        }
    }

    private fun removeCameraOffFilter() {
        try {
            rtmpCamera?.getGlInterface()?.clearFilters()
        } catch (_: Exception) {}
        cameraOffFilter = null
    }

    val hasMultipleCameras: Boolean
        get() {
            return try {
                val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                cameraManager.cameraIdList.size > 1
            } catch (_: Exception) {
                false
            }
        }

    /** Call when device orientation changes so filters can adapt. */
    fun updateOrientation(portrait: Boolean) {
        if (isPortrait == portrait) return
        isPortrait = portrait
        // Re-apply slideshow filter with new PiP position
        if (_slideshowActive.value && slideshowFilter != null) {
            updatePipRect(slideshowFilter!!)
            updateSlideshowFilterImage()
        }
    }

    /** Returns filter-appropriate dimensions from the actual GL framebuffer. */
    private fun getFilterDimensions(): Pair<Int, Int> {
        // Prefer the actual GL framebuffer dimensions from the active filter
        val filter = slideshowFilter
        if (filter != null) {
            val fw = filter.glWidth
            val fh = filter.glHeight
            if (fw > 0 && fh > 0) return fw to fh
        }
        val camera = rtmpCamera
        if (camera != null && isStreaming) {
            val sw = camera.getStreamWidth()
            val sh = camera.getStreamHeight()
            if (sw > 0 && sh > 0) return sw to sh
        }
        return 1280 to 720
    }

    // --- Slideshow ---

    fun startSlideshow(images: List<Bitmap>) {
        if (images.isEmpty()) return
        slideshowImages = images
        _slideshowIndex.value = 0
        _slideshowCount.value = images.size
        _slideshowActive.value = true
        _currentSlideBitmap.value = images[0]

        // Apply slideshow filter to GL pipeline (works during preview and streaming)
        applySlideshowFilter()

        // Start cycling timer
        slideshowJob = scope.launch {
            while (isActive && _slideshowActive.value) {
                delay(5000)
                if (!_slideshowActive.value) break
                _slideshowIndex.value = (_slideshowIndex.value + 1) % slideshowImages.size
                _currentSlideBitmap.value = slideshowImages.getOrNull(_slideshowIndex.value)
                updateSlideshowFilterImage()
            }
        }
    }

    fun stopSlideshow() {
        slideshowJob?.cancel()
        slideshowJob = null
        _slideshowActive.value = false
        slideshowImages = emptyList()
        _slideshowIndex.value = 0
        _slideshowCount.value = 0
        _currentSlideBitmap.value = null
        removeSlideshowFilter()
        // Restore camera-off filter if needed
        if (isStreaming && !_cameraEnabled.value) {
            applyCameraOffFilter(lastCallsign)
        }
    }

    private fun applySlideshowFilter() {
        val camera = rtmpCamera ?: return
        try {
            // Slideshow filter replaces any camera-off filter
            cameraOffFilter = null
            val filter = SlideshowFilterRender()
            // When GL is ready, render the slide at the actual framebuffer dimensions
            filter.onGlReady = { w, h ->
                scope.launch {
                    updatePipRect(filter)
                    val slide = slideshowImages.getOrNull(_slideshowIndex.value) ?: return@launch
                    filter.setSlideImage(renderSlideBitmap(slide, w, h))
                    Log.d("StreamingManager", "Slideshow filter applied: ${w}x${h}")
                }
            }
            camera.getGlInterface().setFilter(filter)
            slideshowFilter = filter
        } catch (e: Exception) {
            Log.e("StreamingManager", "Failed to apply slideshow filter", e)
        }
    }

    private fun updateSlideshowFilterImage() {
        val filter = slideshowFilter ?: return
        try {
            val (w, h) = getFilterDimensions()
            val slide = slideshowImages.getOrNull(_slideshowIndex.value) ?: return
            filter.setSlideImage(renderSlideBitmap(slide, w, h))
        } catch (e: Exception) {
            Log.e("StreamingManager", "Failed to update slideshow filter", e)
        }
    }

    private fun updatePipRect(filter: SlideshowFilterRender) {
        val (w, h) = getFilterDimensions()
        // PiP must always be 16:9 (camera aspect). Expressed in normalized UV coords,
        // the pixel sizes must satisfy: (pipW * w) / (pipH * h) == 16/9.
        val pipPixelW: Float
        val pipPixelH: Float
        if (w > h) {
            // Landscape framebuffer
            pipPixelW = 240f
            pipPixelH = 135f
        } else {
            // Portrait framebuffer — keep PiP small but 16:9
            pipPixelW = 200f
            pipPixelH = 112f
        }
        val pipW = pipPixelW / w
        val pipH = pipPixelH / h
        val margin = 20f / maxOf(w, h)
        filter.setPipRect(1f - pipW - margin, margin, pipW, pipH)
    }

    private fun removeSlideshowFilter() {
        if (slideshowFilter != null) {
            try {
                rtmpCamera?.getGlInterface()?.clearFilters()
            } catch (_: Exception) {}
            slideshowFilter = null
        }
    }

    /** Renders a slide image aspect-fit on black canvas. */
    private fun renderSlideBitmap(slide: Bitmap, streamWidth: Int, streamHeight: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(streamWidth, streamHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        // Aspect-fit the slide image
        val scale = minOf(streamWidth.toFloat() / slide.width, streamHeight.toFloat() / slide.height)
        val drawW = slide.width * scale
        val drawH = slide.height * scale
        val drawX = (streamWidth - drawW) / 2f
        val drawY = (streamHeight - drawH) / 2f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(slide, null, RectF(drawX, drawY, drawX + drawW, drawY + drawH), paint)

        return bitmap
    }

    // --- Morse CW ID ---

    private fun renderCameraOffBitmap(callsign: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val cx = width / 2f
        val cy = height / 2f

        // Draw videocam-off icon from vector drawable
        val iconSize = 96
        try {
            val drawable = ContextCompat.getDrawable(context, net.wwats.ww7ats.R.drawable.ic_videocam_off)
            drawable?.setBounds(
                (cx - iconSize / 2).toInt(), (cy - iconSize / 2 - 40).toInt(),
                (cx + iconSize / 2).toInt(), (cy + iconSize / 2 - 40).toInt()
            )
            drawable?.draw(canvas)
        } catch (_: Exception) {}

        // Draw callsign
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            callsign.ifBlank { "NO CALLSIGN" },
            cx, cy + 60f, textPaint
        )

        return bitmap
    }

    private fun renderCWIDText(text: String, width: Int, height: Int, cameraOff: Boolean = false, callsign: String = ""): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        if (cameraOff) {
            // Opaque black background with camera-off icon + callsign
            canvas.drawColor(Color.BLACK)
            val cx = width / 2f
            val cy = height / 2f
            val iconSize = 96
            try {
                val drawable = ContextCompat.getDrawable(context, net.wwats.ww7ats.R.drawable.ic_videocam_off)
                drawable?.setBounds(
                    (cx - iconSize / 2).toInt(), (cy - iconSize / 2 - 60).toInt(),
                    (cx + iconSize / 2).toInt(), (cy + iconSize / 2 - 60).toInt()
                )
                drawable?.draw(canvas)
            } catch (_: Exception) {}
            val callsignPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 48f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(callsign.ifBlank { "NO CALLSIGN" }, cx, cy + 30f, callsignPaint)
        } else {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        // Draw CW ID text
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = 64f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }
        val x = width / 2f
        val y = if (cameraOff) height / 2f + 100f else height / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)
        return bitmap
    }

    private fun sendMorseId(
        callsign: String,
        wpm: Int,
        imageFilter: ImageObjectFilterRender?,
        camera: RtmpCamera2?,
        cameraOff: Boolean = false
    ) {
        val morsePlayer = MorseCodePlayer()
        val charBuffers = morsePlayer.generateCharacterBuffers(callsign, wpm)
        if (charBuffers.isEmpty()) return

        val streamW = camera?.getStreamWidth() ?: 1280
        val streamH = camera?.getStreamHeight() ?: 720
        val accumulated = StringBuilder()

        morsePlayer.playCharacterBuffers(charBuffers) { char ->
            accumulated.append(char)
            _cwIdDisplayText.value = accumulated.toString()
            try {
                imageFilter?.setImage(renderCWIDText(accumulated.toString(), streamW, streamH, cameraOff, callsign))
            } catch (_: Exception) {}
        }

        _cwIdDisplayText.value = ""
    }

    // --- ConnectChecker Callbacks ---

    override fun onConnectionStarted(url: String) {
        _connectionStatus.value = ConnectionStatus(ConnectionState.CONNECTING)
    }

    override fun onConnectionSuccess() {
        _connectionStatus.value = ConnectionStatus(ConnectionState.STREAMING)
        // Apply appropriate filter: slideshow takes priority over camera-off
        if (_slideshowActive.value) {
            applySlideshowFilter()
        } else if (!_cameraEnabled.value) {
            applyCameraOffFilter(lastCallsign)
        }
    }

    override fun onConnectionFailed(reason: String) {
        _connectionStatus.value = ConnectionStatus(
            ConnectionState.IDLE,
            errorMessage = "Connection failed: $reason"
        )
    }

    override fun onDisconnect() {
        _connectionStatus.value = ConnectionStatus(ConnectionState.IDLE)
    }

    override fun onAuthError() {
        _connectionStatus.value = ConnectionStatus(
            ConnectionState.IDLE,
            errorMessage = "Authentication failed"
        )
    }

    override fun onAuthSuccess() {}

    override fun onNewBitrate(bitrate: Long) {}

    fun release() {
        slideshowJob?.cancel()
        scope.cancel()
        rtmpCamera?.stopStream()
        rtmpCamera?.stopPreview()
    }
}
