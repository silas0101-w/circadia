package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.domain.ConcentrationPoint
import com.example.domain.ConcentrationSnapshot
import com.example.domain.CurvePoint
import com.example.domain.DateTimeUtils
import com.example.domain.DayConcentrationChartModel
import com.example.domain.DecayModelMode
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SeverityTier
import com.example.domain.SubstanceConcentrationSeries
import com.example.ui.DailyScorePoint
import com.example.ui.theme.MidnightSlate700
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.MidnightSlate850
import com.example.ui.theme.MidnightSlate900
import com.example.ui.theme.SeverityCriticalCrimson
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeverityModerateAmber
import com.example.ui.theme.SeveritySevereCoral
import com.example.ui.theme.SleepIndigo400
import com.example.ui.theme.SoftTeal300
import com.example.ui.theme.StageAwakeAmber
import com.example.ui.theme.StageDeepTeal
import com.example.ui.theme.StageLightSlate
import com.example.ui.theme.StageRemIndigo
import com.example.ui.theme.WarmAmber400
import java.util.Locale
import kotlin.math.roundToInt

fun colorForSeverity(tier: SeverityTier): Color = when (tier) {
    SeverityTier.LOW -> SeverityLowEmerald
    SeverityTier.MODERATE -> SeverityModerateAmber
    SeverityTier.SEVERE -> SeveritySevereCoral
    SeverityTier.CRITICAL -> SeverityCriticalCrimson
}

fun colorForSeverityLabel(label: String): Color = when (label.lowercase()) {
    "low" -> SeverityLowEmerald
    "moderate" -> SeverityModerateAmber
    "severe" -> SeveritySevereCoral
    "critical" -> SeverityCriticalCrimson
    else -> WarmAmber400
}

