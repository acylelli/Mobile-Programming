package com.example.routealarm.presentation.schedule.create

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LabeledValue
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.TimePickerCard
import com.example.routealarm.core.designsystem.component.TransitTimeline
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.usecase.PlanOutcome
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.dayShortName
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.place.PlaceRow
import java.time.DayOfWeek

@Composable
fun CreateScheduleRoute(
    pickedOrigin: Place?,
    pickedDestination: Place?,
    onConsumePicked: () -> Unit,
    onSearchOrigin: () -> Unit,
    onSearchDestination: () -> Unit,
    onSaved: (Long) -> Unit,
    onClose: () -> Unit,
    viewModel: CreateScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 장소 검색 화면에서 돌아온 결과를 반영한다.
    LaunchedEffect(pickedOrigin, pickedDestination) {
        pickedOrigin?.let(viewModel::setOrigin)
        pickedDestination?.let(viewModel::setDestination)
        if (pickedOrigin != null || pickedDestination != null) onConsumePicked()
    }
    LaunchedEffect(state.validationError) {
        val error = state.validationError ?: return@LaunchedEffect
        snackbar.showSnackbar(context.getString(error.messageRes()))
        viewModel.consumeValidationError()
    }
    LaunchedEffect(state.result) {
        (state.result as? ResultState.Saved)?.let { onSaved(it.scheduleId) }
    }
    val back = { if (!viewModel.back()) onClose() }
    BackHandler(onBack = back)

    CreateScheduleScreen(
        state = state,
        savedPlaces = savedPlaces,
        snackbarHostState = snackbar,
        onBack = back,
        onNext = viewModel::next,
        onSelectOrigin = viewModel::setOrigin,
        onSelectDestination = viewModel::setDestination,
        onUseCurrentLocation = { viewModel.useCurrentLocation(context.getString(R.string.create_current_location_name)) },
        onSearchOrigin = onSearchOrigin,
        onSearchDestination = onSearchDestination,
        onTitle = viewModel::setTitle,
        onTime = viewModel::setTargetTime,
        onRepeatPreset = viewModel::setRepeatPreset,
        onToggleDay = viewModel::toggleCustomDay,
        onPreparation = viewModel::setPreparation,
        onBufferPreset = viewModel::setBufferPreset,
        onCustomBuffer = viewModel::setCustomBuffer,
        onAlarmMode = viewModel::setAlarmMode,
        onRetry = viewModel::calculate,
        onSave = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleScreen(
    state: CreateScheduleUiState,
    savedPlaces: List<Place>,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelectOrigin: (Place) -> Unit,
    onSelectDestination: (Place) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onSearchOrigin: () -> Unit,
    onSearchDestination: () -> Unit,
    onTitle: (String) -> Unit,
    onTime: (java.time.LocalTime) -> Unit,
    onRepeatPreset: (RepeatPreset) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onPreparation: (Int) -> Unit,
    onBufferPreset: (BufferPreset?) -> Unit,
    onCustomBuffer: (Int) -> Unit,
    onAlarmMode: (AlarmMode) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isEditing) R.string.create_edit_title else R.string.create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    if (state.step != CreateStep.RESULT) {
                        Text(
                            stringResource(R.string.create_step_format, state.stepIndex + 1, state.stepCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = Spacing.md),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.step != CreateStep.RESULT) {
                LinearProgressIndicator(
                    progress = { (state.stepIndex + 1) / state.stepCount.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                )
            }
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn()) togetherWith
                        (slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut())
                },
                label = "createStep",
                modifier = Modifier.weight(1f),
            ) { step ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                    when (step) {
                        CreateStep.ORIGIN -> PlaceStep(
                            question = stringResource(R.string.create_origin_question),
                            selected = state.origin,
                            savedPlaces = savedPlaces,
                            onSelect = onSelectOrigin,
                            onSearch = onSearchOrigin,
                            currentLocation = state.useCurrentLocation,
                            onUseCurrentLocation = onUseCurrentLocation,
                        )
                        CreateStep.DESTINATION -> PlaceStep(
                            question = stringResource(R.string.create_destination_question),
                            selected = state.destination,
                            savedPlaces = savedPlaces,
                            onSelect = onSelectDestination,
                            onSearch = onSearchDestination,
                        )
                        CreateStep.TIME -> TimeStep(state, onTitle, onTime, onRepeatPreset, onToggleDay)
                        CreateStep.PREPARATION -> PreparationStep(state.preparationMinutes, onPreparation)
                        CreateStep.BUFFER -> BufferStep(state, onBufferPreset, onCustomBuffer, onAlarmMode)
                        CreateStep.RESULT -> ResultStep(state, onRetry, onSave, onBack)
                    }
                }
            }
            if (state.step != CreateStep.RESULT) {
                PrimaryButton(
                    text = stringResource(if (state.step == CreateStep.BUFFER) R.string.create_calculate else R.string.common_next),
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                )
            }
        }
    }
}

