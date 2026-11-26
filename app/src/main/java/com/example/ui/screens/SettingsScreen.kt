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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.data.remote.GoogleCredentialAuthHelper
import com.example.data.remote.GoogleFitRestService
import com.example.data.repository.DatabaseStats
import com.example.domain.DateTimeUtils
import com.example.ui.CloudAuthState
import com.example.ui.HealthSyncUiState
import com.example.ui.components.GeminiLoaderSize
import com.example.ui.components.GeminiSyncStatusBanner
import com.example.ui.components.GeminiThreeDotsLoader
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.rememberPressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    databaseStats: DatabaseStats,
    diskDatabaseStats: DatabaseStats,
    cloudAuthState: CloudAuthState,
    isPersistenceBusy: Boolean,
    cloudSyncWorking: Boolean,
    showDisablePersistenceDialog: Boolean,
    healthSyncState: HealthSyncUiState,
    feedbackMessage: String?,
    onClearFeedback: () -> Unit,
    onTogglePersistentDatabase: (Boolean) -> Unit,
    onDismissDisablePersistenceDialog: () -> Unit,
    onConfirmDisablePersistence: (clearSavedData: Boolean, clearCloudData: Boolean) -> Unit,
    onToggleCloudSync: (Boolean) -> Unit,
    onSyncCloudNow: () -> Unit,
    onClearSavedDatabase: (clearCloudData: Boolean) -> Unit,
    onFirebaseGoogleSignedIn: (String) -> Unit,
    onFirebaseAuthMessage: (String) -> Unit,
    onFirebaseSignedOut: () -> Unit,
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
    val coroutineScope = rememberCoroutineScope()

    var showAdvancedGoogleClient by remember { mutableStateOf(false) }

    var apiKeyInput by remember(settings.nvidiaApiKey) { mutableStateOf(settings.nvidiaApiKey) }
    var showApiKey by remember { mutableStateOf(false) }
    var modelIdInput by remember(settings.nvidiaModelId) { mutableStateOf(settings.nvidiaModelId) }
    var endpointInput by remember(settings.nvidiaEndpointUrl) { mutableStateOf(settings.nvidiaEndpointUrl) }
    var selectedMode by remember(settings.aiEngineMode) { mutableStateOf(settings.aiEngineMode) }
    var googleClientIdInput by remember(settings.googleClientId) { mutableStateOf(settings.googleClientId) }

    var showBedtimePicker by remember { mutableStateOf(false) }
    var isSigningInCloud by remember { mutableStateOf(false) }
    var showClearSavedDbDialog by remember { mutableStateOf(false) }

    LaunchedEffect(feedbackMessage) {
        if (feedbackMessage != null) {
            kotlinx.coroutines.delay(4000L)
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
                    text = "Configure database storage, target bedtime, AI engines, Google OAuth, and backups",
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

        // Database & Persistence Card (Opt-in, Session-Only by default)
        item(key = "database_persistence_card") {
            val totalDiskRecords = diskDatabaseStats.logCount +
                diskDatabaseStats.sleepSessionCount +
                diskDatabaseStats.reportCount

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(
                    1.dp,
                    if (settings.persistentDbEnabled) {
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("database_persistence_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (settings.persistentDbEnabled) {
                                    Icons.Default.Storage
                                } else {
                                    Icons.Default.Memory
                                },
                                contentDescription = null,
                                tint = if (settings.persistentDbEnabled) {
                                    MaterialTheme.colorScheme.secondary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Database & Persistence",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (settings.persistentDbEnabled) {
                                        "Persistent SQLite Active"
                                    } else {
                                        "Session-Only Mode (Default)"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (settings.persistentDbEnabled) {
                                        MaterialTheme.colorScheme.secondary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Switch(
                            checked = settings.persistentDbEnabled,
                            onCheckedChange = onTogglePersistentDatabase,
                            enabled = !isPersistenceBusy,
                            modifier = Modifier.testTag("toggle_persistent_database")
                        )
                    }

                    Text(
                        text = if (settings.persistentDbEnabled) {
                            "Logs, imported sleep sessions, and AI impact reports are saved on-device across restarts. " +
                                "Active records: ${databaseStats.logCount} logs · ${databaseStats.sleepSessionCount} sleep · ${databaseStats.reportCount} reports."
                        } else {
                            "Storage is off by default: entries live only in memory for this session. " +
                                "Turn this on to migrate current session logs (${databaseStats.logCount} logs, ${databaseStats.sleepSessionCount} sleep) to an on-device SQLite database."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(visible = !settings.persistentDbEnabled && totalDiskRecords > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$totalDiskRecords saved record(s) preserved on disk from an earlier session.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { showClearSavedDbDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("clear_preserved_disk_data_button")
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = isPersistenceBusy) {
                        GeminiSyncStatusBanner(message = "Updating database storage mode...")
                    }

                    AnimatedVisibility(
                        visible = settings.persistentDbEnabled,
                        enter = expandVertically(
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                        ) + fadeIn(tween(220)),
                        exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = null,
                                        tint = if (settings.cloudSyncEnabled) {
                                            MaterialTheme.colorScheme.secondary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Column {
                                        Text(
                                            text = "Optional Cloud Database Sync",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (cloudAuthState.isSignedIn) {
                                                "Account: ${cloudAuthState.accountLabel}"
                                            } else {
                                                "Sign in with Google to sync SQLite records to Cloud Firestore"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = settings.cloudSyncEnabled,
                                    onCheckedChange = onToggleCloudSync,
                                    enabled = cloudAuthState.isSignedIn && !cloudSyncWorking && !isPersistenceBusy,
                                    modifier = Modifier.testTag("toggle_cloud_database_sync")
                                )
                            }

                            AnimatedVisibility(visible = cloudSyncWorking || isSigningInCloud) {
                                GeminiSyncStatusBanner(
                                    message = if (isSigningInCloud) {
                                        "Authenticating Google account..."
                                    } else {
                                        "Syncing database with Cloud Firestore..."
                                    }
                                )
                            }

                            if (!cloudAuthState.isSignedIn) {
                                OutlinedButton(
                                    onClick = {
                                        isSigningInCloud = true
                                        GoogleCredentialAuthHelper.signInWithGoogleInteractive(
                                            context = context,
                                            scope = coroutineScope,
                                            onAuthSuccess = { email ->
                                                isSigningInCloud = false
                                                onFirebaseGoogleSignedIn(email)
                                            },
                                            onAuthCancelled = {
                                                isSigningInCloud = false
                                                onFirebaseAuthMessage("Google Sign-In was cancelled.")
                                            },
                                            onAuthError = { errMsg ->
                                                isSigningInCloud = false
                                                onFirebaseAuthMessage(errMsg)
                                            }
                                        )
                                    },
                                    enabled = !isSigningInCloud && !isPersistenceBusy,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("cloud_sync_google_signin_button")
                                ) {
                                    if (isSigningInCloud) {
                                        GeminiThreeDotsLoader(
                                            size = GeminiLoaderSize.Compact,
                                            description = "Signing in"
                                        )
                                    } else {
                                        Text("Sign in with Google for Cloud Sync")
                                    }
                                }
                            } else {
                                if (settings.lastCloudDbSyncMillis > 0L) {
                                    Text(
                                        text = "Last Cloud DB sync: ${DateTimeUtils.formatTime(settings.lastCloudDbSyncMillis)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    val (cloudSyncInteraction, cloudSyncScale) = rememberPressScale()
                                    FilledTonalButton(
                                        onClick = onSyncCloudNow,
                                        enabled = !cloudSyncWorking && !isPersistenceBusy,
                                        interactionSource = cloudSyncInteraction,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .graphicsLayer {
                                                scaleX = cloudSyncScale
                                                scaleY = cloudSyncScale
                                            }
                                            .testTag("sync_cloud_db_now_button")
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Sync Cloud DB")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            GoogleCredentialAuthHelper.signOutFirebase(
                                                context = context,
                                                scope = coroutineScope,
                                                onSignOutComplete = onFirebaseSignedOut
                                            )
                                        },
                                        enabled = !cloudSyncWorking && !isPersistenceBusy,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("cloud_sync_google_signout_button")
                                    ) {
                                        Text("Sign Out")
                                    }
                                }
                            }

                            OutlinedButton(
                                onClick = { showClearSavedDbDialog = true },
                                enabled = !isPersistenceBusy && !cloudSyncWorking,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("clear_saved_database_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Clear Saved Database")
                            }
                        }
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
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("ai_mode_${mode.name}")
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
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("toggle_advanced_google_client")
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

    if (showDisablePersistenceDialog) {
        var alsoClearCloud by remember(cloudAuthState.isSignedIn) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = onDismissDisablePersistenceDialog,
            title = { Text("Turn Off Persistent Database?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Choose what to do with your saved records (${databaseStats.logCount} logs, " +
                            "${databaseStats.sleepSessionCount} sleep sessions, ${databaseStats.reportCount} AI reports):",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "• Keep Saved Data: pauses disk saving and keeps records available in this session.\n" +
                            "• Clear All Saved Data: deletes saved local records immediately.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (cloudAuthState.isSignedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = alsoClearCloud,
                                onCheckedChange = { alsoClearCloud = it },
                                modifier = Modifier.testTag("checkbox_clear_cloud_on_disable")
                            )
                            Text(
                                text = "Also delete synced Cloud Firestore records when clearing",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { onConfirmDisablePersistence(false, false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("confirm_disable_keep_data")
                    ) {
                        Text("Keep Saved Data")
                    }
                    Button(
                        onClick = { onConfirmDisablePersistence(true, alsoClearCloud) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("confirm_disable_clear_data")
                    ) {
                        Text("Clear All Saved Data")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDisablePersistenceDialog,
                    modifier = Modifier.testTag("cancel_disable_persistence")
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearSavedDbDialog) {
        var alsoClearCloud by remember(cloudAuthState.isSignedIn) { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showClearSavedDbDialog = false },
            title = { Text("Clear Saved Database?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This permanently deletes all saved substance logs, sleep sessions, and AI impact reports from the local SQLite database.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (cloudAuthState.isSignedIn) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Checkbox(
                                checked = alsoClearCloud,
                                onCheckedChange = { alsoClearCloud = it },
                                modifier = Modifier.testTag("checkbox_clear_cloud_direct")
                            )
                            Text(
                                text = "Also delete synced Cloud Firestore records",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearSavedDbDialog = false
                        onClearSavedDatabase(alsoClearCloud)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_clear_saved_database")
                ) {
                    Text("Delete Records")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearSavedDbDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
