package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.SubstanceLogEntity
import com.example.domain.DateTimeUtils
import com.example.domain.EditImpactPreview
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SubstanceCatalog
import com.example.domain.SubstanceCategory
import com.example.domain.SubstanceReference
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.SeverityCriticalCrimson
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeveritySevereCoral
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSubstanceSheet(
    initialDateIso: String,
    existingEntriesByDate: Map<String, List<SubstanceLogEntity>>,
    targetBedtimeMinutes: Int,
    editingEntry: SubstanceLogEntity? = null,
    onDismiss: () -> Unit,
    onRequestDelete: ((SubstanceLogEntity) -> Unit)? = null,
    onSave: (
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
    ) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val initialRef = remember(editingEntry) {
        editingEntry?.let { SubstanceCatalog.findById(it.substanceId) }
    }

    var selectedDateIso by remember(editingEntry, initialDateIso) {
        mutableStateOf(editingEntry?.dateIso ?: initialDateIso)
    }
    var searchQuery by remember(editingEntry) {
        mutableStateOf(editingEntry?.substanceName ?: "")
    }
    var selectedCategoryFilter by remember { mutableStateOf<SubstanceCategory?>(null) }
    var dropdownExpanded by remember(editingEntry) { mutableStateOf(editingEntry == null) }

    var selectedRef by remember(editingEntry) { mutableStateOf(initialRef) }
    var isCustomMode by remember(editingEntry) {
        mutableStateOf(editingEntry != null && initialRef == null)
    }

    var customCategory by remember(editingEntry) {
        mutableStateOf(
            SubstanceCategory.entries.find { it.label == editingEntry?.category }
                ?: SubstanceCategory.STIMULANT
        )
    }
    var dosageText by remember(editingEntry) {
        mutableStateOf(
            editingEntry?.dosageAmount?.let { formatCompactAmount(it) } ?: "95"
        )
    }
    var unitText by remember(editingEntry) {
        mutableStateOf(editingEntry?.dosageUnit ?: "mg")
    }
    var halfLifeText by remember(editingEntry) {
        mutableStateOf(editingEntry?.halfLifeHours?.toString() ?: "5.0")
    }
    var disruptionWeight by remember(editingEntry) {
        mutableStateOf(editingEntry?.disruptionWeight ?: 1.0)
    }
    var notesText by remember(editingEntry) {
        mutableStateOf(editingEntry?.notes ?: "")
    }

    val initialTimeMillis = editingEntry?.takenAtMillis ?: run {
        val nowPair = DateTimeUtils.hourAndMinuteFromMillis(System.currentTimeMillis())
        DateTimeUtils.combineDateAndTime(initialDateIso, nowPair.first, nowPair.second)
    }
    val (initHour, initMinute) = remember(initialTimeMillis) {
        DateTimeUtils.hourAndMinuteFromMillis(initialTimeMillis)
    }
    var takenHour by remember(editingEntry) { mutableStateOf(initHour) }
    var takenMinute by remember(editingEntry) { mutableStateOf(initMinute) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val filteredSubstances = remember(searchQuery, selectedCategoryFilter) {
        SubstanceCatalog.search(searchQuery, selectedCategoryFilter)
    }
    val exactPresetMatch = remember(searchQuery) {
        SubstanceCatalog.items.any { it.name.equals(searchQuery.trim(), ignoreCase = true) }
    }
    val takenAtMillis = remember(selectedDateIso, takenHour, takenMinute) {
        DateTimeUtils.combineDateAndTime(selectedDateIso, takenHour, takenMinute)
    }

    val parsedDosage = dosageText.toDoubleOrNull()
    val parsedHalfLife = halfLifeText.toDoubleOrNull() ?: selectedRef?.halfLifeHours ?: 4.5
    val validName = (selectedRef?.name ?: searchQuery).trim()
    val canSave = validName.isNotBlank() && parsedDosage != null && parsedDosage > 0.0

    val resolvedWeight = selectedRef?.disruptionWeight ?: when {
        editingEntry != null && selectedRef == null && editingEntry.substanceName.equals(validName, ignoreCase = true) ->
            editingEntry.disruptionWeight
        else -> when (customCategory) {
            SubstanceCategory.STIMULANT -> 1.25
            SubstanceCategory.DEPRESSANT -> 1.0
            SubstanceCategory.MEDICATION -> 0.85
            SubstanceCategory.METABOLIC -> 0.7
            SubstanceCategory.CUSTOM -> 1.0
        }
    }

    val livePreview: EditImpactPreview? = remember(
        selectedDateIso,
        existingEntriesByDate,
        editingEntry,
        validName,
        parsedDosage,
        unitText,
        takenAtMillis,
        parsedHalfLife,
        resolvedWeight
    ) {
        if (validName.isBlank() || parsedDosage == null || parsedDosage <= 0.0) {
            null
        } else {
            val origDate = editingEntry?.dateIso ?: selectedDateIso
            val candidate = SubstanceLogEntity(
                id = editingEntry?.id ?: -1L,
                dateIso = selectedDateIso,
                substanceId = selectedRef?.id ?: editingEntry?.substanceId ?: "custom_preview",
                substanceName = validName,
                category = selectedRef?.category?.label ?: customCategory.label,
                dosageAmount = parsedDosage.coerceAtLeast(0.01),
                dosageUnit = unitText.trim().ifBlank { "mg" },
                takenAtMillis = takenAtMillis,
                halfLifeHours = parsedHalfLife.coerceAtLeast(0.25),
                disruptionWeight = resolvedWeight.coerceAtLeast(0.05),
                notes = notesText
            )
            PharmacokineticsEngine.previewEditImpact(
                originalDateIso = origDate,
                originalDayEntries = existingEntriesByDate[origDate].orEmpty(),
                targetDayEntries = existingEntriesByDate[selectedDateIso].orEmpty(),
                editingEntry = editingEntry,
                candidateEntry = candidate,
                targetBedtimeMinutes = targetBedtimeMinutes
            )
        }
    }

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
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header with Edit/Add Title, Date Badge, Delete (in edit mode), and Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (editingEntry == null) "Log Substance" else "Edit Entry",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    AnimatedContent(
                        targetState = DateTimeUtils.formatDisplayDate(selectedDateIso),
                        transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                        label = "sheetSelectedDateLabel"
                    ) { dateLabel ->
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (editingEntry != null && onRequestDelete != null) {
                        IconButton(
                            onClick = { onRequestDelete(editingEntry) },
                            modifier = Modifier.testTag("sheet_delete_entry_button")
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete entry",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_log_sheet_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close log sheet")
                    }
                }
            }

            // Live Bedtime Impact Preview Card
            AnimatedVisibility(
                visible = livePreview != null,
                enter = expandVertically(
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140))
            ) {
                livePreview?.let { preview ->
                    LiveImpactPreviewCard(
                        preview = preview,
                        unit = unitText.trim().ifBlank { "mg" },
                        targetBedtimeMinutes = targetBedtimeMinutes,
                        originalDateIso = editingEntry?.dateIso ?: selectedDateIso,
                        targetDateIso = selectedDateIso,
                        isEditMode = editingEntry != null
                    )
                }
            }

            // Searchable Autocomplete Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    searchQuery = newValue
                    dropdownExpanded = true
                    if (selectedRef != null && !selectedRef!!.name.equals(newValue, ignoreCase = true)) {
                        selectedRef = null
                    }
                },
                label = { Text("Search or type custom substance") },
                placeholder = { Text("Coffee, Adderall, Alcohol, Melatonin...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = "Search substances")
                },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    selectedRef = null
                                    isCustomMode = false
                                    dropdownExpanded = true
                                },
                                modifier = Modifier.testTag("clear_substance_search")
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear search")
                            }
                        }
                        IconButton(
                            onClick = { dropdownExpanded = !dropdownExpanded },
                            modifier = Modifier.testTag("toggle_substance_dropdown")
                        ) {
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (dropdownExpanded) "Collapse substance list" else "Expand substance list"
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("substance_search_input")
            )

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterOptions: List<Pair<String, SubstanceCategory?>> = listOf(
                    "All" to null,
                    "Stimulants" to SubstanceCategory.STIMULANT,
                    "Depressants" to SubstanceCategory.DEPRESSANT,
                    "Medications" to SubstanceCategory.MEDICATION,
                    "Meals" to SubstanceCategory.METABOLIC
                )
                filterOptions.forEach { (label, cat) ->
                    val selected = selectedCategoryFilter == cat
                    FilterChip(
                        selected = selected,
                        onClick = {
                            selectedCategoryFilter = cat
                            dropdownExpanded = true
                        },
                        label = { Text(label) },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("category_filter_${cat?.name ?: "ALL"}")
                    )
                }
            }

            // Expandable Autocomplete Dropdown Card
            AnimatedVisibility(visible = dropdownExpanded) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(filteredSubstances, key = { it.id }) { ref ->
                            val isSelected = selectedRef?.id == ref.id && !isCustomMode
                            SubstanceDropdownRow(
                                ref = ref,
                                isSelected = isSelected,
                                onClick = {
                                    selectedRef = ref
                                    isCustomMode = false
                                    searchQuery = ref.name
                                    dosageText = formatCompactAmount(ref.defaultDosage)
                                    unitText = ref.defaultUnit
                                    halfLifeText = ref.halfLifeHours.toString()
                                    disruptionWeight = ref.disruptionWeight
                                    dropdownExpanded = false
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        if (searchQuery.isNotBlank() && !exactPresetMatch) {
                            item(key = "custom_entry_option") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRef = null
                                            isCustomMode = true
                                            dropdownExpanded = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                        .testTag("select_custom_substance_option"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = "Add custom substance",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Use custom entry: \"${searchQuery.trim()}\"",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Configure custom half-life, unit, and category",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Custom parameters
            val effectiveCustom = isCustomMode || (selectedRef == null && searchQuery.isNotBlank())
            AnimatedVisibility(visible = effectiveCustom) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Custom Substance Parameters",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            SubstanceCategory.STIMULANT,
                            SubstanceCategory.DEPRESSANT,
                            SubstanceCategory.MEDICATION,
                            SubstanceCategory.METABOLIC,
                            SubstanceCategory.CUSTOM
                        ).forEach { cat ->
                            FilterChip(
                                selected = customCategory == cat,
                                onClick = { customCategory = cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = halfLifeText,
                        onValueChange = { halfLifeText = it },
                        label = { Text("Estimated Half-Life (hours)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_halflife_input")
                    )
                }
            }

            // Dosage + Unit Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = dosageText,
                    onValueChange = { dosageText = it },
                    label = { Text("Dosage / Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1.4f)
                        .testTag("dosage_amount_input")
                )
                OutlinedTextField(
                    value = unitText,
                    onValueChange = { unitText = it },
                    label = { Text("Unit") },
                    placeholder = { Text("mg, cups") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dosage_unit_input")
                )
            }

            // Tactile Dose Quick-Chips
            val currentDoseVal = parsedDosage ?: (selectedRef?.defaultDosage ?: 10.0)
            val doseStep = PharmacokineticsEngine.computeSmartDoseStep(unitText, currentDoseVal)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SheetQuickChip(
                    label = "0.5x",
                    onClick = {
                        dosageText = formatCompactAmount((currentDoseVal * 0.5).coerceAtLeast(0.25))
                    },
                    tag = "sheet_dose_half"
                )
                SheetQuickChip(
                    label = "-${formatCompactAmount(doseStep)}",
                    onClick = {
                        dosageText = formatCompactAmount((currentDoseVal - doseStep).coerceAtLeast(0.25))
                    },
                    tag = "sheet_dose_minus"
                )
                SheetQuickChip(
                    label = "+${formatCompactAmount(doseStep)}",
                    onClick = {
                        dosageText = formatCompactAmount(currentDoseVal + doseStep)
                    },
                    tag = "sheet_dose_plus"
                )
                SheetQuickChip(
                    label = "1.5x",
                    onClick = {
                        dosageText = formatCompactAmount(currentDoseVal * 1.5)
                    },
                    tag = "sheet_dose_15x"
                )
            }

            // Date & Time Taken Row (Cross-date reassignment + TimePicker)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("date_picker_button")
                ) {
                    Icon(Icons.Default.CalendarToday, contentDescription = "Select entry date", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatDisplayDate(selectedDateIso),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = { showTimePicker = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .testTag("time_taken_picker_button")
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = "Select intake time", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = DateTimeUtils.formatTime(takenAtMillis),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Tactile Time Quick-Shift Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val shiftTime: (Int) -> Unit = { deltaMinutes ->
                    val shiftedMillis = takenAtMillis + deltaMinutes * 60_000L
                    selectedDateIso = DateTimeUtils.dateIsoFromMillis(shiftedMillis)
                    val (h, m) = DateTimeUtils.hourAndMinuteFromMillis(shiftedMillis)
                    takenHour = h
                    takenMinute = m
                }
                SheetQuickChip(label = "-1h", onClick = { shiftTime(-60) }, tag = "sheet_time_minus_1h")
                SheetQuickChip(label = "-15m", onClick = { shiftTime(-15) }, tag = "sheet_time_minus_15m")
                SheetQuickChip(label = "+15m", onClick = { shiftTime(15) }, tag = "sheet_time_plus_15m")
                SheetQuickChip(label = "+1h", onClick = { shiftTime(60) }, tag = "sheet_time_plus_1h")
            }

            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text("Optional Notes") },
                placeholder = { Text("e.g., Cold brew on empty stomach") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("substance_notes_input")
            )

            val (saveInteraction, saveScale) = rememberPressScale()
            Button(
                onClick = {
                    val chosenRef = selectedRef
                    val finalId = chosenRef?.id
                        ?: editingEntry?.substanceId?.takeIf { editingEntry.substanceName.equals(validName, ignoreCase = true) }
                        ?: "custom_${validName.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                    val finalCategory = chosenRef?.category?.label ?: customCategory.label
                    onSave(
                        selectedDateIso,
                        finalId,
                        validName,
                        finalCategory,
                        parsedDosage ?: 1.0,
                        unitText.trim().ifBlank { "mg" },
                        takenAtMillis,
                        parsedHalfLife.coerceAtLeast(0.25),
                        resolvedWeight,
                        notesText,
                        editingEntry?.id
                    )
                },
                enabled = canSave,
                interactionSource = saveInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .graphicsLayer {
                        scaleX = saveScale
                        scaleY = saveScale
                    }
                    .testTag("save_substance_button")
            ) {
                Text(
                    when {
                        editingEntry == null -> "Save to ${DateTimeUtils.formatDisplayDate(selectedDateIso)}"
                        selectedDateIso != editingEntry.dateIso -> "Move & Save to ${DateTimeUtils.formatDisplayDate(selectedDateIso)}"
                        else -> "Update Entry"
                    }
                )
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = takenHour,
            initialMinute = takenMinute,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Intake Time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        takenHour = timePickerState.hour
                        takenMinute = timePickerState.minute
                        showTimePicker = false
                    },
                    modifier = Modifier.testTag("confirm_time_picker")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = DateTimeUtils.dateIsoToUtcMidnightMillis(selectedDateIso)
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMs ->
                            selectedDateIso = DateTimeUtils.utcMidnightMillisToDateIso(utcMs)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("confirm_date_picker")
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun LiveImpactPreviewCard(
    preview: EditImpactPreview,
    unit: String,
    targetBedtimeMinutes: Int,
    originalDateIso: String,
    targetDateIso: String,
    isEditMode: Boolean
) {
    val tierColor by animateColorAsState(
        targetValue = colorForSeverity(preview.afterSeverityTier),
        animationSpec = tween(260),
        label = "previewTierColor"
    )
    val animatedAfterScore by animateIntAsState(
        targetValue = preview.afterDayScore,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "previewScore"
    )
    val animatedResidueFrac by animateFloatAsState(
        targetValue = (preview.afterEntryPercentAtBed / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "previewResidueFrac"
    )

    val deltaColor by animateColorAsState(
        targetValue = when {
            preview.scoreDelta < 0 -> SeverityLowEmerald
            preview.scoreDelta > 0 -> SeveritySevereCoral
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220),
        label = "previewDeltaColor"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MidnightSlate800),
        border = BorderStroke(1.dp, tierColor.copy(alpha = 0.65f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_impact_preview_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "LIVE BEDTIME PREVIEW (${DateTimeUtils.formatMinutesOfDay(targetBedtimeMinutes)})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    val afterMg = String.format(Locale.US, "%.1f", preview.afterEntryRemainingAtBed)
                    val afterPct = preview.afterEntryPercentAtBed.toInt()
                    val residueText = if (isEditMode && preview.beforeEntryRemainingAtBed != null && preview.beforeEntryPercentAtBed != null) {
                        val beforeMg = String.format(Locale.US, "%.1f", preview.beforeEntryRemainingAtBed)
                        val beforePct = preview.beforeEntryPercentAtBed.toInt()
                        "$beforeMg $unit ($beforePct%) → $afterMg $unit ($afterPct%)"
                    } else {
                        "$afterMg $unit ($afterPct%) active at bed"
                    }
                    Text(
                        text = residueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SCORE ${preview.beforeDayScore} → $animatedAfterScore",
                        style = MaterialTheme.typography.labelMedium,
                        color = tierColor,
                        fontWeight = FontWeight.Bold
                    )
                    AnimatedContent(targetState = preview.scoreDelta, label = "deltaPill") { delta ->
                        val deltaLabel = when {
                            delta < 0 -> "$delta RISK ↓"
                            delta > 0 -> "+$delta RISK ↑"
                            else -> "NO CHANGE"
                        }
                        Surface(
                            color = deltaColor.copy(alpha = 0.16f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = deltaLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = deltaColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            LinearProgressIndicator(
                progress = { animatedResidueFrac },
                color = tierColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val clearLabel = if (preview.afterHoursUntilCleared <= 0.08) {
                    "Cleared (<10% active)"
                } else {
                    "Clears (<10%) in ${DateTimeUtils.formatDurationHours(preview.afterHoursUntilCleared)}"
                }
                Text(
                    text = clearLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (preview.isMovedToDifferentDate) {
                    Text(
                        text = "Moving ${DateTimeUtils.formatDisplayDate(originalDateIso)} (${preview.movedTargetScoreWithoutMove}) → ${DateTimeUtils.formatDisplayDate(targetDateIso)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetQuickChip(
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    val (interactionSource, pressScale) = rememberPressScale(pressedScale = 0.93f)
    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun SubstanceDropdownRow(
    ref: SubstanceReference,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("substance_option_${ref.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ref.name,
                style = MaterialTheme.typography.titleSmall,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${ref.category.label} • Default ${formatCompactAmount(ref.defaultDosage)} ${ref.defaultUnit} • t½ ${ref.halfLifeHours}h",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
