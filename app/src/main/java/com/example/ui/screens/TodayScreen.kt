package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.SubstanceLogEntity
import com.example.domain.DateTimeUtils
import com.example.ui.AiAnalysisUiState
import com.example.ui.TodayUiState
import com.example.ui.UndoEvent
import com.example.ui.UndoSnapshot
import com.example.ui.components.AiImpactSheet
import com.example.ui.components.BindFiveSecondUndoSnackbar
import com.example.ui.components.CircadiaSnackbarHost
import com.example.ui.components.ClearanceDecayChart
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.DisruptionScoreGauge
import com.example.ui.components.LogSubstanceSheet
import com.example.ui.components.SubstanceEntryCard
import com.example.ui.components.colorForSeverity
import com.example.ui.components.rememberPressScale

@Composable
fun TodayScreen(
    uiState: TodayUiState,
    aiState: AiAnalysisUiState,
    undoEvent: UndoEvent?,
    onSaveOrUpdateEntry: (
        targetDateIso: String,
        substanceId: String,
        substanceName: String,
        category: String,
        dosageAmount: Double,
        dosageUnit: String,
        takenAtMillis: Long,
        halfLifeHours: Double,
        disruptionWeight: Double,
        notes: String,
        existingId: Long?
    ) -> Unit,
    onQuickAdjust: (SubstanceLogEntity, Double, Long) -> Unit,
    onConfirmDeleteEntry: (SubstanceLogEntity) -> Unit,
    onUndo: (UndoSnapshot) -> Unit,
    onConsumeUndoEvent: () -> Unit,
    onAnalyzeDay: (Boolean) -> Unit,
    onAnalyzeEntry: (SubstanceLogEntity, Boolean) -> Unit,
    onShowSavedDayReport: () -> Unit,
    onDismissAiSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLogSheet by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<SubstanceLogEntity?>(null) }
    var pendingDeleteEntry by remember { mutableStateOf<SubstanceLogEntity?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    BindFiveSecondUndoSnackbar(
        undoEvent = undoEvent,
        snackbarHostState = snackbarHostState,
        onUndo = onUndo,
        onConsumeEvent = onConsumeUndoEvent
    )

    val summary = uiState.summary
    val severityColor by animateColorAsState(
        targetValue = colorForSeverity(summary.severityTier),
        animationSpec = tween(durationMillis = 360),
        label = "todaySeverityColor"
    )
    val (fabInteraction, fabScale) = rememberPressScale(pressedScale = 0.92f)
    val (analyzeInteraction, analyzeScale) = rememberPressScale()
    val (viewReportInteraction, viewReportScale) = rememberPressScale()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { CircadiaSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    showLogSheet = true
                },
                interactionSource = fabInteraction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .testTag("log_substance_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log substance")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item(key = "today_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Log",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = DateTimeUtils.formatDisplayDate(uiState.dateIso),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime,
                                contentDescription = "Target bedtime",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Bed ${DateTimeUtils.formatMinutesOfDay(uiState.settings.targetBedtimeMinutes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Hero Disruption Score & Clearance Curve Card
            item(key = "disruption_hero_card") {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            DisruptionScoreGauge(
                                score = summary.disruptionScore,
                                severityTier = summary.severityTier
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sleep Disruption Score",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val heroDescription = when {
                                    summary.entryInfos.isEmpty() ->
                                        "No substances logged yet today. Tap + to model bedtime clearance."
                                    summary.disruptionScore < 25 ->
                                        "Minimal active load projected at bedtime. Sleep architecture looks protected."
                                    summary.disruptionScore < 50 ->
                                        "Moderate residual load at bedtime. Expect slightly longer sleep onset."
                                    summary.disruptionScore < 75 ->
                                        "Elevated bedtime residue. Deep N3 or REM compression likely."
                                    else ->
                                        "Heavy active stimulant/depressant overlap at bedtime. Mitigation strongly advised."
                                }
                                AnimatedContent(
                                    targetState = heroDescription,
                                    transitionSpec = {
                                        fadeIn(tween(220)) togetherWith fadeOut(tween(150))
                                    },
                                    label = "heroDescription"
                                ) { copy ->
                                    Text(
                                        text = copy,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${summary.entryInfos.size} logged today",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = severityColor
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = summary.entryInfos.isNotEmpty(),
                            enter = expandVertically(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ) + fadeIn(tween(240)),
                            exit = shrinkVertically(tween(200)) + fadeOut(tween(160))
                        ) {
                            ClearanceDecayChart(
                                curve = summary.decayCurve,
                                curveStartMillis = summary.curveStartMillis,
                                curveEndMillis = summary.curveEndMillis,
                                bedtimeMillis = summary.bedtimeMillis,
                                nowMillis = uiState.nowMillis
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { onAnalyzeDay(false) },
                                enabled = summary.entryInfos.isNotEmpty(),
                                interactionSource = analyzeInteraction,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer {
                                        scaleX = analyzeScale
                                        scaleY = analyzeScale
                                    }
                                    .testTag("analyze_sleep_impact_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.isDayReportStale) Icons.Default.Update else Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                AnimatedContent(
                                    targetState = uiState.isDayReportStale,
                                    transitionSpec = {
                                        fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                    },
                                    label = "analyzeDayLabel"
                                ) { stale ->
                                    Text(if (stale) "Re-Analyze Impact" else "Analyze Sleep Impact")
                                }
                            }

                            if (uiState.savedDayReport != null) {
                                FilledTonalButton(
                                    onClick = onShowSavedDayReport,
                                    interactionSource = viewReportInteraction,
                                    colors = if (uiState.isDayReportStale) {
                                        ButtonDefaults.filledTonalButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.filledTonalButtonColors()
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .graphicsLayer {
                                            scaleX = viewReportScale
                                            scaleY = viewReportScale
                                        }
                                        .testTag("view_saved_day_report_button")
                                ) {
                                    Text(if (uiState.isDayReportStale) "Outdated" else "View Last")
                                }
                            }
                        }
                    }
                }
            }

            // Timeline section title
            item(key = "timeline_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Intake Timeline & Bedtime Clearance",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (summary.entryInfos.isEmpty()) {
                item(key = "empty_today_state") {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalCafe,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "Your circadian slate is clean",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Log morning coffee, medications, nicotine, or evening drinks to watch real-time half-life decay toward bedtime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(summary.entryInfos, key = { it.entry.id }) { info ->
                    val entryId = info.entry.id
                    SubstanceEntryCard(
                        info = info,
                        isEntryReportStale = uiState.staleEntryIds.contains(entryId),
                        hasEntryReport = uiState.entryReports.containsKey("entry:$entryId"),
                        onQuickAdjust = onQuickAdjust,
                        onAnalyzeClick = { onAnalyzeEntry(info.entry, false) },
                        onEditClick = {
                            editingEntry = info.entry
                            showLogSheet = true
                        },
                        onDeleteClick = {
                            pendingDeleteEntry = info.entry
                        },
                        modifier = Modifier.animateItem()
                    )
                }
            }

            item(key = "bottom_fab_spacer") {
                Spacer(Modifier.height(76.dp))
            }
        }
    }

    if (showLogSheet) {
        LogSubstanceSheet(
            initialDateIso = editingEntry?.dateIso ?: uiState.dateIso,
            existingEntriesByDate = uiState.entriesByDate,
            targetBedtimeMinutes = uiState.settings.targetBedtimeMinutes,
            editingEntry = editingEntry,
            onDismiss = { showLogSheet = false },
            onRequestDelete = { toDelete ->
                showLogSheet = false
                pendingDeleteEntry = toDelete
            },
            onSave = { targetDate, subId, subName, cat, dose, unit, takenAt, halfLife, weight, notes, existingId ->
                onSaveOrUpdateEntry(
                    targetDate,
                    subId,
                    subName,
                    cat,
                    dose,
                    unit,
                    takenAt,
                    halfLife,
                    weight,
                    notes,
                    existingId
                )
                showLogSheet = false
            }
        )
    }

    ConfirmDeleteDialog(
        entry = pendingDeleteEntry,
        onDismiss = { pendingDeleteEntry = null },
        onConfirmDelete = { toDelete ->
            pendingDeleteEntry = null
            onConfirmDeleteEntry(toDelete)
        }
    )

    val isAiSheetStale = when (aiState) {
        is AiAnalysisUiState.Success -> {
            val scope = aiState.report.scopeKey
            when {
                scope.startsWith("day:") -> uiState.isDayReportStale
                scope.startsWith("entry:") -> {
                    val id = scope.removePrefix("entry:").toLongOrNull()
                    id != null && uiState.staleEntryIds.contains(id)
                }
                else -> false
            }
        }
        else -> false
    }

    AiImpactSheet(
        aiState = aiState,
        isStale = isAiSheetStale,
        onDismiss = onDismissAiSheet,
        onRetryWithGemini = { entry ->
            if (entry != null) {
                onAnalyzeEntry(entry, true)
            } else {
                onAnalyzeDay(true)
            }
        },
        onRegenerate = { scopeKey ->
            if (scopeKey.startsWith("entry:")) {
                val id = scopeKey.removePrefix("entry:").toLongOrNull()
                val match = summary.entryInfos.find { it.entry.id == id }?.entry
                if (match != null) onAnalyzeEntry(match, false)
            } else {
                onAnalyzeDay(false)
            }
        }
    )
}
