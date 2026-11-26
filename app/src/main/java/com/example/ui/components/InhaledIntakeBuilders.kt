package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VapingRooms
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.CannabinoidEntry
import com.example.domain.CannabinoidType
import com.example.domain.CannabisIntakeMethod
import com.example.domain.CannabisQuantityMode
import com.example.domain.CannabisSessionEstimate
import com.example.domain.NicotineDeliveryMethod
import com.example.domain.NicotineSessionEstimate
import com.example.domain.SmokedTobaccoType
import com.example.domain.VapeStrengthUnit
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.MidnightSlate900
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeverityModerateAmber
import com.example.ui.theme.SeveritySevereCoral

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CannabisIntakeBuilderCard(
    method: CannabisIntakeMethod,
    onSelectMethod: (CannabisIntakeMethod) -> Unit,
    cannabinoids: List<CannabinoidEntry>,
    onUpdateCannabinoid: (Long, CannabinoidEntry) -> Unit,
    onAddCannabinoid: () -> Unit,
    onRemoveCannabinoid: (Long) -> Unit,
    quantityMode: CannabisQuantityMode,
    onSelectQuantityMode: (CannabisQuantityMode) -> Unit,
    quantityText: String,
    onQuantityChange: (String) -> Unit,
    puffLengthSecondsText: String,
    onPuffLengthChange: (String) -> Unit,
    estimate: CannabisSessionEstimate
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSlate800.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cannabis_intake_builder_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (method == CannabisIntakeMethod.SMOKED) Icons.Default.LocalFireDepartment else Icons.Default.VapingRooms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Cannabis / THC Intake Method",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select how you took it in to model onset, potency, and bedtime load",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Step 1: Intake Method (Vaped / Smoked / Drank or Ate)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CannabisIntakeMethod.entries.forEach { option ->
                    val selected = method == option
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMethod(option) },
                        label = {
                            Text(
                                text = option.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cannabis_method_${option.name}")
                    )
                }
            }

            AnimatedVisibility(visible = method == CannabisIntakeMethod.DRANK_ATE) {
                Surface(
                    color = MidnightSlate900,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Oral THC converts in the liver to 11-hydroxy-THC (~75m peak onset, t½ 8h). Pick a standard mg strength below or enter an exact dose.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Step 2 & 3: Multi-Cannabinoid Profile + Puffs (with Puff Length) or Grams
            AnimatedVisibility(visible = method == CannabisIntakeMethod.VAPED || method == CannabisIntakeMethod.SMOKED) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cannabinoid Profile (%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        val over100 = estimate.totalPercent > 100.0
                        Surface(
                            color = if (over100) SeveritySevereCoral.copy(alpha = 0.18f) else MidnightSlate900,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = if (over100) {
                                    "Total ${formatCompactAmount(estimate.totalPercent)}% (>100%)"
                                } else {
                                    "Total ${formatCompactAmount(estimate.totalPercent)}%"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (over100) SeveritySevereCoral else MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Dynamic Cannabinoid Rows
                    cannabinoids.forEachIndexed { index, row ->
                        CannabinoidRowEditor(
                            index = index,
                            row = row,
                            method = method,
                            canRemove = cannabinoids.size > 1,
                            onChange = { updated -> onUpdateCannabinoid(row.rowId, updated) },
                            onRemove = { onRemoveCannabinoid(row.rowId) }
                        )
                    }

                    OutlinedButton(
                        onClick = onAddCannabinoid,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("add_cannabinoid_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Another Cannabinoid")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Quantity Mode Toggle: Puffs / Hits vs Grams
                    Text(
                        text = "Session Amount",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CannabisQuantityMode.entries.forEach { mode ->
                            val selected = quantityMode == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectQuantityMode(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("cannabis_qty_mode_${mode.name}")
                            )
                        }
                    }

                    if (quantityMode == CannabisQuantityMode.PUFFS) {
                        // Puff Count Input + Quick Chips
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = onQuantityChange,
                            label = { Text("Number of Puffs / Hits") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cannabis_puffs_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("1", "2", "3", "5", "8", "12").forEach { preset ->
                                FilterChip(
                                    selected = quantityText.trim() == preset,
                                    onClick = { onQuantityChange(preset) },
                                    label = { Text("$preset ${if (preset == "1") "puff" else "puffs"}") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cannabis_puff_preset_$preset")
                                )
                            }
                        }

                        // Puff Length (Seconds) Input + Quick Chips
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Puff Length (seconds)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedTextField(
                            value = puffLengthSecondsText,
                            onValueChange = onPuffLengthChange,
                            label = { Text("Average Puff Length (sec)") },
                            placeholder = { Text("3") },
                            supportingText = { Text("Scales estimated mg proportionally (3s = standard draw)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cannabis_puff_length_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("1", "2", "3", "5", "7", "10").forEach { sec ->
                                FilterChip(
                                    selected = puffLengthSecondsText.trim() == sec,
                                    onClick = { onPuffLengthChange(sec) },
                                    label = { Text("${sec}s") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cannabis_puff_len_${sec}s")
                                )
                            }
                        }
                    } else {
                        // Grams Input + Quick Chips
                        OutlinedTextField(
                            value = quantityText,
                            onValueChange = onQuantityChange,
                            label = { Text("Amount in Grams (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cannabis_grams_input")
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("0.1", "0.25", "0.5", "0.75", "1").forEach { g ->
                                FilterChip(
                                    selected = quantityText.trim() == g,
                                    onClick = { onQuantityChange(g) },
                                    label = { Text("${g}g") },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("cannabis_gram_preset_$g")
                                )
                            }
                        }
                    }

                    // Live Auto-Estimated mg Summary Box
                    Surface(
                        color = MidnightSlate900,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cannabis_session_estimate_box")
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Estimated Active Load",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatCompactAmount(estimate.totalActiveMg)} mg · t½ ${formatCompactAmount(estimate.weightedHalfLifeHours)}h",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (estimate.breakdown.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    estimate.breakdown.forEach { item ->
                                        val tint = if (item.displayName.contains("CBD", ignoreCase = true) ||
                                            item.displayName.contains("CBN", ignoreCase = true)
                                        ) {
                                            SeverityLowEmerald
                                        } else {
                                            MaterialTheme.colorScheme.secondary
                                        }
                                        Surface(
                                            color = tint.copy(alpha = 0.15f),
                                            shape = MaterialTheme.shapes.extraSmall
                                        ) {
                                            Text(
                                                text = "${item.displayName} ${formatCompactAmount(item.percent)}% (~${formatCompactAmount(item.estimatedMg)} mg)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = tint,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
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
}

@Composable
private fun CannabinoidRowEditor(
    index: Int,
    row: CannabinoidEntry,
    method: CannabisIntakeMethod,
    canRemove: Boolean,
    onChange: (CannabinoidEntry) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        color = MidnightSlate900,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cannabinoid_row_$index")
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
                Text(
                    text = "Cannabinoid ${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                if (canRemove) {
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.testTag("remove_cannabinoid_$index")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Remove cannabinoid ${index + 1}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Cannabinoid Selector Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CannabinoidType.entries.forEach { type ->
                    val selected = row.type == type
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(row.copy(type = type)) },
                        label = { Text(type.label) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("cannabinoid_${index}_${type.name}")
                    )
                }
            }

            if (row.type == CannabinoidType.CUSTOM) {
                OutlinedTextField(
                    value = row.customName,
                    onValueChange = { onChange(row.copy(customName = it)) },
                    label = { Text("Custom Cannabinoid Name") },
                    placeholder = { Text("e.g., THCV, CBC, Delta-10") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cannabinoid_custom_name_$index")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = row.percentText,
                    onValueChange = { onChange(row.copy(percentText = it)) },
                    label = { Text("Potency (%)") },
                    suffix = { Text("%") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("cannabinoid_percent_input_$index")
                )
                val quickPercents = if (method == CannabisIntakeMethod.VAPED) {
                    listOf("5", "20", "75", "85")
                } else {
                    listOf("2", "15", "22", "30")
                }
                Row(
                    modifier = Modifier
                        .weight(1.4f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickPercents.forEach { pct ->
                        FilterChip(
                            selected = row.percentText.trim() == pct,
                            onClick = { onChange(row.copy(percentText = pct)) },
                            label = { Text("$pct%") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NicotineTobaccoBuilderCard(
    method: NicotineDeliveryMethod,
    onSelectMethod: (NicotineDeliveryMethod) -> Unit,
    smokedType: SmokedTobaccoType,
    onSelectSmokedType: (SmokedTobaccoType) -> Unit,
    smokedCountText: String,
    onSmokedCountChange: (String) -> Unit,
    vapeStrengthUnit: VapeStrengthUnit,
    onSelectVapeStrengthUnit: (VapeStrengthUnit) -> Unit,
    vapeStrengthText: String,
    onVapeStrengthChange: (String) -> Unit,
    vapePuffsText: String,
    onVapePuffsChange: (String) -> Unit,
    vapePuffLengthSecondsText: String,
    onVapePuffLengthChange: (String) -> Unit,
    estimate: NicotineSessionEstimate
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSlate800.copy(alpha = 0.8f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nicotine_tobacco_builder_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (method == NicotineDeliveryMethod.SMOKED_TOBACCO) Icons.Default.LocalFireDepartment else Icons.Default.VapingRooms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Column {
                    Text(
                        text = "Tobacco / Nicotine Intake",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Select Smoked Tobacco or Vaped Nicotine and configure strength",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Delivery Method Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NicotineDeliveryMethod.entries.forEach { option ->
                    val selected = method == option
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectMethod(option) },
                        label = {
                            Text(
                                text = option.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("nicotine_method_${option.name}")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (method == NicotineDeliveryMethod.SMOKED_TOBACCO) {
                Text(
                    text = "Tobacco Strength / Type",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmokedTobaccoType.entries.forEach { type ->
                        val selected = smokedType == type
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectSmokedType(type) },
                            label = {
                                Text("${type.shortLabel} (${formatCompactAmount(type.mgPerUnit)} mg)")
                            },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("smoked_tobacco_type_${type.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = smokedCountText,
                    onValueChange = onSmokedCountChange,
                    label = { Text("Quantity (${smokedType.pluralUnit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smoked_tobacco_count_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("0.5", "1", "2", "3", "5").forEach { count ->
                        FilterChip(
                            selected = smokedCountText.trim() == count,
                            onClick = { onSmokedCountChange(count) },
                            label = { Text("$count ${smokedType.pluralUnit}") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("smoked_count_preset_$count")
                        )
                    }
                }
            } else {
                // Vaped Nicotine Controls
                Text(
                    text = "Vape E-Liquid Strength",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VapeStrengthUnit.entries.forEach { unit ->
                        val selected = vapeStrengthUnit == unit
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectVapeStrengthUnit(unit) },
                            label = { Text(unit.label) },
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("vape_strength_unit_${unit.name}")
                        )
                    }
                }

                OutlinedTextField(
                    value = vapeStrengthText,
                    onValueChange = onVapeStrengthChange,
                    label = { Text("Nicotine Strength (${vapeStrengthUnit.suffix})") },
                    suffix = { Text(vapeStrengthUnit.suffix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vape_nicotine_strength_input")
                )

                val strengthPresets = if (vapeStrengthUnit == VapeStrengthUnit.PERCENT) {
                    listOf("0.3" to "0.3%", "0.6" to "0.6%", "2" to "2%", "3" to "3%", "5" to "5%")
                } else {
                    listOf("3" to "3 mg/mL", "6" to "6 mg/mL", "12" to "12 mg/mL", "20" to "20 mg/mL", "35" to "35 mg/mL", "50" to "50 mg/mL")
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    strengthPresets.forEach { (value, label) ->
                        FilterChip(
                            selected = vapeStrengthText.trim() == value,
                            onClick = { onVapeStrengthChange(value) },
                            label = { Text(label) },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("vape_strength_preset_$value")
                        )
                    }
                }

                OutlinedTextField(
                    value = vapePuffsText,
                    onValueChange = onVapePuffsChange,
                    label = { Text("Number of Puffs") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vape_nicotine_puffs_input")
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("3", "5", "10", "15", "25", "40").forEach { puffs ->
                        FilterChip(
                            selected = vapePuffsText.trim() == puffs,
                            onClick = { onVapePuffsChange(puffs) },
                            label = { Text("$puffs puffs") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("vape_puffs_preset_$puffs")
                        )
                    }
                }

                // Puff Length (Seconds) for Vaped Nicotine
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Puff Length (seconds)",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                OutlinedTextField(
                    value = vapePuffLengthSecondsText,
                    onValueChange = onVapePuffLengthChange,
                    label = { Text("Average Puff Length (sec)") },
                    placeholder = { Text("3") },
                    supportingText = { Text("Scales absorbed nicotine mg proportionally (3s = standard draw)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nicotine_puff_length_input")
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("1", "2", "3", "4", "5", "6").forEach { sec ->
                        FilterChip(
                            selected = vapePuffLengthSecondsText.trim() == sec,
                            onClick = { onVapePuffLengthChange(sec) },
                            label = { Text("${sec}s") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("nicotine_puff_len_${sec}s")
                        )
                    }
                }
            }

            // Live Absorbed Nicotine Summary
            Surface(
                color = MidnightSlate900,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, SeverityModerateAmber.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("nicotine_session_estimate_box")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Estimated Absorbed Nicotine",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        estimate.mgPerMlEquivalent?.let { eq ->
                            Text(
                                text = "Equivalent to ${formatCompactAmount(eq)} mg/mL e-liquid",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        text = "${formatCompactAmount(estimate.absorbedMg)} mg · t½ 2h",
                        style = MaterialTheme.typography.labelLarge,
                        color = SeverityModerateAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
