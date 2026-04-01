package net.wwats.ww7ats.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.wwats.ww7ats.data.Settings
import net.wwats.ww7ats.model.StreamQuality
import net.wwats.ww7ats.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    var streamKeyVisible by remember { mutableStateOf(false) }

    // Local editing states
    var callsign by remember(settings.callsign) { mutableStateOf(settings.callsign) }
    var streamKey by remember(settings.streamKey) { mutableStateOf(settings.streamKey) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    TextButton(onClick = onDismiss) {
                        Text("Done")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Operator Section
            SettingsSection(title = "Operator") {
                OutlinedTextField(
                    value = callsign,
                    onValueChange = {
                        callsign = it.uppercase()
                        viewModel.updateCallsign(callsign)
                    },
                    label = { Text("Callsign") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                )
            }

            // RTMP Configuration
            SettingsSection(title = "RTMP Configuration") {
                OutlinedTextField(
                    value = streamKey,
                    onValueChange = {
                        streamKey = it
                        viewModel.updateStreamKey(it)
                    },
                    label = { Text("Stream Key") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (streamKeyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { streamKeyVisible = !streamKeyVisible }) {
                            Icon(
                                if (streamKeyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = "Toggle visibility"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    )
                )
            }

            // Stream Quality
            SettingsSection(title = "Stream Quality") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StreamQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = settings.streamQuality == quality,
                            onClick = { viewModel.updateStreamQuality(quality) },
                            label = { Text(quality.label, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Morse CW ID
            SettingsSection(title = "Morse CW ID") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Send CW ID on sign-off")
                    Switch(
                        checked = settings.morseCWIDEnabled,
                        onCheckedChange = { viewModel.updateMorseCWIDEnabled(it) }
                    )
                }

                if (settings.morseCWIDEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Speed: ${settings.morseWPM} WPM")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    if (settings.morseWPM > 10) {
                                        viewModel.updateMorseWPM(settings.morseWPM - 1)
                                    }
                                },
                                enabled = settings.morseWPM > 10
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                            }
                            IconButton(
                                onClick = {
                                    if (settings.morseWPM < 40) {
                                        viewModel.updateMorseWPM(settings.morseWPM + 1)
                                    }
                                },
                                enabled = settings.morseWPM < 40
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase")
                            }
                        }
                    }
                }
            }

            // Info
            SettingsSection(title = "Info") {
                Text(

                    text = "v${net.wwats.ww7ats.BuildConfig.VERSION_NAME} (${net.wwats.ww7ats.BuildConfig.VERSION_CODE})",
                    color = Color.Gray.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        content()
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))
    }
}
