package net.wwats.ww7ats

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import net.wwats.ww7ats.ui.MainScreen
import net.wwats.ww7ats.ui.SettingsScreen
import net.wwats.ww7ats.ui.theme.WW7ATSTheme
import net.wwats.ww7ats.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Results handled by recomposition
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request permissions
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            )
        )

        setContent {
            WW7ATSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black
                ) {
                    val viewModel: MainViewModel = viewModel()
                    var showSettings by remember { mutableStateOf(false) }

                    if (showSettings) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onDismiss = { showSettings = false }
                        )
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenSettings = { showSettings = true }
                        )
                    }
                }
            }
        }
    }
}
