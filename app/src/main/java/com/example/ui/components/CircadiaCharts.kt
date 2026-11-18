package com.example.ui.components

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.domain.CurvePoint
import com.example.domain.DateTimeUtils
import com.example.domain.SeverityTier
import com.example.ui.DailyScorePoint
import com.example.ui.theme.MidnightSlate700
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.MidnightSlate850
import com.example.ui.theme.SeverityCriticalCrimson
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeverityModerateAmber
import com.example.ui.theme.SeveritySevereCoral
import com.example.ui.theme.SoftTeal300
import com.example.ui.theme.StageAwakeAmber
import com.example.ui.theme.StageDeepTeal
import com.example.ui.theme.StageLightSlate
import com.example.ui.theme.StageRemIndigo
import com.example.ui.theme.WarmAmber400

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
