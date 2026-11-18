package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.local.AiEngineMode
import com.example.data.local.UserSettings
import com.example.data.remote.GoogleFitRestService
import com.example.domain.DateTimeUtils
import com.example.ui.HealthSyncUiState
import com.example.ui.components.GeminiSyncStatusBanner
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.rememberPressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    healthSyncState: HealthSyncUiState,
    feedbackMessage: String?,
    onClearFeedback: () -> Unit,
    onSaveBedtime: (Int) -> Unit,
    onSaveAiSettings: (apiKey: String, modelId: String, endpointUrl: String, mode: AiEngineMode) -> Unit,
    onSaveGoogleClientId: (String) -> Unit,
    onConnectGoogleFit: () -> Unit,
    onSyncGoogleFitNow: () -> Unit,
    onDisconnectGoogleFit: () -> Unit,
    onExportJsonBackup: (android.content.Context, android.net.Uri) -> Unit,
    onImportJsonBackup: (android.content.Context, android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var showAdvancedGoogleClient by remember { mutableStateOf(false) }

    var apiKeyInput by remember(settings.nvidiaApiKey) { mutableStateOf(settings.nvidiaApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelIdInput by remember(settings.nvidiaModelId) { mutableStateOf(settings.nvidiaModelId) }
    var endpointInput by remember(settings.nvidiaEndpointUrl) { mutableStateOf(settings.nvidiaEndpointUrl) }
    var selectedMode by remember(settings.aiEngineMode) { mutableStateOf(settings.aiEngineMode) }
    var googleClientIdInput by remember(settings.googleClientId) { mutableStateOf(settings.googleClientId) }

    var showBedtimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            kotlinx.coroutines.delay(3500L)
            onClearFeedback()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) onExportJsonBackup(context, uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportJsonBackup(context, uri)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "settings_header") {
            Column {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure target bedtime, NVIDIA NIM & Gemini AI, Google OAuth, and backups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "settings_feedback") {
            AnimatedVisibility(
                visible = feedbackMessage != null,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
            ) {
                feedbackMessage?.let { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Target Bedtime Card
        item(key = "bedtime_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Target bedtime",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Target Bedtime",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Clearance & Disruption Score anchor: ${DateTimeUtils.formatMinutesOfDay(settings.targetBedtimeMinutes)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = { showBedtimePicker = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("edit_bedtime_button")
                    ) {
                        Text("Change")
                    }
                }
            }
        }

        // NVIDIA NIM & Built-in Gemini Settings Card
        item(key = "ai_settings_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "NVIDIA NIM & Gemini AI Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "AI Engine Mode",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        AiEngineMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("ai_mode_${mode.name}")
                            )
                        }
                        AnimatedContent(
                            targetState = selectedMode.description,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "aiModeDescription"
                        ) { desc ->
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("NVIDIA API Key (nvapi-...)") },
                        placeholder = { Text("Optional — leave blank to use built-in Gemini") },
                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(
                                onClick = { showApiKey = !showApiKey },
                                modifier = Modifier.testTag("toggle_nvidia_key_visibility")
                            ) {
                                Icon(
                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showApiKey) "Hide API key" else "Show API key"
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nvidia_api_key_input")
                    )

                    OutlinedTextField(
                        value = modelIdInput,
                        onValueChange = { modelIdInput = it },
                        label = { Text("NVIDIA Model ID") },
                        placeholder = { Text(UserSettings.DEFAULT_NVIDIA_MODEL) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nvidia_model_id_input")
                    )

                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("NVIDIA NIM Endpoint (HTTPS)") },
                        placeholder = { Text(UserSettings.DEFAULT_NVIDIA_ENDPOINT) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nvidia_endpoint_input")
                    )

                    val (saveAiInteraction, saveAiScale) = rememberPressScale()
                    Button(
                        onClick = {
                            onSaveAiSettings(apiKeyInput, modelIdInput, endpointInput, selectedMode)
                        },
                        interactionSource = saveAiInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .graphicsLayer {
                                scaleX = saveAiScale
                                scaleY = saveAiScale
                            }
                            .testTag("save_ai_settings_button")
                    ) {
                        Text("Save AI Configuration")
                    }
                }
            }
        }

        // Google Account & Fit Sync Card
        item(key = "google_oauth_settings_card") {
            val connected = settings.isGoogleConnected
            val hasOAuthToken = settings.hasValidOAuthToken

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (connected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (connected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Google Account & Fit Sync",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when {
                                    hasOAuthToken -> "Signed in · Google Fit Sleep & HR sync active"
                                    connected -> "Signed in with Google"
                                    else -> "Opens an in-app Google sign-in page"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = healthSyncState.isWorking,
                        enter = expandVertically(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(tween(200)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        GeminiSyncStatusBanner(message = "Syncing Google Fit sleep & HR data...")
                    }

                    AnimatedVisibility(
                        visible = healthSyncState.statusMessage != null,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140))
                    ) {
                        healthSyncState.statusMessage?.let { status ->
                            Text(
                                text = status,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = healthSyncState.errorMessage != null,
                        enter = fadeIn(tween(180)),
                        exit = fadeOut(tween(140))
                    ) {
                        healthSyncState.errorMessage?.let { err ->
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!connected) {
                        GoogleSignInButton(
                            onClick = onConnectGoogleFit,
                            enabled = !healthSyncState.isWorking
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val (settingsSyncInteraction, settingsSyncScale) = rememberPressScale()
                            Button(
                                onClick = onSyncGoogleFitNow,
                                enabled = !healthSyncState.isWorking,
                                interactionSource = settingsSyncInteraction,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer {
                                        scaleX = settingsSyncScale
                                        scaleY = settingsSyncScale
                                    }
                                    .testTag("settings_sync_google_fit_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Now")
                            }
                            OutlinedButton(
                                onClick = onDisconnectGoogleFit,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("settings_disconnect_google_fit_button")
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = "Sign out of Google")
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }

                    TextButton(
                        onClick = { showAdvancedGoogleClient = !showAdvancedGoogleClient },
                        modifier = Modifier.testTag("toggle_advanced_google_client")
                    ) {
                        Icon(
                            imageVector = if (showAdvancedGoogleClient) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Advanced OAuth Client ID (Optional)")
                    }

                    AnimatedVisibility(visible = showAdvancedGoogleClient) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Redirect URI: ${GoogleFitRestService.REDIRECT_URI}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            OutlinedTextField(
                                value = googleClientIdInput,
                                onValueChange = { googleClientIdInput = it },
                                label = { Text("Custom Google Fit OAuth Client ID") },
                                placeholder = { Text("Optional — for Fitness REST API scopes") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("google_client_id_input")
                            )
                            FilledTonalButton(
                                onClick = { onSaveGoogleClientId(googleClientIdInput) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("save_google_client_id_button")
                            ) {
                                Text("Save Custom Client ID")
                            }
                        }
                    }
                }
            }
        }

        // Export / Import JSON History Card
        item(key = "backup_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Log History Portability (JSON)",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Export all multi-day substance logs and sleep sessions as a portable JSON file or restore from a previous backup.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                exportLauncher.launch("circadia_backup_${DateTimeUtils.todayIso()}.json")
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("export_json_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("import_json_button")
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }
        }
    }

    if (showBedtimePicker) {
        val initHour = (settings.targetBedtimeMinutes / 60).coerceIn(0, 23)
        val initMin = (settings.targetBedtimeMinutes % 60).coerceIn(0, 59)
        val pickerState = rememberTimePickerState(
            initialHour = initHour,
            initialMinute = initMin,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showBedtimePicker = false },
            title = { Text("Set Target Bedtime") },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveBedtime(pickerState.hour * 60 + pickerState.minute)
                        showBedtimePicker = false
                    },
                    modifier = Modifier.testTag("confirm_bedtime_picker")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBedtimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
