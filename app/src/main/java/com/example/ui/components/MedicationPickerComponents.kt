package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.HalfLifeSource
import com.example.data.remote.RemoteMedicationItem
import com.example.domain.DoseOption
import com.example.domain.SubstanceReference
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeverityModerateAmber
import kotlin.math.abs

@Composable
internal fun MedicationSectionHeader(
    title: String,
    badgeText: String? = null,
    isLoading: Boolean = false,
    isOffline: Boolean = false
) {
    Surface(
        color = MidnightSlate800.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isOffline) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = "Offline cache",
                        tint = SeverityModerateAmber,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Searching…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (!badgeText.isNullOrBlank()) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CuratedSubstanceRow(
    ref: SubstanceReference,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val doseSummary = buildDoseRangeSummary(ref.doseOptions, ref.defaultDosage, ref.defaultUnit)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("substance_option_${ref.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = ref.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (ref.formulation.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = ref.formulation,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            val metaLine = buildString {
                if (ref.genericName.isNotBlank() && !ref.name.contains(ref.genericName, ignoreCase = true)) {
                    append(ref.genericName)
                    append(" · ")
                }
                append(doseSummary)
                append(" · t½ ")
                append(formatCompactAmount(ref.halfLifeHours))
                append("h")
            }
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun RemoteMedicationRow(
    item: RemoteMedicationItem,
    isSelected: Boolean,
    fromCache: Boolean,
    onClick: () -> Unit
) {
    val strengthCount = item.strengthOptions.size
    val strengthPreview = if (strengthCount > 0) {
        val first = item.strengthOptions.first()
        val last = item.strengthOptions.last()
        if (strengthCount == 1) {
            first.label
        } else {
            "$strengthCount strengths · ${formatCompactAmount(first.amount)}–${formatCompactAmount(last.amount)} ${last.unit}"
        }
    } else {
        "Custom strength"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("remote_medication_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocalPharmacy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = item.cleanName,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = item.routeForm,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = "${if (fromCache) "Cached NIH" else "NIH RxTerms"} · $strengthPreview",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExactStrengthChipsSection(
    formulationTitle: String,
    doseOptions: List<DoseOption>,
    currentDose: Double?,
    currentUnit: String,
    onSelectOption: (DoseOption) -> Unit
) {
    if (doseOptions.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exact_strength_chips_section"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Standard Strengths",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            if (formulationTitle.isNotBlank()) {
                Text(
                    text = formulationTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            doseOptions.take(16).forEachIndexed { index, option ->
                val isSelected = currentDose != null &&
                    abs(currentDose - option.amount) < 0.01 &&
                    currentUnit.equals(option.unit, ignoreCase = true)

                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectOption(option) },
                    label = {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    leadingIcon = if (isSelected) {
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
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("strength_chip_$index")
                )
            }
        }
    }
}

@Composable
internal fun HalfLifeProvenanceCard(
    halfLifeDisplay: String,
    source: HalfLifeSource,
    isLookingUpFda: Boolean,
    excerpt: String?,
    isEditingHalfLife: Boolean,
    onToggleEditHalfLife: () -> Unit
) {
    val badgeColor = when (source) {
        HalfLifeSource.CURATED -> SeverityLowEmerald
        HalfLifeSource.OPEN_FDA, HalfLifeSource.CACHED_OPEN_FDA -> MaterialTheme.colorScheme.secondary
        HalfLifeSource.ESTIMATED -> SeverityModerateAmber
    }
    val badgeIcon = when (source) {
        HalfLifeSource.CURATED -> Icons.Default.Verified
        HalfLifeSource.OPEN_FDA, HalfLifeSource.CACHED_OPEN_FDA -> Icons.Default.CloudDone
        HalfLifeSource.ESTIMATED -> Icons.Default.Science
    }

    Surface(
        color = MidnightSlate800.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("halflife_provenance_card")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isLookingUpFda) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Fetching openFDA half-life…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Half-Life: t½ ${halfLifeDisplay}h",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                color = badgeColor.copy(alpha = 0.16f),
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = source.label,
                                        tint = badgeColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = source.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = badgeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                TextButton(
                    onClick = onToggleEditHalfLife,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("toggle_edit_halflife_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (isEditingHalfLife) "Hide" else "Edit t½")
                }
            }

            AnimatedVisibility(visible = !isLookingUpFda && !excerpt.isNullOrBlank()) {
                Text(
                    text = excerpt.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun buildDoseRangeSummary(
    options: List<DoseOption>,
    defaultDosage: Double,
    defaultUnit: String
): String {
    if (options.size <= 1) {
        return "${formatCompactAmount(defaultDosage)} $defaultUnit"
    }
    val minAmt = options.minOf { it.amount }
    val maxAmt = options.maxOf { it.amount }
    return "${formatCompactAmount(minAmt)}–${formatCompactAmount(maxAmt)} $defaultUnit"
}