@Composable
private fun Question(text: String) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = Spacing.md))
}

@Composable
private fun PlaceStep(
    question: String,
    selected: Place?,
    savedPlaces: List<Place>,
    onSelect: (Place) -> Unit,
    onSearch: () -> Unit,
    currentLocation: Boolean = false,
    onUseCurrentLocation: (() -> Unit)? = null,
) {
    Question(question)
    if (selected != null) {
        Text(stringResource(R.string.cd_selected), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Spacing.xs))
        AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (currentLocation) Icons.Outlined.MyLocation else com.example.routealarm.presentation.place.placeIcon(selected.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(Spacing.md))
                Column {
                    Text(selected.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    if (selected.address.isNotBlank()) {
                        Text(selected.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
    }
    SecondaryButton(text = stringResource(R.string.create_search_place), onClick = onSearch, modifier = Modifier.fillMaxWidth())
    if (onUseCurrentLocation != null) {
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton(text = stringResource(R.string.create_use_current_location), onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(R.string.create_saved_places), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(Spacing.sm))
    if (savedPlaces.isEmpty()) {
        Text(stringResource(R.string.create_no_saved_places), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    savedPlaces.forEach { place ->
        PlaceRow(place = place, onClick = { onSelect(place) })
        Spacer(Modifier.height(Spacing.sm))
    }
}

@Composable
private fun TimeStep(
    state: CreateScheduleUiState,
    onTitle: (String) -> Unit,
    onTime: (java.time.LocalTime) -> Unit,
    onRepeatPreset: (RepeatPreset) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    Question(stringResource(R.string.create_time_question))
    TimePickerCard(time = state.targetTime, onTimeChange = onTime)
    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(R.string.create_repeat_question), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            RepeatPreset.ONCE to R.string.create_repeat_once,
            RepeatPreset.WEEKDAYS to R.string.create_repeat_weekdays,
            RepeatPreset.DAILY to R.string.create_repeat_daily,
            RepeatPreset.CUSTOM to R.string.create_repeat_custom,
        ).forEach { (preset, res) ->
            FilterChip(selected = state.repeatPreset == preset, onClick = { onRepeatPreset(preset) }, label = { Text(stringResource(res)) })
        }
    }
    if (state.repeatPreset == RepeatPreset.CUSTOM) {
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DayOfWeek.entries.forEach { day ->
                FilterChip(selected = day in state.customDays, onClick = { onToggleDay(day) }, label = { Text(dayShortName(day)) })
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    OutlinedTextField(
        value = state.title,
        onValueChange = onTitle,
        label = { Text(stringResource(R.string.create_title_label)) },
        placeholder = { Text(stringResource(R.string.create_title_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PreparationStep(minutes: Int, onChange: (Int) -> Unit) {
    Question(stringResource(R.string.create_preparation_question))
    AppCard {
        Column(Modifier.padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(minutesText(minutes), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
            Slider(
                value = minutes.toFloat(),
                onValueChange = { onChange((it / 5).toInt() * 5) },
                valueRange = 5f..120f,
                steps = 22,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(15, 30, 45, 60).forEach { preset ->
                    FilterChip(selected = minutes == preset, onClick = { onChange(preset) }, label = { Text(minutesText(preset)) })
                }
            }
        }
    }
}

@Composable
private fun BufferStep(
    state: CreateScheduleUiState,
    onBufferPreset: (BufferPreset?) -> Unit,
    onCustomBuffer: (Int) -> Unit,
    onAlarmMode: (AlarmMode) -> Unit,
) {
    Question(stringResource(R.string.create_buffer_question))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            BufferPreset.TIGHT to R.string.create_buffer_tight,
            BufferPreset.NORMAL to R.string.create_buffer_normal,
            BufferPreset.RELAXED to R.string.create_buffer_relaxed,
        ).forEach { (preset, res) ->
            val selected = state.bufferPreset == preset
            AppCard(
                onClick = { onBufferPreset(preset) },
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(res), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(minutesText(preset.minutes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        val custom = state.bufferPreset == null
        AppCard(
            onClick = { onCustomBuffer(state.customBufferMinutes) },
            containerColor = if (custom) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.create_buffer_custom), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(minutesText(state.customBufferMinutes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (custom) {
                    Slider(
                        value = state.customBufferMinutes.toFloat(),
                        onValueChange = { onCustomBuffer((it / 5).toInt() * 5) },
                        valueRange = 0f..60f,
                        steps = 11,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(Spacing.lg))
    Text(stringResource(R.string.create_alarm_mode_label), style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(Spacing.sm))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        FilterChip(
            selected = state.alarmMode == AlarmMode.WAKE_UP_AND_DEPARTURE,
            onClick = { onAlarmMode(AlarmMode.WAKE_UP_AND_DEPARTURE) },
            label = { Text(stringResource(R.string.create_alarm_mode_wake_and_departure)) },
        )
        FilterChip(
            selected = state.alarmMode == AlarmMode.WAKE_UP_ONLY,
            onClick = { onAlarmMode(AlarmMode.WAKE_UP_ONLY) },
            label = { Text(stringResource(R.string.create_alarm_mode_wake_only)) },
        )
    }
}

@Composable
private fun ResultStep(state: CreateScheduleUiState, onRetry: () -> Unit, onSave: () -> Unit, onBack: () -> Unit) {
    when (val result = state.result) {
        ResultState.Idle, ResultState.Calculating -> LoadingState(message = stringResource(R.string.create_calculating))
        is ResultState.Failed -> ErrorState(
            title = stringResource(R.string.error_title),
            message = stringResource(result.error.messageRes()),
            onRetry = onRetry,
            retryText = stringResource(R.string.common_retry),
        )
        is ResultState.Saved -> LoadingState()
        is ResultState.Ready -> {
            val preview = result.preview
            val plan = preview.outcome.plan
            Text(stringResource(R.string.create_result_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(Spacing.md))

            when (val outcome = preview.outcome) {
                is PlanOutcome.TooLate -> AppCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                    Column(Modifier.padding(Spacing.md)) {
                        Text(
                            stringResource(R.string.create_result_too_late_title, TimeFormat.time(plan.targetArrival)),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            stringResource(R.string.create_result_too_late_body, TimeFormat.time(outcome.earliestArrival), outcome.lateByMinutes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (state.repeatPreset != RepeatPreset.ONCE) {
                            Text(stringResource(R.string.create_result_too_late_repeat_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                is PlanOutcome.OnTime -> if (outcome.wakeUpAlreadyPassed) {
                    AppCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            stringResource(R.string.create_result_wake_passed, TimeFormat.time(plan.departure)),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(Spacing.md),
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))

            // 09:00 목표 ↑ 08:52 예상 도착 ↑ 07:45 출발 ↑ 07:15 기상
            AppCard {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    TimelineRow(stringResource(R.string.create_result_target), TimeFormat.time(plan.targetArrival), emphasize = false)
                    TimelineRow(stringResource(R.string.create_result_arrival), TimeFormat.time(plan.estimatedArrival), emphasize = false)
                    TimelineRow(stringResource(R.string.create_result_departure), TimeFormat.time(plan.departure), emphasize = false)
                    TimelineRow(stringResource(R.string.create_result_wake_up), TimeFormat.time(plan.wakeUp), emphasize = true)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            AppCard {
                Row(Modifier.padding(Spacing.md).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    LabeledValue(stringResource(R.string.create_preparation_label), minutesText(state.preparationMinutes))
                    LabeledValue(stringResource(R.string.create_travel_label), minutesText(plan.travelMinutes), horizontalAlignment = Alignment.CenterHorizontally)
                    LabeledValue(stringResource(R.string.create_buffer_label), minutesText(plan.safetyBufferMinutes), horizontalAlignment = Alignment.End)
                }
            }
            Spacer(Modifier.height(Spacing.md))
            AppCard {
                Column(Modifier.padding(Spacing.md)) {
                    Text(stringResource(R.string.home_route_title), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    TransitTimeline(preview.route)
                }
            }
            if (!preview.outcome.plan.isRealtime) {
                Spacer(Modifier.height(Spacing.sm))
                Text(stringResource(R.string.traffic_cached), style = MaterialTheme.typography.bodySmall, color = RouteAlarmTheme.appColors.warning)
            }
            Spacer(Modifier.height(Spacing.lg))
            val canSave = preview.outcome !is PlanOutcome.TooLate || state.repeatPreset != RepeatPreset.ONCE
            PrimaryButton(text = stringResource(R.string.create_result_save), onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.sm))
            SecondaryButton(text = stringResource(R.string.common_back), onClick = onBack, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun TimelineRow(label: String, time: String, emphasize: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            time,
            style = if (emphasize) MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.headlineSmall,
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(140.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
