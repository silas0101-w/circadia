package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.SubstanceLogEntity
import com.example.data.remote.AI_STUDIO_BILLING_URL
import com.example.data.remote.AiFailureKind
import com.example.ui.AiAnalysisUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiImpactSheet(
    aiState: AiAnalysisUiState,
    isStale: Boolean = false,
    onDismiss: () -> Unit,
    onRetryWithGemini: (SubstanceLogEntity?) -> Unit,
    onRegenerate: (String) -> Unit,
    onOpenSettings: () -> Unit = {}
) {
    if (aiState is AiAnalysisUiState.Idle) return
    val uriHandler = LocalUriHandler.current

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Why & Severity Analysis",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_ai_sheet_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close analysis")
                }
            }

            AnimatedContent(
                targetState = aiState,
                transitionSpec = {
                    (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 10 }) togetherWith
                        fadeOut(tween(150)) using SizeTransform(clip = false)
                },
                contentKey = { state ->
                    when (state) {
                        is AiAnalysisUiState.Loading -> "loading"
                        is AiAnalysisUiState.Error -> "error"
                        is AiAnalysisUiState.Success -> "success_${state.report.scopeKey}_${state.report.createdAtMillis}"
                        AiAnalysisUiState.Idle -> "idle"
                    }
                },
                label = "aiImpactSheetContent"
            ) { state ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (state) {
                        is AiAnalysisUiState.Loading -> {
                            AiGeneratingLoadingCard(targetLabel = state.targetLabel)
                        }

                        is AiAnalysisUiState.Error -> {
                            var showTechnicalDetails by remember(state.message, state.technicalDetails) {
                                mutableStateOf(false)
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Analysis error",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Text(
                                            text = "AI Analysis Failed",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    Text(
                                        text = state.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )

                                    if (!state.technicalDetails.isNullOrBlank()) {
                                        TextButton(
                                            onClick = { showTechnicalDetails = !showTechnicalDetails },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("toggle_ai_error_details")
                                        ) {
                                            Icon(
                                                imageVector = if (showTechnicalDetails) {
                                                    Icons.Default.ExpandLess
                                                } else {
                                                    Icons.Default.ExpandMore
                                                },
                                                contentDescription = if (showTechnicalDetails) {
                                                    "Hide technical details"
                                                } else {
                                                    "Show technical details"
                                                },
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = if (showTechnicalDetails) {
                                                    "Hide Technical Details"
                                                } else {
                                                    "Show Technical Details"
                                                },
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }

                                        AnimatedVisibility(
                                            visible = showTechnicalDetails,
                                            enter = expandVertically(tween(200)) + fadeIn(tween(180)),
                                            exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.25f)
                                                ),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("ai_error_technical_details")
                                            ) {
                                                Text(
                                                    text = state.technicalDetails,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            val isBillingDepleted = state.failureKind == AiFailureKind.BILLING_DEPLETED ||
                                state.billingUrl != null
                            val showSettingsAction = isBillingDepleted ||
                                state.failureKind == AiFailureKind.AUTH_OR_APPCHECK ||
                                state.failureKind == AiFailureKind.CONFIGURATION_REQUIRED

                            if (isBillingDepleted) {
                                val targetBillingUrl = state.billingUrl?.takeIf {
                                    it.startsWith("https://ai.studio/")
                                } ?: AI_STUDIO_BILLING_URL
                                Button(
                                    onClick = {
                                        runCatching { uriHandler.openUri(targetBillingUrl) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("open_ai_billing_button")
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open AI Studio Billing")
                                }
                            }

                            if (showSettingsAction) {
                                FilledTonalButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenSettings()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("open_ai_settings_button")
                                ) {
                                    Icon(Icons.Default.Settings, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open AI Settings (Switch Engine or Key)")
                                }
                            }

                            val (retryInteraction, retryScale) = rememberPressScale()
                            if (isBillingDepleted || state.failureKind == AiFailureKind.CONFIGURATION_REQUIRED) {
                                OutlinedButton(
                                    onClick = { onRetryWithGemini(state.lastEntry) },
                                    interactionSource = retryInteraction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .graphicsLayer {
                                            scaleX = retryScale
                                            scaleY = retryScale
                                        }
                                        .testTag("retry_with_gemini_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry with Built-in Gemini")
                                }
                            } else {
                                Button(
                                    onClick = { onRetryWithGemini(state.lastEntry) },
                                    interactionSource = retryInteraction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .graphicsLayer {
                                            scaleX = retryScale
                                            scaleY = retryScale
                                        }
                                        .testTag("retry_with_gemini_button")
                                ) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Retry with Built-in Gemini")
                                }
                            }
                        }

                        is AiAnalysisUiState.Success -> {
                            val report = state.report
                            val severityColor = colorForSeverityLabel(report.severityRating)

                            AnimatedVisibility(
                                visible = isStale,
                                enter = expandVertically(tween(220)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Update,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = "Log Modified · Analysis Out of Sync",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "Dosage, intake time, or logged items changed after this report was generated.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        val (reanalyzeInteraction, reanalyzeScale) = rememberPressScale()
                                        Button(
                                            onClick = { onRegenerate(report.scopeKey) },
                                            interactionSource = reanalyzeInteraction,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .graphicsLayer {
                                                    scaleX = reanalyzeScale
                                                    scaleY = reanalyzeScale
                                                }
                                                .testTag("reanalyze_stale_report_button")
                                        ) {
                                            Icon(Icons.Default.Refresh, contentDescription = null)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Re-Analyze Updated Log")
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = severityColor.copy(alpha = 0.16f),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = "SEVERITY: ${report.severityRating.uppercase()}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = severityColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                                Text(
                                    text = "${report.providerUsed} · ${report.modelUsed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Biotech,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = "Biological Mechanism",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = report.biologicalMechanism,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Actionable Mitigation Tips",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    report.mitigationTips.forEach { tip ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircleOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .size(18.dp)
                                            )
                                            Text(
                                                text = tip,
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }

                            if (!isStale) {
                                val (regenInteraction, regenScale) = rememberPressScale()
                                OutlinedButton(
                                    onClick = { onRegenerate(report.scopeKey) },
                                    interactionSource = regenInteraction,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .graphicsLayer {
                                            scaleX = regenScale
                                            scaleY = regenScale
                                        }
                                        .testTag("regenerate_ai_analysis_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Re-run Analysis")
                                }
                            }
                        }

                        AiAnalysisUiState.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun AiGeneratingLoadingCard(targetLabel: String) {
    val pulseTransition = rememberInfiniteTransition(label = "aiGeneratingStatusPulse")
    val statusAlpha by pulseTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "statusAlpha"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(28.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
            tonalElevation = 2.dp
        ) {
            GeminiThreeDotsLoader(
                size = GeminiLoaderSize.Hero,
                description = "Generating sleep impact analysis",
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = targetLabel,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer { alpha = statusAlpha }
            )
            Text(
                text = "Modeling receptor timing & bedtime clearance",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
