package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.SubstanceLogEntity
import com.example.data.repository.ParsedAiReport
import com.example.domain.DateTimeUtils
import com.example.domain.PharmacokineticsEngine
import com.example.ui.AiAnalysisUiState
import com.example.ui.HistoryUiState
import com.example.ui.UndoEvent
import com.example.ui.UndoSnapshot
import com.example.ui.components.AiImpactSheet
import com.example.ui.components.BindFiveSecondUndoSnackbar
import com.example.ui.components.CircadiaSnackbarHost
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.HistoryTrendChart
import com.example.ui.components.LogSubstanceSheet
import com.example.ui.components.SleepStageBar
import com.example.ui.components.SubstanceEntryCard
import com.example.ui.components.colorForSeverity
import com.example.ui.components.colorForSeverityLabel
import com.example.ui.components.rememberPressScale
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.SeverityCriticalCrimson
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeverityModerateAmber
import com.example.ui.theme.SeveritySevereCoral

@Composable
fun HistoryScreen(
    uiState: HistoryUiState,
    aiState: AiAnalysisUiState,
    undoEvent: UndoEvent?,
    onSelectDate: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
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
    onAnalyzeSelectedDay: (Boolean) -> Unit,
    onAnalyzeEntry: (SubstanceLogEntity, Boolean) -> Unit,
    onShowSavedReport: (ParsedAiReport) -> Unit,
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

    val (fabInteraction, fabScale) = rememberPressScale(pressedScale = 0.92f)
    val (analyzeDayInteraction, analyzeDayScale) = rememberPressScale()
    val (viewReportInteraction, viewReportScale) = rememberPressScale()
    val (emptyAddInteraction, emptyAddScale) = rememberPressScale()

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
                    .testTag("history_add_entry_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add entry to ${DateTimeUtils.formatDisplayDate(uiState.selectedDateIso)}"
                )
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
            item(key = "history_header") {
                Column {
                    Text(
                        text = "History",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Browse, edit & analyze multi-day substance logs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 14-day score trend card
            item(key = "history_trend_card") {
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
                            text = "Disruption Score Comparison",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        HistoryTrendChart(
                            points = uiState.trendPoints,
                            selectedDateIso = uiState.selectedDateIso
                        )
                    }
                }
            }

            // Calendar Grid Card
            item(key = "history_calendar_card") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPreviousMonth,
                                modifier = Modifier.testTag("calendar_prev_month")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Previous month"
                                )
                            }
                            val monthKey = uiState.year * 12 + uiState.monthZeroBased
                            AnimatedContent(
                                targetState = monthKey,
                                transitionSpec = {
                                    if (targetState >= initialState) {
                                        (slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(220))) togetherWith
                                            (slideOutHorizontally(tween(180)) { -it / 4 } + fadeOut(tween(150)))
                                    } else {
                                        (slideInHorizontally(tween(240)) { -it / 4 } + fadeIn(tween(220))) togetherWith
                                            (slideOutHorizontally(tween(180)) { it / 4 } + fadeOut(tween(150)))
                                    }
                                },
                                label = "calendarMonthHeader"
                            ) { encodedMonth ->
                                val y = encodedMonth / 12
                                val m = encodedMonth % 12
                                Text(
                                    text = DateTimeUtils.formatMonthYear(y, m),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = onNextMonth,
                                modifier = Modifier.testTag("calendar_next_month")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "Next month"
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        val weeks = uiState.monthCells.chunked(7)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            weeks.forEach { week ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    for (i in 0 until 7) {
                                        val dateIso = week.getOrNull(i)
                                        if (dateIso == null) {
                                            Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                        } else {
                                            val hasScore = uiState.scoresByDate.containsKey(dateIso)
                                            val score = uiState.scoresByDate[dateIso] ?: 0
                                            val isSelected = dateIso == uiState.selectedDateIso
                                            val dayNum = dateIso.takeLast(2).trimStart('0').ifEmpty { "0" }
                                            val targetDotColor = when {
                                                !hasScore -> Color.Transparent
                                                score < 25 -> SeverityLowEmerald
                                                score < 50 -> SeverityModerateAmber
                                                score < 75 -> SeveritySevereCoral
                                                else -> SeverityCriticalCrimson
                                            }
                                            val cellInteraction = remember { MutableInteractionSource() }
                                            val isCellPressed by cellInteraction.collectIsPressedAsState()
                                            val cellScale by animateFloatAsState(
                                                targetValue = when {
                                                    isCellPressed -> 0.90f
                                                    isSelected -> 1.04f
                                                    else -> 1f
                                                },
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                                    stiffness = Spring.StiffnessMediumLow
                                                ),
                                                label = "calendarCellScale_$dateIso"
                                            )
                                            val cellBgColor by animateColorAsState(
                                                targetValue = if (isSelected) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MidnightSlate800.copy(alpha = 0.45f)
                                                },
                                                animationSpec = tween(220),
                                                label = "calendarCellBg_$dateIso"
                                            )
                                            val cellBorderColor by animateColorAsState(
                                                targetValue = if (isSelected) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    Color.Transparent
                                                },
                                                animationSpec = tween(220),
                                                label = "calendarCellBorder_$dateIso"
                                            )
                                            val cellDotColor by animateColorAsState(
                                                targetValue = targetDotColor,
                                                animationSpec = tween(260),
                                                label = "calendarCellDot_$dateIso"
                                            )
                                            val cellTextColor by animateColorAsState(
                                                targetValue = if (isSelected) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface
                                                },
                                                animationSpec = tween(200),
                                                label = "calendarCellText_$dateIso"
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .graphicsLayer {
                                                        scaleX = cellScale
                                                        scaleY = cellScale
                                                    }
                                                    .clip(MaterialTheme.shapes.small)
                                                    .background(cellBgColor)
                                                    .border(
                                                        width = if (isSelected) 1.5.dp else 0.dp,
                                                        color = cellBorderColor,
                                                        shape = MaterialTheme.shapes.small
                                                    )
                                                    .clickable(
                                                        interactionSource = cellInteraction,
                                                        indication = LocalIndication.current
                                                    ) { onSelectDate(dateIso) }
                                                    .testTag("calendar_day_$dateIso"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                                ) {
                                                    Text(
                                                        text = dayNum,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = cellTextColor
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(cellDotColor)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selected Day Summary + AI Action Bar
            item(key = "selected_day_summary") {
                val tier = PharmacokineticsEngine.severityFromScore(uiState.selectedDisruptionScore)
                val tierColor by animateColorAsState(
                    targetValue = colorForSeverity(tier),
                    animationSpec = tween(320),
                    label = "historySelectedTierColor"
                )
                val animatedScore by animateIntAsState(
                    targetValue = uiState.selectedDisruptionScore,
                    animationSpec = tween(420),
                    label = "historySelectedScore"
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
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
                            AnimatedContent(
                                targetState = uiState.selectedDateIso to uiState.selectedLogs.size,
                                transitionSpec = {
                                    fadeIn(tween(200)) togetherWith fadeOut(tween(140))
                                },
                                label = "selectedDayHeader"
                            ) { (dateIso, count) ->
                                Column {
                                    Text(
                                        text = DateTimeUtils.formatDisplayDate(dateIso),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "$count substance(s) logged",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Surface(
                                color = tierColor.copy(alpha = 0.16f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = "SCORE $animatedScore · ${tier.label.uppercase()}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = tierColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.selectedLogs.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onAnalyzeSelectedDay(false) },
                                    interactionSource = analyzeDayInteraction,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .graphicsLayer {
                                            scaleX = analyzeDayScale
                                            scaleY = analyzeDayScale
                                        }
                                        .testTag("history_analyze_day_button")
                                ) {
                                    Icon(
                                        imageVector = if (uiState.isDayReportStale) Icons.Default.Update else Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(if (uiState.isDayReportStale) "Re-Analyze Day" else "Analyze Day")
                                }

                                uiState.selectedDayReport?.let { savedReport ->
                                    FilledTonalButton(
                                        onClick = { onShowSavedReport(savedReport) },
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
                                            .testTag("history_view_day_report_button")
                                    ) {
                                        Text(if (uiState.isDayReportStale) "Outdated" else "View Report")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Synced Sleep Session for Selected Date (if any)
            uiState.selectedSleepSession?.let { sleep ->
                item(key = "selected_day_sleep") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
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
                                    Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "Recorded Sleep (${sleep.source})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Duration: ${DateTimeUtils.formatDurationMinutes(sleep.totalDurationMinutes)} · " +
                                    "REM ${sleep.remMinutes}m · Deep ${sleep.deepMinutes}m" +
                                    (sleep.restingHeartRateBpm?.let { " · RHR $it bpm" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SleepStageBar(
                                deepMinutes = sleep.deepMinutes,
                                remMinutes = sleep.remMinutes,
                                lightMinutes = sleep.lightMinutes,
                                awakeMinutes = sleep.awakeMinutes
                            )
                        }
                    }
                }
            }

            // Saved AI Report Card for Selected Date (if any)
            uiState.selectedDayReport?.let { report ->
                item(key = "selected_day_ai") {
                    val color by animateColorAsState(
                        targetValue = colorForSeverityLabel(report.severityRating),
                        animationSpec = tween(260),
                        label = "historySavedReportColor"
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = if (uiState.isDayReportStale) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                        } else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                            .clickable { onShowSavedReport(report) }
                            .testTag("history_saved_ai_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved AI Analysis (${report.severityRating}) · ${report.providerUsed}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = color,
                                    modifier = Modifier.weight(1f)
                                )
                                AnimatedVisibility(visible = uiState.isDayReportStale) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "Edited · Stale",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = report.biologicalMechanism,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (uiState.selectedLogs.isEmpty()) {
                item(key = "history_empty_day") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "No substances logged on ${DateTimeUtils.formatDisplayDate(uiState.selectedDateIso)}.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            FilledTonalButton(
                                onClick = {
                                    editingEntry = null
                                    showLogSheet = true
                                },
                                interactionSource = emptyAddInteraction,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .graphicsLayer {
                                        scaleX = emptyAddScale
                                        scaleY = emptyAddScale
                                    }
                                    .testTag("history_empty_add_entry_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Log Substance for ${DateTimeUtils.formatDisplayDate(uiState.selectedDateIso)}")
                            }
                        }
                    }
                }
            } else {
                items(uiState.selectedSummary.entryInfos, key = { it.entry.id }) { info ->
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

            item(key = "history_bottom_fab_spacer") {
                Spacer(Modifier.height(76.dp))
            }
        }
    }

    if (showLogSheet) {
        LogSubstanceSheet(
            initialDateIso = editingEntry?.dateIso ?: uiState.selectedDateIso,
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
                if (targetDate != uiState.selectedDateIso) {
                    onSelectDate(targetDate)
                }
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
                onAnalyzeSelectedDay(true)
            }
        },
        onRegenerate = { scopeKey ->
            if (scopeKey.startsWith("entry:")) {
                val id = scopeKey.removePrefix("entry:").toLongOrNull()
                val match = uiState.selectedLogs.find { it.id == id }
                if (match != null) onAnalyzeEntry(match, false)
            } else {
                onAnalyzeSelectedDay(false)
            }
        }
    )
}
