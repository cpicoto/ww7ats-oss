package net.wwats.ww7ats.ui

import android.content.res.Configuration
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.wwats.ww7ats.data.SettingsManager
import net.wwats.ww7ats.model.ConnectionState
import net.wwats.ww7ats.model.ConnectionStatus
import net.wwats.ww7ats.viewmodel.MainViewModel

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val cameraEnabled by viewModel.cameraEnabled.collectAsState()
    val isFrontCamera by viewModel.isFrontCamera.collectAsState()
    val cwIdDisplayText by viewModel.cwIdDisplayText.collectAsState()
    val slideshowActive by viewModel.slideshowActive.collectAsState()
    val slideshowIndex by viewModel.slideshowIndex.collectAsState()
    val slideshowCount by viewModel.slideshowCount.collectAsState()
    val currentSlideBitmap by viewModel.currentSlideBitmap.collectAsState()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Camera always outputs 16:9; PiP box matches that aspect ratio
    val pipWidth = if (isLandscape) 160.dp else 120.dp
    val pipHeight = if (isLandscape) 90.dp else 68.dp

    // Notify StreamingManager of orientation changes
    LaunchedEffect(isLandscape) {
        viewModel.streamingManager.updateOrientation(!isLandscape)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.startSlideshow(uris)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Stream player + Camera PiP
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // HLS Stream Player
                HlsPlayerView(
                    url = SettingsManager.DEFAULT_HLS_URL,
                    isMuted = connectionStatus.isStreaming,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                )

                // Camera PiP overlay (bottom-right)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Camera preview
                    Box(
                        modifier = Modifier
                            .size(pipWidth, pipHeight)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                2.dp,
                                if (connectionStatus.isStreaming) Color.Red
                                else Color.White.copy(alpha = 0.5f),
                                RoundedCornerShape(8.dp)
                            )
                            .shadow(4.dp, RoundedCornerShape(8.dp))
                    ) {
                        // Always keep CameraPreview so GL pipeline stays active for filters
                        // During slideshow, the GL filter composites slide + camera PiP cutout
                        CameraPreview(
                            streamingManager = viewModel.streamingManager,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!cameraEnabled && !slideshowActive) {
                            // Camera off overlay on PiP (stream uses GL filter instead)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VideocamOff,
                                        contentDescription = null,
                                        tint = Color.Gray
                                    )
                                    Text(
                                        text = settings.callsign.ifBlank { "NO CALLSIGN" },
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // CW ID is rendered via GL filter on the stream/preview,
                        // no need for a separate Compose overlay.
                    }

                    // Camera control buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Toggle camera
                        SmallFloatingActionButton(
                            onClick = { viewModel.toggleCamera(settings.callsign) },
                            containerColor = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                if (cameraEnabled) Icons.Default.Videocam
                                else Icons.Default.VideocamOff,
                                contentDescription = "Toggle camera",
                                tint = if (cameraEnabled) Color.White else Color(0xFFFF9800)
                            )
                        }

                        // Switch camera
                        if (viewModel.streamingManager.hasMultipleCameras) {
                            SmallFloatingActionButton(
                                onClick = { viewModel.switchCamera() },
                                containerColor = Color.White.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Cameraswitch,
                                    contentDescription = "Switch camera",
                                    tint = Color.White
                                )
                            }
                        }

                        // Slideshow photo picker
                        SmallFloatingActionButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            containerColor = Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = "Slideshow",
                                tint = if (slideshowActive) Color.Yellow else Color.White
                            )
                        }
                    }

                    // Slideshow indicator
                    if (slideshowActive) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.clickable { viewModel.stopSlideshow() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${slideshowIndex + 1}/$slideshowCount",
                                    color = Color.Yellow,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }

            // Bottom controls
            BottomControls(
                connectionStatus = connectionStatus,
                callsign = settings.callsign,
                onToggleStreaming = { viewModel.toggleStreaming() },
                onOpenSettings = onOpenSettings,
                permissionsGranted = true // Handled at top level
            )
        }
    }
}

@Composable
private fun BottomControls(
    connectionStatus: ConnectionStatus,
    callsign: String,
    onToggleStreaming: () -> Unit,
    onOpenSettings: () -> Unit,
    permissionsGranted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Callsign
        if (callsign.isNotBlank()) {
            Text(
                text = callsign,
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        // Connection state chip
        ConnectionStateChip(connectionStatus)

        // Main controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings button
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }

            // PTT Button
            PttButton(
                isStreaming = connectionStatus.isStreaming,
                enabled = permissionsGranted,
                onClick = onToggleStreaming
            )

            // Status indicator
            StatusIndicator(connectionStatus)
        }

        // Version
        Text(
            text = "v${net.wwats.ww7ats.BuildConfig.VERSION_NAME} (${net.wwats.ww7ats.BuildConfig.VERSION_CODE})",
            color = Color.Gray.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ConnectionStateChip(status: ConnectionStatus) {
    when {
        status.state == ConnectionState.CONNECTING -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Yellow.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = Color.Yellow
                    )
                    Text(
                        text = "Connecting…",
                        color = Color.Yellow,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
        status.isStreaming -> {
            Text(
                text = "● ON AIR",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        status.isFailed -> {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xFFFF9800).copy(alpha = 0.15f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = status.errorMessage ?: "Error",
                        color = Color(0xFFFF9800),
                        fontSize = 11.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun PttButton(
    isStreaming: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isStreaming) Color.Red else Color(0xFF4CAF50)
    val shadowColor = if (isStreaming) Color.Red else Color(0xFF4CAF50)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .shadow(16.dp, CircleShape, ambientColor = shadowColor, spotColor = shadowColor),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            disabledContainerColor = bgColor.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues(0.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Icon(
                if (isStreaming) Icons.Default.CellTower else Icons.Default.Mic,
                contentDescription = if (isStreaming) "Stop streaming" else "Start streaming",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "PTT",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun StatusIndicator(status: ConnectionStatus) {
    Column(
        modifier = Modifier.size(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val (color, label) = when {
            status.isStreaming -> Color.Green to "LIVE"
            status.state == ConnectionState.CONNECTING -> Color.Yellow to "CONN"
            status.isFailed -> Color.Red to "FAIL"
            else -> Color.Gray.copy(alpha = 0.4f) to "IDLE"
        }
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}