@Composable
fun DisruptionScoreGauge(
    score: Int,
    severityTier: SeverityTier,
    modifier: Modifier = Modifier
) {
    val clampedScore = score.coerceIn(0, 100)
    val animatedScore by animateIntAsState(
        targetValue = clampedScore,
        animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
        label = "scoreCounter"
    )
    val targetSweep = (clampedScore / 100f) * 240f
    val animatedSweep by animateFloatAsState(
        targetValue = targetSweep,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessLow
        ),
        label = "scoreGauge"
    )
    val accentColor by animateColorAsState(
        targetValue = colorForSeverity(severityTier),
        animationSpec = tween(durationMillis = 350),
        label = "scoreAccentColor"
    )

    Box(
        modifier = modifier.size(164.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val strokeWidth = 14.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            // Background track
            drawArc(
                color = MidnightSlate700.copy(alpha = 0.55f),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active sweep
            if (animatedSweep > 0.5f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(SoftTeal300, WarmAmber400, accentColor)
                    ),
                    startAngle = 150f,
                    sweepAngle = animatedSweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = animatedScore.toString(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            AnimatedContent(
                targetState = severityTier.label.uppercase(),
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "severityTierLabel"
            ) { tierLabel ->
                Text(
                    text = tierLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "/ 100 RISK",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ClearanceDecayChart(
    curve: List<CurvePoint>,
    curveStartMillis: Long,
    curveEndMillis: Long,
    bedtimeMillis: Long,
    nowMillis: Long,
    modifier: Modifier = Modifier
) {
    val revealProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        revealProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
        )
    }

    val targetLoads = remember(curve) { curve.map { it.normalizedLoad.coerceIn(0f, 1f) } }
    var previousLoads by remember { mutableStateOf(targetLoads) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(targetLoads) {
        if (previousLoads.size == targetLoads.size && previousLoads != targetLoads) {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        previousLoads = targetLoads
    }

    val beaconTransition = rememberInfiniteTransition(label = "nowBeaconPulse")
    val beaconPulse by beaconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beaconRadiusPhase"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            if (targetLoads.size < 2) return@Canvas
            val w = size.width
            val h = size.height
            val padTop = 10.dp.toPx()
            val padBottom = 10.dp.toPx()
            val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
            val totalSpan = (curveEndMillis - curveStartMillis).coerceAtLeast(1L).toFloat()

            // Horizontal baseline grid lines
            listOf(0.25f, 0.5f, 0.75f).forEach { frac ->
                val y = padTop + usableH * frac
                drawLine(
                    color = MidnightSlate700.copy(alpha = 0.35f),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Build interpolated curve path
            val linePath = Path()
            val fillPath = Path()
            val interpLoads = List(targetLoads.size) { idx ->
                val prev = previousLoads.getOrElse(idx) { targetLoads[idx] }
                lerp(prev, targetLoads[idx], morphProgress.value).coerceIn(0f, 1f)
            }

            interpLoads.forEachIndexed { idx, load ->
                val x = (idx.toFloat() / (interpLoads.size - 1)) * w
                val y = padTop + (1f - load) * usableH
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, h - padBottom)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (idx == interpLoads.lastIndex) {
                    fillPath.lineTo(x, h - padBottom)
                    fillPath.close()
                }
            }

            clipRect(right = w * revealProgress.value) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            WarmAmber400.copy(alpha = 0.34f),
                            WarmAmber400.copy(alpha = 0.02f)
                        )
                    )
                )
                drawPath(
                    path = linePath,
                    color = WarmAmber400,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Bedtime vertical dashed marker
            val bedFraction = ((bedtimeMillis - curveStartMillis).toFloat() / totalSpan).coerceIn(0f, 1f)
            val bedX = bedFraction * w
            drawLine(
                color = SoftTeal300,
                start = Offset(bedX, 0f),
                end = Offset(bedX, h - padBottom),
                strokeWidth = 1.8.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
            )

            // Current time dot + breathing halo
            val nowFraction = ((nowMillis - curveStartMillis).toFloat() / totalSpan).coerceIn(0f, 1f)
            val nowX = nowFraction * w
            val idxFloat = nowFraction * (interpLoads.size - 1)
            val idxLow = idxFloat.toInt().coerceIn(0, interpLoads.lastIndex)
            val idxHigh = (idxLow + 1).coerceIn(0, interpLoads.lastIndex)
            val interp = interpLoads[idxLow] + (interpLoads[idxHigh] - interpLoads[idxLow]) * (idxFloat - idxLow)
            val nowY = padTop + (1f - interp.coerceIn(0f, 1f)) * usableH

            drawCircle(
                color = WarmAmber400.copy(alpha = 0.36f * (1f - beaconPulse * 0.55f)),
                radius = (7f + 4.5f * beaconPulse).dp.toPx(),
                center = Offset(nowX, nowY)
            )
            drawCircle(
                color = MidnightSlate850,
                radius = 6.dp.toPx(),
                center = Offset(nowX, nowY)
            )
            drawCircle(
                color = WarmAmber400,
                radius = 4.dp.toPx(),
                center = Offset(nowX, nowY)
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateTimeUtils.formatTime(curveStartMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bedtime ${DateTimeUtils.formatTime(bedtimeMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = SoftTeal300
            )
            Text(
                text = DateTimeUtils.formatTime(curveEndMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HistoryTrendChart(
    points: List<DailyScorePoint>,
    selectedDateIso: String,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) return

    val targetFractions = remember(points) {
        points.map { pt ->
            if (pt.entryCount == 0) 0f else (pt.score / 100f).coerceIn(0.05f, 1f)
        }
    }
    var previousFractions by remember { mutableStateOf(List(targetFractions.size) { 0f }) }
    val morphProgress = remember { Animatable(0f) }

    LaunchedEffect(targetFractions) {
        morphProgress.snapTo(0f)
        morphProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        previousFractions = targetFractions
    }

    val selectedIndex = remember(points, selectedDateIso) {
        points.indexOfFirst { it.dateIso == selectedDateIso }.toFloat()
    }
    val animatedSelectedIndex by animateFloatAsState(
        targetValue = if (selectedIndex >= 0f) selectedIndex else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "trendSelectedIndex"
    )
    val hasSelection = selectedIndex >= 0f

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(92.dp)
        ) {
            val w = size.width
            val h = size.height
            val slotW = w / points.size.coerceAtLeast(1)
            val barW = (slotW * 0.56f).coerceAtLeast(6.dp.toPx())

            // Midpoint 50 line
            drawLine(
                color = MidnightSlate700.copy(alpha = 0.4f),
                start = Offset(0f, h * 0.5f),
                end = Offset(w, h * 0.5f),
                strokeWidth = 1.dp.toPx()
            )

            if (hasSelection) {
                val selX = animatedSelectedIndex * slotW + (slotW - barW) / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.2f),
                    topLeft = Offset(selX - 2.dp.toPx(), 0f),
                    size = Size(barW + 4.dp.toPx(), h),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )
            }

            points.forEachIndexed { idx, pt ->
                val x = idx * slotW + (slotW - barW) / 2f
                val prevFrac = previousFractions.getOrElse(idx) { 0f }
                val targetFrac = targetFractions[idx]
                val interpFrac = lerp(prevFrac, targetFrac, morphProgress.value).coerceIn(0f, 1f)
                val barH = if (pt.entryCount == 0) {
                    4.dp.toPx()
                } else {
                    (interpFrac * (h - 8.dp.toPx())).coerceAtLeast(4.dp.toPx())
                }
                val barColor = when {
                    pt.entryCount == 0 -> MidnightSlate800
                    pt.score < 25 -> SeverityLowEmerald
                    pt.score < 50 -> SeverityModerateAmber
                    pt.score < 75 -> SeveritySevereCoral
                    else -> SeverityCriticalCrimson
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x, h - barH),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateTimeUtils.formatDisplayDate(points.first().dateIso),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "14-Day Risk Trend",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = DateTimeUtils.formatDisplayDate(points.last().dateIso),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SleepStageBar(
    deepMinutes: Int,
    remMinutes: Int,
    lightMinutes: Int,
    awakeMinutes: Int,
    modifier: Modifier = Modifier
) {
    val total = (deepMinutes + remMinutes + lightMinutes + awakeMinutes).coerceAtLeast(1).toFloat()
    val stageSpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow
    )
    val deepWeight by animateFloatAsState(deepMinutes / total, stageSpring, label = "deepStage")
    val remWeight by animateFloatAsState(remMinutes / total, stageSpring, label = "remStage")
    val lightWeight by animateFloatAsState(lightMinutes / total, stageSpring, label = "lightStage")
    val awakeWeight by animateFloatAsState(awakeMinutes / total, stageSpring, label = "awakeStage")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MidnightSlate800)
    ) {
        if (deepWeight > 0.002f) {
            Box(
                modifier = Modifier
                    .weight(deepWeight)
                    .height(10.dp)
                    .background(StageDeepTeal)
            )
        }
        if (remWeight > 0.002f) {
            Box(
                modifier = Modifier
                    .weight(remWeight)
                    .height(10.dp)
                    .background(StageRemIndigo)
            )
        }
        if (lightWeight > 0.002f) {
            Box(
                modifier = Modifier
                    .weight(lightWeight)
                    .height(10.dp)
                    .background(StageLightSlate)
            )
        }
        if (awakeWeight > 0.002f) {
            Box(
                modifier = Modifier
                    .weight(awakeWeight)
                    .height(10.dp)
                    .background(StageAwakeAmber)
            )
        }
    }
}

val CircadiaSeriesColors: List<Color> = listOf(
    WarmAmber400,
    SoftTeal300,
    SleepIndigo400,
    SeverityLowEmerald,
    SeveritySevereCoral
)

fun seriesColorAt(index: Int): Color {
    val idx = (index % CircadiaSeriesColors.size + CircadiaSeriesColors.size) % CircadiaSeriesColors.size
    return CircadiaSeriesColors[idx]
}

@Composable
fun MultiSeriesConcentrationChart(
    chartModel: DayConcentrationChartModel,
    nowMillis: Long?,
    onSelectDecayModel: (DecayModelMode) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chartModel.series.isEmpty()) return

    var focusedEntryId by remember(chartModel.dateIso) { mutableStateOf<Long?>(null) }
    val activeFocusedId = focusedEntryId?.takeIf { id -> chartModel.series.any { it.entry.id == id } }
    val visibleSeries = remember(chartModel.series, activeFocusedId) {
        if (activeFocusedId != null) {
            chartModel.series.filter { it.entry.id == activeFocusedId }
        } else {
            chartModel.series
        }
    }

    var preferExactUnits by remember { mutableStateOf(false) }
    val singleSeries = visibleSeries.singleOrNull()
    val exactUnitLabel = when {
        singleSeries != null -> singleSeries.entry.dosageUnit.trim().ifBlank { "units" }
        chartModel.sharedUnitLabel != null -> chartModel.sharedUnitLabel
        else -> null
    }
    val yMaxExact = when {
        singleSeries != null -> singleSeries.entry.dosageAmount.coerceAtLeast(0.01)
        chartModel.sharedUnitLabel != null && chartModel.combinedMaxSingleUnit != null ->
            chartModel.combinedMaxSingleUnit.coerceAtLeast(0.01)
        else -> 1.0
    }
    val canShowExactUnits = exactUnitLabel != null
    val isExactAxis = preferExactUnits && canShowExactUnits

    var scrubbedTimestampMillis by remember(chartModel.dateIso) { mutableStateOf<Long?>(null) }

    val previousPointsMap = remember(chartModel.dateIso) {
        mutableMapOf<Long, List<ConcentrationPoint>>()
    }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(chartModel.decayModel, chartModel.series, isExactAxis) {
        morphProgress.snapTo(0f)
        morphProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        chartModel.series.forEach { s ->
            previousPointsMap[s.entry.id] = s.points
        }
    }

    val beaconTransition = rememberInfiniteTransition(label = "multiConcentrationNowPulse")
    val beaconPulse by beaconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "multiBeaconPulse"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section header & quick scrubber reset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Active Concentration Over Time",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Tap or drag chart to inspect exact levels",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (scrubbedTimestampMillis != null) {
                TextButton(
                    onClick = { scrubbedTimestampMillis = null },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("concentration_scrub_clear")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear time scrubber",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }

        // Pharmacokinetic formula toggle + Y-axis unit switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DecayModelMode.entries.forEach { mode ->
                val selected = chartModel.decayModel == mode
                FilterChip(
                    selected = selected,
                    onClick = { onSelectDecayModel(mode) },
                    label = { Text(mode.label) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("decay_model_${mode.name}")
                )
            }

            FilterChip(
                selected = !isExactAxis,
                onClick = { preferExactUnits = false },
                label = { Text("% Left") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("yaxis_percent_chip")
            )

            FilterChip(
                selected = isExactAxis,
                enabled = canShowExactUnits,
                onClick = {
                    if (canShowExactUnits) {
                        preferExactUnits = true
                    }
                },
                label = {
                    Text(
                        if (exactUnitLabel != null) "Exact ($exactUnitLabel)" else "Exact (Mixed)"
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("yaxis_exact_chip")
            )
        }

        // Active formula equation subtitle
        AnimatedContent(
            targetState = chartModel.decayModel,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
            label = "decayFormulaSummary"
        ) { mode ->
            Text(
                text = mode.formulaSummary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        if (!canShowExactUnits && chartModel.series.size > 1) {
            Text(
                text = "Mixed units logged — select a single substance below to plot exact dosage units.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Interactive Legend / Series Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = activeFocusedId == null,
                onClick = { focusedEntryId = null },
                label = { Text("All (${chartModel.series.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MidnightSlate800,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("legend_series_all")
            )

            chartModel.series.forEach { s ->
                val seriesColor = seriesColorAt(s.seriesColorIndex)
                val isSelected = activeFocusedId == s.entry.id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        focusedEntryId = if (isSelected) null else s.entry.id
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(seriesColor)
                        )
                    },
                    label = {
                        Text(
                            text = "${s.entry.substanceName} (${formatCompactAmount(s.entry.dosageAmount)} ${s.entry.dosageUnit})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = seriesColor.copy(alpha = 0.22f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("legend_series_${s.entry.id}")
                )
            }
        }

        // Chart Canvas + Y-Axis Ticks
        val topLabel = if (isExactAxis) {
            "${formatCompactAmount(yMaxExact)} $exactUnitLabel"
        } else {
            "100%"
        }
        val midLabel = if (isExactAxis) {
            formatCompactAmount(yMaxExact * 0.5)
        } else {
            "50%"
        }
        val clearThresholdLabel = if (isExactAxis && visibleSeries.size == 1) {
            formatCompactAmount(yMaxExact * 0.1)
        } else if (!isExactAxis) {
            "10%"
        } else {
            ""
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Y-axis ticks
            Column(
                modifier = Modifier
                    .width(50.dp)
                    .fillMaxHeight()
                    .padding(vertical = 8.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = topLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = midLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = clearThresholdLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftTeal300.copy(alpha = 0.85f),
                    maxLines = 1
                )
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Spacer(Modifier.width(6.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(chartModel.windowStartMillis, chartModel.windowEndMillis) {
                        val span = (chartModel.windowEndMillis - chartModel.windowStartMillis).coerceAtLeast(1L)
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            scrubbedTimestampMillis = chartModel.windowStartMillis + (frac * span).toLong()
                        }
                    }
                    .pointerInput(chartModel.windowStartMillis, chartModel.windowEndMillis) {
                        val span = (chartModel.windowEndMillis - chartModel.windowStartMillis).coerceAtLeast(1L)
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                            scrubbedTimestampMillis = chartModel.windowStartMillis + (frac * span).toLong()
                        }
                    }
                    .testTag("multi_concentration_canvas")
            ) {
                val w = size.width
                val h = size.height
                val padTop = 10.dp.toPx()
                val padBottom = 10.dp.toPx()
                val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
                val totalSpan = (chartModel.windowEndMillis - chartModel.windowStartMillis).coerceAtLeast(1L).toFloat()

                // Baseline horizontal grid lines (100%, 50%, 0%)
                listOf(0f, 0.5f, 1f).forEach { normY ->
                    val y = padTop + (1f - normY) * usableH
                    drawLine(
                        color = MidnightSlate700.copy(alpha = 0.42f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Dashed 10% clearance threshold line
                if (!isExactAxis || visibleSeries.size == 1) {
                    val thresholdY = padTop + (1f - 0.10f) * usableH
                    drawLine(
                        color = SoftTeal300.copy(alpha = 0.55f),
                        start = Offset(0f, thresholdY),
                        end = Offset(w, thresholdY),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                }

                // Bedtime vertical dashed marker
                val bedFraction = ((chartModel.bedtimeMillis - chartModel.windowStartMillis).toFloat() / totalSpan)
                    .coerceIn(0f, 1f)
                val bedX = bedFraction * w
                drawLine(
                    color = SoftTeal300.copy(alpha = 0.85f),
                    start = Offset(bedX, padTop),
                    end = Offset(bedX, h - padBottom),
                    strokeWidth = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                // Draw each visible concentration line
                val showAreaFill = visibleSeries.size == 1
                visibleSeries.forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val color = seriesColorAt(s.seriesColorIndex)
                    val prevPoints = previousPointsMap[s.entry.id].orEmpty()

                    val linePath = Path()
                    val fillPath = Path()
                    s.points.forEachIndexed { idx, pt ->
                        val x = (((pt.timestampMillis - chartModel.windowStartMillis).toFloat() / totalSpan) * w)
                            .coerceIn(0f, w)
                        val targetNorm = pointNormalizedY(pt, isExactAxis, yMaxExact)
                        val prevNorm = if (prevPoints.isNotEmpty()) {
                            interpolateNormalizedYAtTimestamp(
                                points = prevPoints,
                                timestampMillis = pt.timestampMillis,
                                isExactAxis = isExactAxis,
                                yMaxExact = yMaxExact
                            )
                        } else {
                            targetNorm
                        }
                        val normY = lerp(prevNorm, targetNorm, morphProgress.value).coerceIn(0f, 1f)
                        val y = padTop + (1f - normY) * usableH

                        if (idx == 0) {
                            linePath.moveTo(x, y)
                            if (showAreaFill) {
                                fillPath.moveTo(x, h - padBottom)
                                fillPath.lineTo(x, y)
                            }
                        } else {
                            linePath.lineTo(x, y)
                            if (showAreaFill) {
                                fillPath.lineTo(x, y)
                            }
                        }
                        if (showAreaFill && idx == s.points.lastIndex) {
                            fillPath.lineTo(x, h - padBottom)
                            fillPath.close()
                        }
                    }

                    if (showAreaFill) {
                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    color.copy(alpha = 0.30f),
                                    color.copy(alpha = 0.02f)
                                )
                            )
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = color,
                        style = Stroke(
                            width = 2.5.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )

                    // Bedtime dot on curve
                    val bedNorm = snapshotNormalizedY(s.bedtimeSnapshot, s.entry.dosageAmount, isExactAxis, yMaxExact)
                    val bedY = padTop + (1f - bedNorm) * usableH
                    drawCircle(color = MidnightSlate850, radius = 4.5.dp.toPx(), center = Offset(bedX, bedY))
                    drawCircle(color = SoftTeal300, radius = 2.8.dp.toPx(), center = Offset(bedX, bedY))

                    // Pulsing "Now" marker on Today
                    if (nowMillis != null && nowMillis in chartModel.windowStartMillis..chartModel.windowEndMillis && s.nowSnapshot != null) {
                        val nowFraction = ((nowMillis - chartModel.windowStartMillis).toFloat() / totalSpan)
                            .coerceIn(0f, 1f)
                        val nowX = nowFraction * w
                        val nowNorm = snapshotNormalizedY(s.nowSnapshot, s.entry.dosageAmount, isExactAxis, yMaxExact)
                        val nowY = padTop + (1f - nowNorm) * usableH
                        drawCircle(
                            color = color.copy(alpha = 0.34f * (1f - beaconPulse * 0.55f)),
                            radius = (6.5f + 4f * beaconPulse).dp.toPx(),
                            center = Offset(nowX, nowY)
                        )
                        drawCircle(color = MidnightSlate850, radius = 5.dp.toPx(), center = Offset(nowX, nowY))
                        drawCircle(color = color, radius = 3.2.dp.toPx(), center = Offset(nowX, nowY))
                    }
                }

                // Subtle Now vertical hairline when active
                if (nowMillis != null && nowMillis in chartModel.windowStartMillis..chartModel.windowEndMillis) {
                    val nowX = ((nowMillis - chartModel.windowStartMillis).toFloat() / totalSpan).coerceIn(0f, 1f) * w
                    drawLine(
                        color = Color.White.copy(alpha = 0.25f),
                        start = Offset(nowX, padTop),
                        end = Offset(nowX, h - padBottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Interactive Scrubber hairline & exact curve intersection dots
                val scrubTs = scrubbedTimestampMillis
                if (scrubTs != null) {
                    val scrubFrac = ((scrubTs - chartModel.windowStartMillis).toFloat() / totalSpan).coerceIn(0f, 1f)
                    val scrubX = scrubFrac * w
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(scrubX, padTop),
                        end = Offset(scrubX, h - padBottom),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    visibleSeries.forEach { s ->
                        val color = seriesColorAt(s.seriesColorIndex)
                        val snap = PharmacokineticsEngine.calculateRemainingConcentration(
                            dosageAmount = s.entry.dosageAmount,
                            takenAtMillis = s.entry.takenAtMillis,
                            halfLifeHours = s.entry.halfLifeHours,
                            targetMillis = scrubTs,
                            peakOnsetMinutes = s.peakOnsetMinutes,
                            model = chartModel.decayModel
                        )
                        val normY = snapshotNormalizedY(snap, s.entry.dosageAmount, isExactAxis, yMaxExact)
                        val scrubY = padTop + (1f - normY) * usableH
                        drawCircle(color = MidnightSlate850, radius = 6.dp.toPx(), center = Offset(scrubX, scrubY))
                        drawCircle(color = color, radius = 3.8.dp.toPx(), center = Offset(scrubX, scrubY))
                    }
                }
            }
        }

        // Time X-Axis labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateTimeUtils.formatTime(chartModel.windowStartMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bed ${DateTimeUtils.formatTime(chartModel.bedtimeMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = SoftTeal300,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = DateTimeUtils.formatTime(chartModel.windowEndMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Scrubber Readout Banner (visible while scrubbing)
        val activeScrubTs = scrubbedTimestampMillis
        AnimatedVisibility(visible = activeScrubTs != null) {
            if (activeScrubTs != null) {
                Surface(
                    color = MidnightSlate900,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("concentration_scrub_readout")
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "INSPECTED AT ${DateTimeUtils.formatTime(activeScrubTs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = WarmAmber400,
                            fontWeight = FontWeight.Bold
                        )
                        visibleSeries.forEach { s ->
                            val color = seriesColorAt(s.seriesColorIndex)
                            val snap = PharmacokineticsEngine.calculateRemainingConcentration(
                                dosageAmount = s.entry.dosageAmount,
                                takenAtMillis = s.entry.takenAtMillis,
                                halfLifeHours = s.entry.halfLifeHours,
                                targetMillis = activeScrubTs,
                                peakOnsetMinutes = s.peakOnsetMinutes,
                                model = chartModel.decayModel
                            )
                            val labelValue = if (activeScrubTs < s.entry.takenAtMillis) {
                                "Not taken yet (0 ${s.entry.dosageUnit} · 0%)"
                            } else {
                                "${formatConcentrationValue(snap.remainingAmount)} ${s.entry.dosageUnit} (${snap.percentRemaining.roundToInt()}%)"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                    Text(
                                        text = s.entry.substanceName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = labelValue,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = color,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Exact Dosage Units + % Remaining Per-Substance Summary Panel
        Surface(
            color = MidnightSlate800.copy(alpha = 0.65f),
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleSeries.forEach { s ->
                    val color = seriesColorAt(s.seriesColorIndex)
                    val bedAmt = formatConcentrationValue(s.bedtimeSnapshot.remainingAmount)
                    val bedPct = s.bedtimeSnapshot.percentRemaining.roundToInt()
                    val nowPart = s.nowSnapshot?.let { nowSnap ->
                        "Now ${formatConcentrationValue(nowSnap.remainingAmount)} ${s.entry.dosageUnit} (${nowSnap.percentRemaining.roundToInt()}%) · "
                    } ?: ""
                    val clearTimeStr = DateTimeUtils.formatTime(s.clearedAtMillis)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = "${s.entry.substanceName} (${formatCompactAmount(s.entry.dosageAmount)} ${s.entry.dosageUnit})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "<10% @ $clearTimeStr",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${nowPart}Bed $bedAmt ${s.entry.dosageUnit} ($bedPct%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = color,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SingleSubstanceDecayChart(
    series: SubstanceConcentrationSeries,
    bedtimeMillis: Long,
    nowMillis: Long?,
    decayModel: DecayModelMode,
    modifier: Modifier = Modifier
) {
    val points = series.points
    if (points.size < 2) return

    val entry = series.entry
    val seriesColor = seriesColorAt(series.seriesColorIndex)
    val unitLabel = entry.dosageUnit.trim().ifBlank { "units" }
    val maxDose = entry.dosageAmount.coerceAtLeast(0.01)

    var previousPoints by remember(entry.id) { mutableStateOf(points) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(points, decayModel) {
        if (previousPoints != points) {
            morphProgress.snapTo(0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            previousPoints = points
        }
    }

    val beaconTransition = rememberInfiniteTransition(label = "singleDecayNowPulse_${entry.id}")
    val beaconPulse by beaconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "singleBeaconPhase_${entry.id}"
    )

    val windowStart = points.first().timestampMillis
    val windowEnd = points.last().timestampMillis

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("single_decay_chart_${entry.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DECAY CURVE (${decayModel.label.uppercase()})",
                style = MaterialTheme.typography.labelSmall,
                color = seriesColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "t½ ${entry.halfLifeHours}h · Peak ${series.peakOnsetMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(102.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .width(46.dp)
                    .fillMaxHeight()
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "${formatCompactAmount(maxDose)} $unitLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "50%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "10%",
                    style = MaterialTheme.typography.labelSmall,
                    color = SoftTeal300.copy(alpha = 0.85f)
                )
            }

            Spacer(Modifier.width(6.dp))

            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val w = size.width
                val h = size.height
                val padTop = 8.dp.toPx()
                val padBottom = 8.dp.toPx()
                val usableH = (h - padTop - padBottom).coerceAtLeast(1f)
                val totalSpan = (windowEnd - windowStart).coerceAtLeast(1L).toFloat()

                // Grid lines
                listOf(0f, 0.5f, 1f).forEach { normY ->
                    val y = padTop + (1f - normY) * usableH
                    drawLine(
                        color = MidnightSlate700.copy(alpha = 0.38f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 10% dashed clearance threshold
                val clearY = padTop + (1f - 0.10f) * usableH
                drawLine(
                    color = SoftTeal300.copy(alpha = 0.55f),
                    start = Offset(0f, clearY),
                    end = Offset(w, clearY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                )

                val linePath = Path()
                val fillPath = Path()
                points.forEachIndexed { idx, pt ->
                    val x = (((pt.timestampMillis - windowStart).toFloat() / totalSpan) * w).coerceIn(0f, w)
                    val targetNorm = (pt.percentRemaining / 100f).coerceIn(0f, 1f)
                    val prevNorm = interpolateNormalizedYAtTimestamp(
                        points = previousPoints,
                        timestampMillis = pt.timestampMillis,
                        isExactAxis = false,
                        yMaxExact = maxDose
                    )
                    val normY = lerp(prevNorm, targetNorm, morphProgress.value).coerceIn(0f, 1f)
                    val y = padTop + (1f - normY) * usableH
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, h - padBottom)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (idx == points.lastIndex) {
                        fillPath.lineTo(x, h - padBottom)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            seriesColor.copy(alpha = 0.28f),
                            seriesColor.copy(alpha = 0.02f)
                        )
                    )
                )
                drawPath(
                    path = linePath,
                    color = seriesColor,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Bedtime dashed vertical line + dot
                if (bedtimeMillis in windowStart..windowEnd) {
                    val bedX = ((bedtimeMillis - windowStart).toFloat() / totalSpan).coerceIn(0f, 1f) * w
                    val bedNorm = (series.bedtimeSnapshot.percentRemaining / 100.0).toFloat().coerceIn(0f, 1f)
                    val bedY = padTop + (1f - bedNorm) * usableH
                    drawLine(
                        color = SoftTeal300.copy(alpha = 0.75f),
                        start = Offset(bedX, padTop),
                        end = Offset(bedX, h - padBottom),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    )
                    drawCircle(color = MidnightSlate850, radius = 4.5.dp.toPx(), center = Offset(bedX, bedY))
                    drawCircle(color = SoftTeal300, radius = 2.8.dp.toPx(), center = Offset(bedX, bedY))
                }

                // Pulsing Now dot if on Today
                if (nowMillis != null && nowMillis in windowStart..windowEnd && series.nowSnapshot != null) {
                    val nowX = ((nowMillis - windowStart).toFloat() / totalSpan).coerceIn(0f, 1f) * w
                    val nowNorm = (series.nowSnapshot.percentRemaining / 100.0).toFloat().coerceIn(0f, 1f)
                    val nowY = padTop + (1f - nowNorm) * usableH
                    drawCircle(
                        color = seriesColor.copy(alpha = 0.34f * (1f - beaconPulse * 0.55f)),
                        radius = (5.5f + 3.5f * beaconPulse).dp.toPx(),
                        center = Offset(nowX, nowY)
                    )
                    drawCircle(color = MidnightSlate850, radius = 4.5.dp.toPx(), center = Offset(nowX, nowY))
                    drawCircle(color = seriesColor, radius = 2.8.dp.toPx(), center = Offset(nowX, nowY))
                }
            }
        }

        // Compact Time Axis
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = DateTimeUtils.formatTime(windowStart),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Bed ${DateTimeUtils.formatTime(bedtimeMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = SoftTeal300
            )
            Text(
                text = DateTimeUtils.formatTime(windowEnd),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 3-stat readout strip: Now (if today), At Bedtime, <10% Clear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            series.nowSnapshot?.let { nowSnap ->
                ConcentrationStatTile(
                    label = "NOW",
                    value = "${formatConcentrationValue(nowSnap.remainingAmount)} $unitLabel (${nowSnap.percentRemaining.roundToInt()}%)",
                    valueColor = seriesColor,
                    modifier = Modifier.weight(1f)
                )
            }
            ConcentrationStatTile(
                label = "AT BEDTIME",
                value = "${formatConcentrationValue(series.bedtimeSnapshot.remainingAmount)} $unitLabel (${series.bedtimeSnapshot.percentRemaining.roundToInt()}%)",
                valueColor = SoftTeal300,
                modifier = Modifier.weight(1f)
            )
            ConcentrationStatTile(
                label = "<10% CLEAR",
                value = DateTimeUtils.formatTime(series.clearedAtMillis),
                valueColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.85f)
            )
        }
    }
}

@Composable
private fun ConcentrationStatTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MidnightSlate850,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun pointNormalizedY(
    point: ConcentrationPoint,
    isExactAxis: Boolean,
    yMaxExact: Double
): Float {
    return if (isExactAxis) {
        (point.remainingAmount / yMaxExact.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)
    } else {
        (point.percentRemaining / 100f).coerceIn(0f, 1f)
    }
}

private fun snapshotNormalizedY(
    snapshot: ConcentrationSnapshot,
    entryDosage: Double,
    isExactAxis: Boolean,
    yMaxExact: Double
): Float {
    return if (isExactAxis) {
        (snapshot.remainingAmount / yMaxExact.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f)
    } else {
        val pct = if (entryDosage > 0.0) snapshot.percentRemaining else 0.0
        (pct / 100.0).toFloat().coerceIn(0f, 1f)
    }
}

private fun interpolateNormalizedYAtTimestamp(
    points: List<ConcentrationPoint>,
    timestampMillis: Long,
    isExactAxis: Boolean,
    yMaxExact: Double
): Float {
    if (points.isEmpty()) return 0f
    if (timestampMillis <= points.first().timestampMillis) {
        return pointNormalizedY(points.first(), isExactAxis, yMaxExact)
    }
    if (timestampMillis >= points.last().timestampMillis) {
        return pointNormalizedY(points.last(), isExactAxis, yMaxExact)
    }
    val idx = points.indexOfFirst { it.timestampMillis >= timestampMillis }
    if (idx <= 0) return pointNormalizedY(points.first(), isExactAxis, yMaxExact)
    val p0 = points[idx - 1]
    val p1 = points[idx]
    val dt = (p1.timestampMillis - p0.timestampMillis).coerceAtLeast(1L).toFloat()
    val t = ((timestampMillis - p0.timestampMillis).toFloat() / dt).coerceIn(0f, 1f)
    val y0 = pointNormalizedY(p0, isExactAxis, yMaxExact)
    val y1 = pointNormalizedY(p1, isExactAxis, yMaxExact)
    return lerp(y0, y1, t).coerceIn(0f, 1f)
}

private fun formatConcentrationValue(amount: Double): String {
    return when {
        amount >= 100.0 -> String.format(Locale.US, "%.0f", amount)
        amount >= 10.0 -> String.format(Locale.US, "%.1f", amount)
        else -> String.format(Locale.US, "%.2f", amount)
    }
}

