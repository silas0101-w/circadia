package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SubstanceLogEntity
import com.example.domain.DateTimeUtils
import com.example.domain.EntryClearanceInfo
import com.example.domain.PharmacokineticsEngine
import com.example.ui.theme.MidnightSlate900
import com.example.ui.theme.WarmAmber400
import java.util.Locale

@Composable
fun SubstanceEntryCard(
    info: EntryClearanceInfo,
    isEntryReportStale: Boolean,
    hasEntryReport: Boolean,
    onQuickAdjust: (SubstanceLogEntity, doseDelta: Double, timeDeltaMillis: Long) -> Unit,
    onAnalyzeClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val e = info.entry
    var isExpanded by rememberSaveable(e.id) { mutableStateOf(false) }

    val doseStr = formatCompactAmount(e.dosageAmount)
    val remBedStr = String.format(Locale.US, "%.1f", info.remainingAtBedtimeAmount)
    val pctBed = info.percentRemainingAtBedtime.toInt()
    val animatedProgress by animateFloatAsState(
        targetValue = (pctBed / 100f).coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = 0.85f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bedtimeResidueProgress"
    )
    val clearanceText = if (info.hoursUntilTenPercentFromNow <= 0.08) {
        "Cleared (<10% active)"
    } else {
        "${DateTimeUtils.formatDurationHours(info.hoursUntilTenPercentFromNow)} to <10%"
    }

    val doseStep = PharmacokineticsEngine.computeSmartDoseStep(e.dosageUnit, e.dosageAmount)
    val stepLabel = formatCompactAmount(doseStep)

    val borderColor by animateColorAsState(
        targetValue = if (isExpanded) {
            WarmAmber400.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        },
        animationSpec = tween(220),
        label = "entryCardBorderColor"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "entryChevronRotation"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .testTag("substance_card_${e.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = iconForCategory(e.category),
                        contentDescription = e.category,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(9.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = e.substanceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$doseStr ${e.dosageUnit} · ${DateTimeUtils.formatTime(e.takenAtMillis)} · t½ ${e.halfLifeHours}h",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.testTag("tune_entry_${e.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse quick adjust" else "Expand quick adjust",
                        tint = if (isExpanded) WarmAmber400 else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation }
                    )
                }
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.testTag("edit_entry_${e.id}")
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit ${e.substanceName}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.testTag("delete_entry_${e.id}")
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete ${e.substanceName}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Bedtime residue & countdown bar
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "At bedtime: $remBedStr ${e.dosageUnit} ($pctBed%)",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (pctBed >= 25) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = clearanceText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = if (pctBed >= 35) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
            }

            // Expandable Inline Quick-Stepper Tray
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
            ) {
                Surface(
                    color = MidnightSlate900,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = WarmAmber400,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "QUICK ADJUST",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WarmAmber400,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Tap Edit for full preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QuickStepPill(
                                label = "-$stepLabel ${e.dosageUnit}",
                                enabled = e.dosageAmount > 0.25,
                                onClick = { onQuickAdjust(e, -doseStep, 0L) },
                                tag = "quick_minus_dose_${e.id}"
                            )
                            QuickStepPill(
                                label = "+$stepLabel ${e.dosageUnit}",
                                enabled = true,
                                onClick = { onQuickAdjust(e, doseStep, 0L) },
                                tag = "quick_plus_dose_${e.id}"
                            )
                            QuickStepPill(
                                label = "-30m",
                                enabled = true,
                                onClick = { onQuickAdjust(e, 0.0, -30 * 60_000L) },
                                tag = "quick_minus_time_${e.id}"
                            )
                            QuickStepPill(
                                label = "+30m",
                                enabled = true,
                                onClick = { onQuickAdjust(e, 0.0, 30 * 60_000L) },
                                tag = "quick_plus_time_${e.id}"
                            )
                        }
                    }
                }
            }

            if (e.notes.isNotBlank()) {
                Text(
                    text = e.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = isEntryReportStale,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(150))
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Update,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Edited · Stale",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                val (analyzeInteraction, analyzeScale) = rememberPressScale()
                FilledTonalButton(
                    onClick = onAnalyzeClick,
                    interactionSource = analyzeInteraction,
                    colors = if (isEntryReportStale) {
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
                            scaleX = analyzeScale
                            scaleY = analyzeScale
                        }
                        .testTag("analyze_entry_${e.id}")
                ) {
                    Icon(
                        imageVector = if (isEntryReportStale) Icons.Default.Update else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    AnimatedContent(
                        targetState = when {
                            isEntryReportStale -> "Re-Analyze Impact"
                            hasEntryReport -> "View AI Impact"
                            else -> "Analyze Why & Severity"
                        },
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "analyzeEntryLabel"
                    ) { btnLabel ->
                        Text(btnLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStepPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val (interactionSource, pressScale) = rememberPressScale(pressedScale = 0.93f)
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

fun formatCompactAmount(amount: Double): String {
    return if (amount % 1.0 == 0.0) {
        amount.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", amount)
    }
}

private fun iconForCategory(category: String): ImageVector {
    val lower = category.lowercase()
    return when {
        lower.contains("stim") -> Icons.Default.LocalCafe
        lower.contains("depress") -> Icons.Default.WineBar
        lower.contains("med") -> Icons.Default.Medication
        lower.contains("meal") || lower.contains("meta") -> Icons.Default.Restaurant
        else -> Icons.Default.Science
    }
}
