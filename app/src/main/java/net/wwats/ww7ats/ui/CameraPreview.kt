package net.wwats.ww7ats.ui

import android.content.res.Configuration
import android.view.SurfaceHolder
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.pedro.library.view.OpenGlView
import net.wwats.ww7ats.media.StreamingManager

@Composable
fun CameraPreview(
    streamingManager: StreamingManager,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val orientation = configuration.orientation

    // Restart preview when orientation changes so camera rotation updates
    LaunchedEffect(orientation) {
        streamingManager.restartPreview()
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            OpenGlView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        streamingManager.initCamera(this@apply)
                        streamingManager.startPreview()
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {}

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        streamingManager.stopPreview()
                    }
                })
            }
        }
    )
}
