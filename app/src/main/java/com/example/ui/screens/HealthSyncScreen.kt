package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.DateTimeUtils
import com.example.domain.PharmacokineticsEngine
import com.example.ui.HealthSyncUiState
import com.example.ui.SleepCorrelationItem
import com.example.ui.components.GeminiSyncStatusBanner
import com.example.ui.components.GoogleSignInButton
import com.example.ui.components.SleepStageBar
import com.example.ui.components.colorForSeverity
import com.example.ui.components.rememberPressScale
import com.example.ui.theme.StageAwakeAmber
import com.example.ui.theme.StageDeepTeal
import com.example.ui.theme.StageLightSlate
import com.example.ui.theme.StageRemIndigo

@Composable
fun HealthSyncScreen(
    uiState: HealthSyncUiState,
    onConnectGoogleFit: () -> Unit,
    onSyncGoogleFitNow: () -> Unit,
    onDisconnectGoogleFit: () -> Unit,
    onImportHealthFile: (android.content.Context, android.net.Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportHealthFile(context, uri)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "health_header") {
            Column {
                Text(
                    text = "Health Sync",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Correlate Google Fit REST & Samsung Health sleep architecture with daily stimulants",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item(key = "health_progress") {
            AnimatedVisibility(
                visible = uiState.isWorking,
                enter = expandVertically(
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
            ) {
                GeminiSyncStatusBanner(message = "Syncing health & sleep sessions...")
            }
        }

        item(key = "health_status") {
            AnimatedVisibility(
                visible = uiState.statusMessage != null,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
            ) {
                uiState.statusMessage?.let { msg ->
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

        item(key = "health_error") {
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140))
            ) {
                uiState.errorMessage?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        // Google Sign-In & Fit Sync Card
        item(key = "google_fit_oauth_card") {
            val connected = uiState.settings.isGoogleConnected
            val hasOAuthToken = uiState.settings.hasValidOAuthToken

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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
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
                                        else -> "Sign in with Google in-app to connect your account"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!connected) {
                        GoogleSignInButton(
                            onClick = onConnectGoogleFit,
                            enabled = !uiState.isWorking
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val (syncInteraction, syncScale) = rememberPressScale()
                            Button(
                                onClick = onSyncGoogleFitNow,
                                enabled = !uiState.isWorking,
                                interactionSource = syncInteraction,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer {
                                        scaleX = syncScale
                                        scaleY = syncScale
                                    }
                                    .testTag("sync_google_fit_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Sync Now")
                            }
                            OutlinedButton(
                                onClick = onDisconnectGoogleFit,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("disconnect_google_fit_button")
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = "Sign out of Google")
                                Spacer(Modifier.width(6.dp))
                                Text("Sign Out")
                            }
                        }
                    }
                }
            }
        }

        // Samsung Health & Health Connect CSV/JSON Importer Card
        item(key = "csv_json_importer_card") {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Samsung Health & Health Connect Importer",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Import exported Samsung Health CSV logs or Google Takeout / Health Connect JSON sleep files via Android's privacy-preserving file picker.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val (importInteraction, importScale) = rememberPressScale()
                    FilledTonalButton(
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/json",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        enabled = !uiState.isWorking,
                        interactionSource = importInteraction,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .graphicsLayer {
                                scaleX = importScale
                                scaleY = importScale
                            }
                            .testTag("import_health_file_button")
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Samsung CSV / Health JSON")
                    }
                }
            }
        }

        // Sleep Architecture Correlation Feed
        item(key = "correlation_header") {
            Text(
                text = "Sleep Architecture vs. Intake Correlation",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (uiState.correlations.isEmpty()) {
            item(key = "correlation_empty") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(34.dp)
                        )
                        Text(
                            text = "No synced sleep sessions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect Google Fit OAuth or import a Samsung Health / Health Connect export to compare actual REM, Deep sleep, and resting HR against your substance logs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.correlations, key = { it.sleepSession.dateIso }) { item ->
                CorrelationCard(
                    item = item,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

@Composable
private fun CorrelationCard(
    item: SleepCorrelationItem,
    modifier: Modifier = Modifier
) {
    val s = item.sleepSession
    val total = s.totalDurationMinutes.coerceAtLeast(1)
    val remPct = (s.remMinutes * 100f / total).toInt()
    val deepPct = (s.deepMinutes * 100f / total).toInt()
    val tier = PharmacokineticsEngine.severityFromScore(item.disruptionScore)
    val scoreColor = colorForSeverity(tier)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Sleep ending ${DateTimeUtils.formatDisplayDate(s.dateIso)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${DateTimeUtils.formatDurationMinutes(s.totalDurationMinutes)} · ${s.source}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = scoreColor.copy(alpha = 0.16f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "RISK ${item.disruptionScore}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            SleepStageBar(
                deepMinutes = s.deepMinutes,
                remMinutes = s.remMinutes,
                lightMinutes = s.lightMinutes,
                awakeMinutes = s.awakeMinutes
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StageLegendItem(label = "Deep ${s.deepMinutes}m ($deepPct%)", color = StageDeepTeal)
                StageLegendItem(label = "REM ${s.remMinutes}m ($remPct%)", color = StageRemIndigo)
                StageLegendItem(label = "Light ${s.lightMinutes}m", color = StageLightSlate)
                StageLegendItem(label = "Awake ${s.awakeMinutes}m", color = StageAwakeAmber)
            }

            s.restingHeartRateBpm?.let { bpm ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Resting heart rate",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Resting / Nocturnal HR: $bpm bpm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (item.loggedSubstancesSummary.isNotEmpty()) {
                Text(
                    text = "Prior Intake (${DateTimeUtils.formatDisplayDate(item.eveningDateIso)}): " +
                        item.loggedSubstancesSummary.joinToString(", "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "No substances logged on ${DateTimeUtils.formatDisplayDate(item.eveningDateIso)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StageLegendItem(
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            color = color,
            shape = androidx.compose.foundation.shape.CircleShape,
            modifier = Modifier.size(7.dp)
        ) {}
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
