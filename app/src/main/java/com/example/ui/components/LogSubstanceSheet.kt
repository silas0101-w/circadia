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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.data.remote.HalfLifeSource
import com.example.data.remote.OpenFdaPharmacokinetics
import com.example.data.remote.RemoteMedicationItem
import com.example.data.repository.MedicationSearchResult
import com.example.domain.CannabinoidEntry
import com.example.domain.CannabinoidType
import com.example.domain.CannabisIntakeMethod
import com.example.domain.CannabisQuantityMode
import com.example.domain.DateTimeUtils
import com.example.domain.DecayModelMode
import com.example.domain.EditImpactPreview
import com.example.domain.InhaledIntakeCalculator
import com.example.domain.NicotineDeliveryMethod
import com.example.domain.PharmacokineticsEngine
import com.example.domain.SmokedTobaccoType
import com.example.domain.StructuredSessionMetadata
import com.example.domain.SubstanceCatalog
import com.example.domain.SubstanceCategory
import com.example.domain.VapeStrengthUnit
import com.example.ui.theme.MidnightSlate800
import com.example.ui.theme.SeverityLowEmerald
import com.example.ui.theme.SeveritySevereCoral
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSubstanceSheet(
    initialDateIso: String,
    existingEntriesByDate: Map<String, List<SubstanceLogEntity>>,
    targetBedtimeMinutes: Int,
    decayModelMode: DecayModelMode = DecayModelMode.ONSET_RAMP,
    editingEntry: SubstanceLogEntity? = null,
    onSearchMedications: suspend (String, SubstanceCategory?) -> MedicationSearchResult = { q, cat ->
        MedicationSearchResult(SubstanceCatalog.search(q, cat), emptyList(), false, false)
    },
    onResolveMedicationPk: suspend (RemoteMedicationItem) -> OpenFdaPharmacokinetics = {
        OpenFdaPharmacokinetics(5.0, 45, null, HalfLifeSource.ESTIMATED)
    },
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
    var selectedRemote by remember(editingEntry) { mutableStateOf<RemoteMedicationItem?>(null) }
    var isCustomMode by remember(editingEntry) {
        mutableStateOf(editingEntry != null && initialRef == null)
    }

    var remoteMatches by remember { mutableStateOf<List<RemoteMedicationItem>>(emptyList()) }
    var isSearchingRemote by remember { mutableStateOf(false) }
    var remoteFromCache by remember { mutableStateOf(false) }
    var remoteNetworkOffline by remember { mutableStateOf(false) }

    var halfLifeSource by remember(editingEntry) {
        mutableStateOf(
            if (initialRef != null) HalfLifeSource.CURATED else HalfLifeSource.ESTIMATED
        )
    }
    var isFetchingOpenFda by remember { mutableStateOf(false) }
    var fdaExcerpt by remember(editingEntry) { mutableStateOf<String?>(null) }
    var showHalfLifeEditor by remember(editingEntry) {
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
        mutableStateOf(editingEntry?.halfLifeHours?.let { formatCompactAmount(it) } ?: "5")
    }
    var disruptionWeight by remember(editingEntry) {
        mutableStateOf(editingEntry?.disruptionWeight ?: 1.0)
    }
    var notesText by remember(editingEntry) {
        mutableStateOf(
            editingEntry?.let { StructuredSessionMetadata.stripStructuredTag(it.notes) } ?: ""
        )
    }

    val initialCannabisEditState = remember(editingEntry) {
        editingEntry?.takeIf { StructuredSessionMetadata.isCannabisSubstanceId(it.substanceId) }?.let { entry ->
            StructuredSessionMetadata.parseCannabisStateFromNotes(entry.substanceId, entry.notes)
        }
    }
    val initialNicotineEditState = remember(editingEntry) {
        editingEntry?.takeIf { StructuredSessionMetadata.isNicotineBuilderSubstanceId(it.substanceId) }?.let { entry ->
            StructuredSessionMetadata.parseNicotineStateFromNotes(entry.substanceId, entry.notes)
        }
    }

    // Cannabis / THC multi-cannabinoid & puff-length builder state
    var cannabisMethod by remember(editingEntry) {
        mutableStateOf(
            initialCannabisEditState?.method
                ?: editingEntry?.takeIf { StructuredSessionMetadata.isCannabisSubstanceId(it.substanceId) }
                    ?.let { StructuredSessionMetadata.defaultCannabisMethodForId(it.substanceId) }
                ?: CannabisIntakeMethod.VAPED
        )
    }
    var cannabisQuantityMode by remember(editingEntry) {
        mutableStateOf(
            initialCannabisEditState?.quantityMode
                ?: if (cannabisMethod == CannabisIntakeMethod.SMOKED) CannabisQuantityMode.GRAMS else CannabisQuantityMode.PUFFS
        )
    }
    var cannabisQuantityText by remember(editingEntry) {
        mutableStateOf(
            initialCannabisEditState?.quantityText
                ?: if (cannabisQuantityMode == CannabisQuantityMode.GRAMS) "0.25" else "3"
        )
    }
    var cannabisPuffLengthText by remember(editingEntry) {
        mutableStateOf(initialCannabisEditState?.puffLengthSecondsText ?: "3")
    }
    var cannabinoids by remember(editingEntry) {
        mutableStateOf(
            initialCannabisEditState?.cannabinoids ?: listOf(
                CannabinoidEntry(
                    rowId = 1L,
                    type = CannabinoidType.DELTA_9_THC,
                    percentText = if (cannabisMethod == CannabisIntakeMethod.SMOKED) "22" else "80"
                )
            )
        )
    }
    var nextCannabinoidRowId by remember(editingEntry) {
        mutableStateOf((cannabinoids.maxOfOrNull { it.rowId } ?: 1L) + 1L)
    }

    // Tobacco / Nicotine intake builder state
    var nicotineMethod by remember(editingEntry) {
        mutableStateOf(
            initialNicotineEditState?.method
                ?: editingEntry?.takeIf { StructuredSessionMetadata.isNicotineBuilderSubstanceId(it.substanceId) }
                    ?.let { StructuredSessionMetadata.defaultNicotineMethodForId(it.substanceId) }
                ?: NicotineDeliveryMethod.VAPED_NICOTINE
        )
    }
    var smokedTobaccoType by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.smokedType ?: SmokedTobaccoType.FULL_FLAVOR_CIG)
    }
    var smokedTobaccoCountText by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.smokedCountText ?: "1")
    }
    var vapeStrengthUnit by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.vapeStrengthUnit ?: VapeStrengthUnit.PERCENT)
    }
    var vapeStrengthText by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.vapeStrengthText ?: "5")
    }
    var vapePuffsText by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.vapePuffsText ?: "10")
    }
    var vapePuffLengthText by remember(editingEntry) {
        mutableStateOf(initialNicotineEditState?.vapePuffLengthSecondsText ?: "3")
    }

    fun syncCannabisEstimate(
        method: CannabisIntakeMethod = cannabisMethod,
        qtyMode: CannabisQuantityMode = cannabisQuantityMode,
        qtyText: String = cannabisQuantityText,
        puffSecText: String = cannabisPuffLengthText,
        rows: List<CannabinoidEntry> = cannabinoids
    ) {
        if (method == CannabisIntakeMethod.DRANK_ATE) {
            unitText = "mg"
            halfLifeText = formatCompactAmount(method.defaultHalfLifeHours)
            disruptionWeight = 1.2
            return
        }
        val est = InhaledIntakeCalculator.estimateCannabisSession(
            method = method,
            quantityMode = qtyMode,
            quantityValue = parseDecimalInput(qtyText) ?: 0.0,
            puffLengthSeconds = parseDecimalInput(puffSecText) ?: InhaledIntakeCalculator.BASELINE_PUFF_SECONDS,
            cannabinoids = rows
        )
        if (est.totalActiveMg > 0.0) {
            dosageText = formatCompactAmount(est.totalActiveMg)
        }
        unitText = "mg"
        halfLifeText = formatCompactAmount(est.weightedHalfLifeHours)
        disruptionWeight = est.weightedDisruptionWeight
    }

    fun syncNicotineEstimate(
        method: NicotineDeliveryMethod = nicotineMethod,
        sType: SmokedTobaccoType = smokedTobaccoType,
        sCountText: String = smokedTobaccoCountText,
        vUnit: VapeStrengthUnit = vapeStrengthUnit,
        vStrengthText: String = vapeStrengthText,
        vPuffsText: String = vapePuffsText,
        vPuffLenText: String = vapePuffLengthText
    ) {
        val est = when (method) {
            NicotineDeliveryMethod.SMOKED_TOBACCO -> InhaledIntakeCalculator.estimateSmokedTobacco(
                type = sType,
                count = parseDecimalInput(sCountText) ?: 0.0
            )
            NicotineDeliveryMethod.VAPED_NICOTINE -> InhaledIntakeCalculator.estimateVapedNicotine(
                strengthValue = parseDecimalInput(vStrengthText) ?: 0.0,
                strengthUnit = vUnit,
                puffs = parseDecimalInput(vPuffsText) ?: 0.0,
                puffLengthSeconds = parseDecimalInput(vPuffLenText) ?: InhaledIntakeCalculator.BASELINE_PUFF_SECONDS
            )
        }
        if (est.absorbedMg > 0.0) {
            dosageText = formatCompactAmount(est.absorbedMg)
        }
        unitText = "mg"
        halfLifeText = formatCompactAmount(est.halfLifeHours)
        disruptionWeight = est.disruptionWeight
    }

    // Debounced live NIH RxTerms search whenever the user types 2+ characters
    LaunchedEffect(searchQuery, selectedCategoryFilter, dropdownExpanded) {
        val trimmed = searchQuery.trim()
        if (!dropdownExpanded || trimmed.length < 2) {
            isSearchingRemote = false
            if (trimmed.length < 2) {
                remoteMatches = emptyList()
                remoteFromCache = false
                remoteNetworkOffline = false
            }
            return@LaunchedEffect
        }
        isSearchingRemote = true
        delay(320L)
        val result = runCatching { onSearchMedications(trimmed, selectedCategoryFilter) }.getOrNull()
        if (result != null) {
            remoteMatches = result.remoteMatches
            remoteFromCache = result.fromOfflineCache
            remoteNetworkOffline = result.networkUnavailable
        } else {
            remoteMatches = emptyList()
            remoteNetworkOffline = true
        }
        isSearchingRemote = false
    }

    // Automatic openFDA half-life resolution whenever a live NIH RxTerms medication is selected
    LaunchedEffect(selectedRemote?.id) {
        val remote = selectedRemote ?: return@LaunchedEffect
        isFetchingOpenFda = true
        fdaExcerpt = null
        val pk = runCatching { onResolveMedicationPk(remote) }.getOrNull()
        if (pk != null) {
            pk.halfLifeHours?.let { hl ->
                halfLifeText = formatCompactAmount(hl)
            }
            halfLifeSource = pk.source
            fdaExcerpt = pk.excerpt
            // Automatically show editable half-life input when falling back to an estimate
            if (pk.source == HalfLifeSource.ESTIMATED) {
                showHalfLifeEditor = true
            }
        } else {
            halfLifeSource = HalfLifeSource.ESTIMATED
            showHalfLifeEditor = true
        }
        isFetchingOpenFda = false
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
    val exactPresetMatch = remember(searchQuery, remoteMatches) {
        val q = searchQuery.trim()
        SubstanceCatalog.items.any { it.name.equals(q, ignoreCase = true) } ||
            remoteMatches.any { it.displayName.equals(q, ignoreCase = true) || it.cleanName.equals(q, ignoreCase = true) }
    }
    val takenAtMillis = remember(selectedDateIso, takenHour, takenMinute) {
        DateTimeUtils.combineDateAndTime(selectedDateIso, takenHour, takenMinute)
    }

    val isCannabisBuilderActive = !isCustomMode && selectedRef != null &&
        StructuredSessionMetadata.isCannabisSubstanceId(selectedRef!!.id)
    val isNicotineBuilderActive = !isCustomMode && selectedRef != null &&
        StructuredSessionMetadata.isNicotineBuilderSubstanceId(selectedRef!!.id)

    val cannabisEstimate = remember(
        cannabisMethod,
        cannabisQuantityMode,
        cannabisQuantityText,
        cannabisPuffLengthText,
        cannabinoids,
        dosageText
    ) {
        val qtyVal = if (cannabisMethod == CannabisIntakeMethod.DRANK_ATE) {
            parseDecimalInput(dosageText) ?: 10.0
        } else {
            parseDecimalInput(cannabisQuantityText) ?: 0.0
        }
        val puffSec = parseDecimalInput(cannabisPuffLengthText) ?: InhaledIntakeCalculator.BASELINE_PUFF_SECONDS
        InhaledIntakeCalculator.estimateCannabisSession(
            method = cannabisMethod,
            quantityMode = cannabisQuantityMode,
            quantityValue = qtyVal,
            puffLengthSeconds = puffSec,
            cannabinoids = cannabinoids
        )
    }

    val nicotineEstimate = remember(
        nicotineMethod,
        smokedTobaccoType,
        smokedTobaccoCountText,
        vapeStrengthUnit,
        vapeStrengthText,
        vapePuffsText,
        vapePuffLengthText
    ) {
        when (nicotineMethod) {
            NicotineDeliveryMethod.SMOKED_TOBACCO -> InhaledIntakeCalculator.estimateSmokedTobacco(
                type = smokedTobaccoType,
                count = parseDecimalInput(smokedTobaccoCountText) ?: 0.0
            )
            NicotineDeliveryMethod.VAPED_NICOTINE -> InhaledIntakeCalculator.estimateVapedNicotine(
                strengthValue = parseDecimalInput(vapeStrengthText) ?: 0.0,
                strengthUnit = vapeStrengthUnit,
                puffs = parseDecimalInput(vapePuffsText) ?: 0.0,
                puffLengthSeconds = parseDecimalInput(vapePuffLengthText) ?: InhaledIntakeCalculator.BASELINE_PUFF_SECONDS
            )
        }
    }

    val parsedDosage = parseDecimalInput(dosageText)
    val parsedHalfLife = parseDecimalInput(halfLifeText) ?: selectedRef?.halfLifeHours ?: 4.5
    val validName = (selectedRef?.name ?: selectedRemote?.displayName ?: searchQuery).trim()
    val canSave = validName.isNotBlank() && parsedDosage != null && parsedDosage > 0.0

    val resolvedCategory = selectedRef?.category
        ?: selectedRemote?.inferredCategory
        ?: customCategory

    val resolvedWeight = when {
        isCannabisBuilderActive || isNicotineBuilderActive -> disruptionWeight
        selectedRef != null -> selectedRef!!.disruptionWeight
        editingEntry != null && selectedRemote == null &&
            editingEntry.substanceName.equals(validName, ignoreCase = true) ->
            editingEntry.disruptionWeight
        else -> when (resolvedCategory) {
            SubstanceCategory.STIMULANT -> 1.35
            SubstanceCategory.DEPRESSANT -> 1.05
            SubstanceCategory.MEDICATION -> 0.8
            SubstanceCategory.METABOLIC -> 0.7
            SubstanceCategory.CUSTOM -> 1.0
        }
    }

    val activeDoseOptions = remember(selectedRef, selectedRemote) {
        selectedRef?.doseOptions ?: selectedRemote?.strengthOptions.orEmpty()
    }
    val activeFormulationTitle = remember(selectedRef, selectedRemote) {
        selectedRef?.formulation ?: selectedRemote?.routeForm.orEmpty()
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
        resolvedWeight,
        resolvedCategory,
        decayModelMode
    ) {
        if (validName.isBlank() || parsedDosage == null || parsedDosage <= 0.0) {
            null
        } else {
            val origDate = editingEntry?.dateIso ?: selectedDateIso
            val candidate = SubstanceLogEntity(
                id = editingEntry?.id ?: -1L,
                dateIso = selectedDateIso,
                substanceId = selectedRef?.id ?: selectedRemote?.id ?: editingEntry?.substanceId ?: "custom_preview",
                substanceName = validName,
                category = resolvedCategory.label,
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
                targetBedtimeMinutes = targetBedtimeMinutes,
                decayModel = decayModelMode
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

            // Searchable Hybrid Autocomplete Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { newValue ->
                    searchQuery = newValue
                    dropdownExpanded = true
                    if (selectedRef != null && !selectedRef!!.name.equals(newValue, ignoreCase = true)) {
                        selectedRef = null
                    }
                    if (selectedRemote != null && !selectedRemote!!.displayName.equals(newValue, ignoreCase = true)) {
                        selectedRemote = null
                    }
                },
                label = { Text("Search medication, brand, or substance") },
                placeholder = { Text("Adderall XR, THC Vape, Nicotine, Coffee, Melatonin...") },
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
                                    selectedRemote = null
                                    isCustomMode = false
                                    fdaExcerpt = null
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
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("category_filter_${cat?.name ?: "ALL"}")
                    )
                }
            }

            // Expandable Hybrid Autocomplete Dropdown Card (Curated PK + Live NIH RxTerms)
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
                            .heightIn(max = 260.dp)
                    ) {
                        if (filteredSubstances.isNotEmpty()) {
                            item(key = "header_curated") {
                                MedicationSectionHeader(
                                    title = "CURATED PHARMACOKINETICS",
                                    badgeText = "${filteredSubstances.size} formulations"
                                )
                            }
                            items(filteredSubstances, key = { it.id }) { ref ->
                                val isSelected = selectedRef?.id == ref.id && !isCustomMode
                                CuratedSubstanceRow(
                                    ref = ref,
                                    isSelected = isSelected,
                                    onClick = {
                                        selectedRef = ref
                                        selectedRemote = null
                                        isCustomMode = false
                                        searchQuery = ref.name
                                        halfLifeSource = HalfLifeSource.CURATED
                                        fdaExcerpt = null
                                        showHalfLifeEditor = false
                                        dropdownExpanded = false

                                        when {
                                            StructuredSessionMetadata.isCannabisSubstanceId(ref.id) -> {
                                                val method = StructuredSessionMetadata.defaultCannabisMethodForId(ref.id)
                                                val qtyMode = if (method == CannabisIntakeMethod.SMOKED) {
                                                    CannabisQuantityMode.GRAMS
                                                } else {
                                                    CannabisQuantityMode.PUFFS
                                                }
                                                val qtyText = if (qtyMode == CannabisQuantityMode.GRAMS) "0.25" else "3"
                                                val defaultRows = listOf(
                                                    CannabinoidEntry(
                                                        rowId = 1L,
                                                        type = CannabinoidType.DELTA_9_THC,
                                                        percentText = if (method == CannabisIntakeMethod.SMOKED) "22" else "80"
                                                    )
                                                )
                                                cannabisMethod = method
                                                cannabisQuantityMode = qtyMode
                                                cannabisQuantityText = qtyText
                                                cannabisPuffLengthText = "3"
                                                cannabinoids = defaultRows
                                                nextCannabinoidRowId = 2L
                                                if (method == CannabisIntakeMethod.DRANK_ATE) {
                                                    dosageText = formatCompactAmount(ref.defaultDosage)
                                                    unitText = ref.defaultUnit
                                                    halfLifeText = formatCompactAmount(ref.halfLifeHours)
                                                    disruptionWeight = ref.disruptionWeight
                                                } else {
                                                    syncCannabisEstimate(
                                                        method = method,
                                                        qtyMode = qtyMode,
                                                        qtyText = qtyText,
                                                        puffSecText = "3",
                                                        rows = defaultRows
                                                    )
                                                }
                                            }
                                            StructuredSessionMetadata.isNicotineBuilderSubstanceId(ref.id) -> {
                                                val method = StructuredSessionMetadata.defaultNicotineMethodForId(ref.id)
                                                nicotineMethod = method
                                                syncNicotineEstimate(method = method)
                                            }
                                            else -> {
                                                dosageText = formatCompactAmount(ref.defaultDosage)
                                                unitText = ref.defaultUnit
                                                halfLifeText = formatCompactAmount(ref.halfLifeHours)
                                                disruptionWeight = ref.disruptionWeight
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        if (searchQuery.trim().length >= 2) {
                            item(key = "header_nih_rxterms") {
                                val headerTitle = when {
                                    remoteFromCache && remoteNetworkOffline -> "NIH RXTERMS (OFFLINE CACHE)"
                                    remoteFromCache -> "NIH RXTERMS (CACHED)"
                                    else -> "NIH RXTERMS LIVE CATALOG"
                                }
                                val badge = when {
                                    isSearchingRemote -> null
                                    remoteNetworkOffline && remoteMatches.isEmpty() -> "Offline · Using curated catalog"
                                    remoteMatches.isEmpty() -> "No extra live matches"
                                    else -> "${remoteMatches.size} live matches"
                                }
                                MedicationSectionHeader(
                                    title = headerTitle,
                                    badgeText = badge,
                                    isLoading = isSearchingRemote,
                                    isOffline = remoteNetworkOffline
                                )
                            }

                            items(remoteMatches, key = { it.id }) { remote ->
                                val isSelected = selectedRemote?.id == remote.id && !isCustomMode
                                RemoteMedicationRow(
                                    item = remote,
                                    isSelected = isSelected,
                                    fromCache = remoteFromCache,
                                    onClick = {
                                        selectedRemote = remote
                                        selectedRef = null
                                        isCustomMode = false
                                        searchQuery = remote.displayName
                                        customCategory = remote.inferredCategory
                                        val firstDose = remote.strengthOptions.firstOrNull()
                                        if (firstDose != null) {
                                            dosageText = formatCompactAmount(firstDose.amount)
                                            unitText = firstDose.unit
                                        } else {
                                            dosageText = "10"
                                            unitText = "mg"
                                        }
                                        halfLifeText = "5"
                                        halfLifeSource = HalfLifeSource.ESTIMATED
                                        showHalfLifeEditor = false
                                        dropdownExpanded = false
                                    }
                                )
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        if (searchQuery.isNotBlank() && !exactPresetMatch) {
                            item(key = "custom_entry_option") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedRef = null
                                            selectedRemote = null
                                            isCustomMode = true
                                            halfLifeSource = HalfLifeSource.ESTIMATED
                                            showHalfLifeEditor = true
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

            // Specialized Cannabis / THC Intake & Multi-Cannabinoid Builder
            AnimatedVisibility(visible = isCannabisBuilderActive) {
                CannabisIntakeBuilderCard(
                    method = cannabisMethod,
                    onSelectMethod = { newMethod ->
                        val prevMethod = cannabisMethod
                        cannabisMethod = newMethod
                        val newRef = SubstanceCatalog.findById(newMethod.substanceId)
                        if (newRef != null) {
                            selectedRef = newRef
                            searchQuery = newRef.name
                        }
                        if (newMethod == CannabisIntakeMethod.DRANK_ATE) {
                            dosageText = formatCompactAmount(newRef?.defaultDosage ?: 10.0)
                            syncCannabisEstimate(method = newMethod)
                        } else {
                            var nextQtyMode = cannabisQuantityMode
                            var nextQtyText = cannabisQuantityText
                            var nextRows = cannabinoids
                            if (newMethod == CannabisIntakeMethod.SMOKED && prevMethod != CannabisIntakeMethod.SMOKED) {
                                if (cannabisQuantityMode == CannabisQuantityMode.PUFFS && cannabisQuantityText == "3") {
                                    nextQtyMode = CannabisQuantityMode.GRAMS
                                    nextQtyText = "0.25"
                                }
                                if (cannabinoids.size == 1 && cannabinoids[0].type == CannabinoidType.DELTA_9_THC && cannabinoids[0].percentText == "80") {
                                    nextRows = listOf(cannabinoids[0].copy(percentText = "22"))
                                }
                            } else if (newMethod == CannabisIntakeMethod.VAPED && prevMethod != CannabisIntakeMethod.VAPED) {
                                if (cannabisQuantityMode == CannabisQuantityMode.GRAMS && cannabisQuantityText == "0.25") {
                                    nextQtyMode = CannabisQuantityMode.PUFFS
                                    nextQtyText = "3"
                                }
                                if (cannabinoids.size == 1 && cannabinoids[0].type == CannabinoidType.DELTA_9_THC && cannabinoids[0].percentText == "22") {
                                    nextRows = listOf(cannabinoids[0].copy(percentText = "80"))
                                }
                            }
                            cannabisQuantityMode = nextQtyMode
                            cannabisQuantityText = nextQtyText
                            cannabinoids = nextRows
                            syncCannabisEstimate(
                                method = newMethod,
                                qtyMode = nextQtyMode,
                                qtyText = nextQtyText,
                                puffSecText = cannabisPuffLengthText,
                                rows = nextRows
                            )
                        }
                    },
                    cannabinoids = cannabinoids,
                    onUpdateCannabinoid = { rowId, updated ->
                        val nextRows = cannabinoids.map { if (it.rowId == rowId) updated else it }
                        cannabinoids = nextRows
                        syncCannabisEstimate(rows = nextRows)
                    },
                    onAddCannabinoid = {
                        val existingTypes = cannabinoids.map { it.type }.toSet()
                        val preferredOrder = listOf(
                            CannabinoidType.CBD,
                            CannabinoidType.CBN,
                            CannabinoidType.CBG,
                            CannabinoidType.DELTA_8_THC,
                            CannabinoidType.THCA,
                            CannabinoidType.HHC,
                            CannabinoidType.DELTA_9_THC,
                            CannabinoidType.CUSTOM
                        )
                        val nextType = preferredOrder.firstOrNull { it !in existingTypes } ?: CannabinoidType.CUSTOM
                        val nextRows = cannabinoids + CannabinoidEntry(
                            rowId = nextCannabinoidRowId++,
                            type = nextType,
                            percentText = "5"
                        )
                        cannabinoids = nextRows
                        syncCannabisEstimate(rows = nextRows)
                    },
                    onRemoveCannabinoid = { rowId ->
                        if (cannabinoids.size > 1) {
                            val nextRows = cannabinoids.filterNot { it.rowId == rowId }
                            cannabinoids = nextRows
                            syncCannabisEstimate(rows = nextRows)
                        }
                    },
                    quantityMode = cannabisQuantityMode,
                    onSelectQuantityMode = { mode ->
                        if (mode != cannabisQuantityMode) {
                            cannabisQuantityMode = mode
                            val defaultQty = if (mode == CannabisQuantityMode.PUFFS) "3" else "0.25"
                            cannabisQuantityText = defaultQty
                            syncCannabisEstimate(qtyMode = mode, qtyText = defaultQty)
                        }
                    },
                    quantityText = cannabisQuantityText,
                    onQuantityChange = { newQty ->
                        cannabisQuantityText = newQty
                        syncCannabisEstimate(qtyText = newQty)
                    },
                    puffLengthSecondsText = cannabisPuffLengthText,
                    onPuffLengthChange = { newSec ->
                        cannabisPuffLengthText = newSec
                        syncCannabisEstimate(puffSecText = newSec)
                    },
                    estimate = cannabisEstimate
                )
            }

            // Specialized Tobacco / Nicotine Intake & Strength Builder
            AnimatedVisibility(visible = isNicotineBuilderActive) {
                NicotineTobaccoBuilderCard(
                    method = nicotineMethod,
                    onSelectMethod = { newMethod ->
                        nicotineMethod = newMethod
                        val newRef = SubstanceCatalog.findById(newMethod.substanceId)
                        if (newRef != null) {
                            selectedRef = newRef
                            searchQuery = newRef.name
                        }
                        syncNicotineEstimate(method = newMethod)
                    },
                    smokedType = smokedTobaccoType,
                    onSelectSmokedType = { newType ->
                        smokedTobaccoType = newType
                        syncNicotineEstimate(sType = newType)
                    },
                    smokedCountText = smokedTobaccoCountText,
                    onSmokedCountChange = { newCount ->
                        smokedTobaccoCountText = newCount
                        syncNicotineEstimate(sCountText = newCount)
                    },
                    vapeStrengthUnit = vapeStrengthUnit,
                    onSelectVapeStrengthUnit = { newUnit ->
                        if (newUnit != vapeStrengthUnit) {
                            val currentVal = parseDecimalInput(vapeStrengthText)
                            val converted = when {
                                currentVal == null -> if (newUnit == VapeStrengthUnit.PERCENT) "5" else "50"
                                newUnit == VapeStrengthUnit.MG_PER_ML -> formatCompactAmount(currentVal * 10.0)
                                else -> formatCompactAmount(currentVal / 10.0)
                            }
                            vapeStrengthUnit = newUnit
                            vapeStrengthText = converted
                            syncNicotineEstimate(vUnit = newUnit, vStrengthText = converted)
                        }
                    },
                    vapeStrengthText = vapeStrengthText,
                    onVapeStrengthChange = { newStrength ->
                        vapeStrengthText = newStrength
                        syncNicotineEstimate(vStrengthText = newStrength)
                    },
                    vapePuffsText = vapePuffsText,
                    onVapePuffsChange = { newPuffs ->
                        vapePuffsText = newPuffs
                        syncNicotineEstimate(vPuffsText = newPuffs)
                    },
                    vapePuffLengthSecondsText = vapePuffLengthText,
                    onVapePuffLengthChange = { newSec ->
                        vapePuffLengthText = newSec
                        syncNicotineEstimate(vPuffLenText = newSec)
                    },
                    estimate = nicotineEstimate
                )
            }

            // Formulation-Specific Exact Clinical Strength Chips (hidden when inhaled Cannabis/Nicotine builder drives mg)
            val showStandardDoseChips = activeDoseOptions.isNotEmpty() && !isCustomMode &&
                !isNicotineBuilderActive &&
                !(isCannabisBuilderActive && cannabisMethod != CannabisIntakeMethod.DRANK_ATE)
            AnimatedVisibility(visible = showStandardDoseChips) {
                ExactStrengthChipsSection(
                    formulationTitle = activeFormulationTitle,
                    doseOptions = activeDoseOptions,
                    currentDose = parsedDosage,
                    currentUnit = unitText,
                    onSelectOption = { option ->
                        dosageText = formatCompactAmount(option.amount)
                        unitText = option.unit
                    }
                )
            }

            // Half-Life Provenance & Inline Edit Toggle (Curated or Live NIH Medication)
            AnimatedVisibility(visible = (selectedRef != null || selectedRemote != null) && !isCustomMode) {
                HalfLifeProvenanceCard(
                    halfLifeDisplay = formatCompactAmount(parsedHalfLife),
                    source = halfLifeSource,
                    isLookingUpFda = isFetchingOpenFda,
                    excerpt = fdaExcerpt,
                    isEditingHalfLife = showHalfLifeEditor,
                    onToggleEditHalfLife = { showHalfLifeEditor = !showHalfLifeEditor }
                )
            }

            // Editable Half-Life & Category Controls (for Custom Entries, Live NIH Overrides, or Curated Overrides)
            val effectiveCustom = isCustomMode || (selectedRef == null && selectedRemote == null && searchQuery.isNotBlank())
            AnimatedVisibility(visible = effectiveCustom || showHalfLifeEditor) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (effectiveCustom || selectedRemote != null) {
                        Text(
                            text = if (selectedRemote != null) "Medication Category & Half-Life" else "Custom Substance Parameters",
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
                                    label = { Text(cat.label) },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("custom_category_${cat.name}")
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = halfLifeText,
                        onValueChange = { newVal ->
                            halfLifeText = newVal
                            if (selectedRef != null && parseDecimalInput(newVal) != selectedRef?.halfLifeHours) {
                                halfLifeSource = HalfLifeSource.ESTIMATED
                            }
                        },
                        label = { Text("Elimination Half-Life (hours)") },
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
                    placeholder = { Text("mg, mcg, cups") },
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
                placeholder = { Text("e.g., Taken with breakfast or empty stomach") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("substance_notes_input")
            )

            val (saveInteraction, saveScale) = rememberPressScale()
            Button(
                onClick = {
                    val chosenRef = selectedRef
                    val chosenRemote = selectedRemote
                    val finalId = when {
                        isCannabisBuilderActive -> cannabisMethod.substanceId
                        isNicotineBuilderActive -> nicotineMethod.substanceId
                        chosenRef != null -> chosenRef.id
                        chosenRemote != null -> chosenRemote.id
                        editingEntry != null && editingEntry.substanceName.equals(validName, ignoreCase = true) ->
                            editingEntry.substanceId
                        else -> "custom_${validName.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_")}"
                    }
                    val finalName = when {
                        isCannabisBuilderActive -> cannabisMethod.entryTitle
                        isNicotineBuilderActive -> if (nicotineMethod == NicotineDeliveryMethod.SMOKED_TOBACCO) {
                            "Smoked Tobacco (${smokedTobaccoType.shortLabel})"
                        } else {
                            "Vaped Nicotine (${vapeStrengthText.trim().ifBlank { "5" }}${vapeStrengthUnit.suffix})"
                        }
                        else -> validName
                    }
                    val finalNotes = when {
                        isCannabisBuilderActive -> {
                            val tag = StructuredSessionMetadata.formatCannabisTag(
                                method = cannabisMethod,
                                quantityMode = cannabisQuantityMode,
                                quantityText = cannabisQuantityText,
                                puffLengthSecondsText = cannabisPuffLengthText,
                                cannabinoids = cannabinoids,
                                oralDoseText = dosageText
                            )
                            StructuredSessionMetadata.mergeTagWithUserNotes(tag, notesText)
                        }
                        isNicotineBuilderActive -> {
                            val tag = StructuredSessionMetadata.formatNicotineTag(
                                method = nicotineMethod,
                                smokedType = smokedTobaccoType,
                                smokedCountText = smokedTobaccoCountText,
                                vapeStrengthUnit = vapeStrengthUnit,
                                vapeStrengthText = vapeStrengthText,
                                vapePuffsText = vapePuffsText,
                                vapePuffLengthSecondsText = vapePuffLengthText
                            )
                            StructuredSessionMetadata.mergeTagWithUserNotes(tag, notesText)
                        }
                        else -> notesText
                    }
                    val finalCategory = chosenRef?.category?.label ?: customCategory.label
                    onSave(
                        selectedDateIso,
                        finalId,
                        finalName,
                        finalCategory,
                        parsedDosage ?: 1.0,
                        unitText.trim().ifBlank { "mg" },
                        takenAtMillis,
                        parsedHalfLife.coerceAtLeast(0.25),
                        resolvedWeight,
                        finalNotes,
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

internal fun parseDecimalInput(raw: String): Double? =
    raw.trim().replace(',', '.').toDoubleOrNull()
