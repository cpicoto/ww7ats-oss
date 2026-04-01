package net.wwats.ww7ats.media

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import com.pedro.encoder.input.gl.render.filters.BaseFilterRender
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom OpenGL filter that composites a slide image (fullscreen) with the
 * camera feed scaled into a small PiP rectangle. Uses a single-pass fragment
 * shader that checks each fragment's position to decide whether to sample
 * from the slide texture or the camera texture.
 */
class SlideshowFilterRender : BaseFilterRender() {

    private var program = 0
    private var aPositionHandle = 0
    private var aTexCoordHandle = 0
    private var uCameraHandle = 0
    private var uSlideHandle = 0
    private var uPipRectHandle = 0

    private val slideTexId = IntArray(1)
    @Volatile private var pendingBitmap: Bitmap? = null

    /** Callback invoked (once) when GL dimensions become available. */
    var onGlReady: ((width: Int, height: Int) -> Unit)? = null
    private var glReadyFired = false

    /** Public accessors for the actual GL framebuffer dimensions. */
    val glWidth: Int get() = getWidth()
    val glHeight: Int get() = getHeight()

    // PiP rectangle in normalized UV coordinates (0-1)
    private var pipLeft = 0.75f
    private var pipBottom = 0.02f
    private var pipWidth = 0.2f
    private var pipHeight = 0.2f

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D uCamera;
            uniform sampler2D uSlide;
            uniform vec4 uPipRect;

            void main() {
                vec2 pipMin = uPipRect.xy;
                vec2 pipSize = uPipRect.zw;
                vec2 pipMax = pipMin + pipSize;

                if (vTextureCoord.x >= pipMin.x && vTextureCoord.x <= pipMax.x &&
                    vTextureCoord.y >= pipMin.y && vTextureCoord.y <= pipMax.y) {
                    vec2 camCoord = (vTextureCoord - pipMin) / pipSize;
                    gl_FragColor = texture2D(uCamera, camCoord);
                } else {
                    // Flip Y for slide: bitmap origin is top-left, GL origin is bottom-left
                    vec2 slideCoord = vec2(vTextureCoord.x, 1.0 - vTextureCoord.y);
                    gl_FragColor = texture2D(uSlide, slideCoord);
                }
            }
        """
    }

    init {
        // Fullscreen quad: X, Y, Z, U, V (matches BaseRenderOffScreen stride)
        val vertexData = floatArrayOf(
            -1f, -1f, 0f, 0f, 0f,  // bottom-left
             1f, -1f, 0f, 1f, 0f,  // bottom-right
            -1f,  1f, 0f, 0f, 1f,  // top-left
             1f,  1f, 0f, 1f, 1f,  // top-right
        )
        squareVertex = ByteBuffer.allocateDirect(vertexData.size * FLOAT_SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .put(vertexData)
        squareVertex.position(0)
    }

    fun setSlideImage(bitmap: Bitmap) {
        pendingBitmap = bitmap
    }

    /**
     * Set PiP position in normalized UV coordinates (0-1).
     * UV (0,0) = bottom-left, (1,1) = top-right.
     */
    fun setPipRect(left: Float, bottom: Float, w: Float, h: Float) {
        pipLeft = left
        pipBottom = bottom
        pipWidth = w
        pipHeight = h
    }

    override fun initGlFilter(context: Context) {
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uCameraHandle = GLES20.glGetUniformLocation(program, "uCamera")
        uSlideHandle = GLES20.glGetUniformLocation(program, "uSlide")
        uPipRectHandle = GLES20.glGetUniformLocation(program, "uPipRect")

        // Create slide texture
        GLES20.glGenTextures(1, slideTexId, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slideTexId[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        // Initialize with 1x1 black pixel
        val blackPixel = ByteBuffer.allocateDirect(4).apply { put(byteArrayOf(0, 0, 0, -1)); position(0) }
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, blackPixel)

        initFBOLink()
    }

    override fun drawFilter() {
        // Fire onGlReady once the framebuffer dimensions are known
        if (!glReadyFired && getWidth() > 0 && getHeight() > 0) {
            glReadyFired = true
            Log.d("SlideshowFilter", "GL ready: ${getWidth()}x${getHeight()}")
            onGlReady?.invoke(getWidth(), getHeight())
        }

        // Upload pending slide bitmap
        val bmp = pendingBitmap
        if (bmp != null) {
            pendingBitmap = null
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slideTexId[0])
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        }

        GLES20.glUseProgram(program)

        // Position attribute (3 floats at offset 0, stride 20)
        squareVertex.position(SQUARE_VERTEX_DATA_POS_OFFSET)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex)
        GLES20.glEnableVertexAttribArray(aPositionHandle)

        // Texture coordinate attribute (2 floats at offset 3, stride 20)
        squareVertex.position(SQUARE_VERTEX_DATA_UV_OFFSET)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false,
            SQUARE_VERTEX_DATA_STRIDE_BYTES, squareVertex)
        GLES20.glEnableVertexAttribArray(aTexCoordHandle)

        // Bind camera texture (previousTexId) to unit 4
        GLES20.glUniform1i(uCameraHandle, 4)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE4)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, previousTexId)

        // Bind slide texture to unit 5
        GLES20.glUniform1i(uSlideHandle, 5)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE5)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slideTexId[0])

        // Set PiP rectangle
        GLES20.glUniform4f(uPipRectHandle, pipLeft, pipBottom, pipWidth, pipHeight)
    }

    override fun release() {
        GLES20.glDeleteProgram(program)
        GLES20.glDeleteTextures(1, slideTexId, 0)
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }
}
