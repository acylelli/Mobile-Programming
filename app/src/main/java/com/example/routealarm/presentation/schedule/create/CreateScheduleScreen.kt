package com.example.routealarm.presentation.schedule.create

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.ChoicePill
import com.example.routealarm.core.designsystem.component.DayChip
import com.example.routealarm.core.designsystem.component.MinuteSlider
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.RadioDot
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.WheelDurationPicker
import com.example.routealarm.core.designsystem.component.WheelTimePicker
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmMode
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.PlaceType
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.domain.usecase.PlanOutcome
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.dayShortName
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.minutesText
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun CreateScheduleRoute(
    onSaved: (inexactWarning: Boolean) -> Unit,
    onClose: () -> Unit,
    onShowPaywall: () -> Unit,
    viewModel: CreateScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.validationError) {
        val error = state.validationError ?: return@LaunchedEffect
        viewModel.consumeValidationError()
        snackbar.showSnackbar(context.getString(error.messageRes()))
    }
    LaunchedEffect(state.result) {
        (state.result as? ResultState.Saved)?.let { onSaved(it.inexactWarning) }
    }
    val back = { if (!viewModel.back()) onClose() }
    BackHandler(onBack = back)

    // 현재 위치는 버튼을 누를 때만 요청한다. 거절하면 검색으로 출발지를 지정하도록 안내한다(SFR-002).
    val currentLocationName = stringResource(R.string.create_current_location_name)
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            viewModel.useCurrentLocation(currentLocationName)
        } else {
            scope.launch { snackbar.showSnackbar(context.getString(R.string.error_location_permission)) }
        }
    }

    CreateScheduleScreen(
        state = state,
        query = viewModel.query,
        title = viewModel.title,
        savedPlaces = savedPlaces,
        recentSearches = recentSearches,
        isPro = isPro,
        onBack = back,
        onNext = viewModel::next,
        onQueryChange = viewModel::onQueryChange,
        onSearch = viewModel::searchNow,
        onSelectPlace = viewModel::selectPlace,
        onUseCurrentLocation = {
            if (viewModel.hasLocationPermission()) {
                viewModel.useCurrentLocation(currentLocationName)
            } else {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
        },
        onStartRegistering = viewModel::startRegistering,
        onCancelRegistering = viewModel::cancelRegistering,
        onDeleteFavorite = viewModel::deleteFavorite,
        onTitle = viewModel::onTitleChange,
        onTime = viewModel::setTargetTime,
        onDate = viewModel::setOneTimeDate,
        onRepeatPreset = viewModel::setRepeatPreset,
        onToggleDay = viewModel::toggleRepeatDay,
        onPreparation = viewModel::setPreparation,
        onBufferPreset = viewModel::setBufferPreset,
        onCustomBuffer = viewModel::setCustomBuffer,
        onAlarmMode = viewModel::setAlarmMode,
        onRetry = viewModel::calculate,
        onSave = viewModel::save,
        onShowPaywall = onShowPaywall,
    )
}

@Composable
fun CreateScheduleScreen(
    state: CreateScheduleUiState,
    query: String,
    title: String,
    savedPlaces: List<Place>,
    recentSearches: List<Place>,
    isPro: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlace: (Place, Boolean) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onStartRegistering: (PlaceType) -> Unit,
    onCancelRegistering: () -> Unit,
    onDeleteFavorite: (Long) -> Unit,
    onTitle: (String) -> Unit,
    onTime: (LocalTime) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onRepeatPreset: (RepeatPreset) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onPreparation: (Int) -> Unit,
    onBufferPreset: (BufferPreset?) -> Unit,
    onCustomBuffer: (Int) -> Unit,
    onAlarmMode: (AlarmMode) -> Unit,
    onRetry: () -> Unit,
    onSave: () -> Unit,
    onShowPaywall: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        if (state.step == CreateStep.RESULT) {
            BackHeader(onBack = onBack, title = stringResource(R.string.create_result_title))
        } else {
            BackHeader(
                onBack = onBack,
                trailing = {
                    Text(
                        stringResource(R.string.create_step_format, state.stepIndex + 1, state.stepCount),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textSecondary,
                    )
                },
            )
            // 단계 표시(①): 5단계 중 현재 단계까지 검정 막대로 채운다.
            StepProgress(current = state.stepIndex, count = state.stepCount)
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
            when (step) {
                CreateStep.ORIGIN, CreateStep.DESTINATION -> PlaceStep(
                    isOrigin = step == CreateStep.ORIGIN,
                    state = state,
                    query = query,
                    savedPlaces = savedPlaces,
                    recentSearches = recentSearches,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onSelect = onSelectPlace,
                    onUseCurrentLocation = onUseCurrentLocation,
                    onStartRegistering = onStartRegistering,
                    onCancelRegistering = onCancelRegistering,
                    onDeleteFavorite = onDeleteFavorite,
                )
                CreateStep.TIME -> TimeStep(state, title, onTitle, onTime, onDate, onRepeatPreset, onToggleDay)
                CreateStep.PREPARATION -> PreparationStep(state.preparationMinutes, onPreparation)
                CreateStep.BUFFER -> BufferStep(state, onBufferPreset, onCustomBuffer, onAlarmMode)
                CreateStep.RESULT -> PreviewStep(state, isPro, onRetry, onToggleDay, onShowPaywall)
            }
        }

        val buttonModifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp)
        if (state.step == CreateStep.RESULT) {
            val ready = state.result as? ResultState.Ready
            if (ready != null) {
                // 한 번만 울리는 일정인데 이미 늦었다면 저장할 수 없다.
                val canSave = ready.preview.outcome !is PlanOutcome.TooLate || state.repeatDays.isNotEmpty()
                PrimaryButton(
                    text = stringResource(R.string.create_result_save),
                    onClick = onSave,
                    enabled = canSave,
                    loading = state.isSaving || state.isRecalculating,
                    modifier = buttonModifier,
                )
            }
        } else {
            PrimaryButton(
                text = stringResource(if (state.step == CreateStep.BUFFER) R.string.create_calculate else R.string.common_next),
                onClick = onNext,
                enabled = state.canProceed,
                modifier = buttonModifier,
            )
        }
    }
}

@Composable
private fun StepProgress(current: Int, count: Int) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(Modifier.weight(1f).height(4.dp).background(if (index <= current) colors.textPrimary else colors.outline, CircleShape))
        }
    }
}

/** 단계마다 맨 위에 오는 큰 질문 */
@Composable
internal fun StepTitle(text: String, body: String? = null) {
    val colors = RouteAlarmTheme.colors
    Spacer(Modifier.height(24.dp))
    Text(text, style = MaterialTheme.typography.headlineLarge, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
    if (body != null) {
        Spacer(Modifier.height(8.dp))
        Paragraph(body, style = MaterialTheme.typography.bodyLarge)
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun StepColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        content()
        Spacer(Modifier.height(Spacing.lg))
    }
}

/** 요일 일곱 개를 한 줄에 나란히(월~일) */
@Composable
internal fun DayChipRow(selected: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { day ->
            DayChip(dayShortName(day), selected = day in selected, onClick = { onToggle(day) }, modifier = Modifier.weight(1f))
        }
    }
}

/* ───────────── 3단계: 도착 시각 (UI-C-003) ───────────── */

@Composable
private fun TimeStep(
    state: CreateScheduleUiState,
    title: String,
    onTitle: (String) -> Unit,
    onTime: (LocalTime) -> Unit,
    onDate: (LocalDate?) -> Unit,
    onRepeatPreset: (RepeatPreset) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    StepColumn {
        StepTitle(stringResource(R.string.create_time_question))
        WheelTimePicker(time = state.targetTime, onTimeChange = onTime)

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel(stringResource(R.string.create_repeat_question))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(stringResource(R.string.create_repeat_once), state.repeatPreset == RepeatPreset.ONCE, { onRepeatPreset(RepeatPreset.ONCE) })
            ChoicePill(stringResource(R.string.create_repeat_weekdays), state.repeatPreset == RepeatPreset.WEEKDAYS, { onRepeatPreset(RepeatPreset.WEEKDAYS) })
            ChoicePill(stringResource(R.string.create_repeat_daily), state.repeatPreset == RepeatPreset.DAILY, { onRepeatPreset(RepeatPreset.DAILY) })
        }
        Spacer(Modifier.height(10.dp))
        DayChipRow(state.repeatDays, onToggleDay, Modifier.fillMaxWidth())

        if (state.repeatDays.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            DateRow(state.oneTimeDate, state.targetTime, onDate)
        }

        Spacer(Modifier.height(Spacing.lg))
        val titleLabel = stringResource(R.string.create_title_label)
        SectionLabel(titleLabel)
        Spacer(Modifier.height(10.dp))
        BasicTextField(
            value = title,
            onValueChange = onTitle,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            singleLine = true,
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .background(colors.surface, Radius.field)
                .semantics { contentDescription = titleLabel },
            decorationBox = { innerTextField ->
                Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                    if (title.isEmpty()) {
                        Text(stringResource(R.string.create_title_hint), style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
                    }
                    innerTextField()
                }
            },
        )
    }
}

/** 한 번만 울리는 일정의 날짜. 따로 고르지 않으면 가장 가까운 날(오늘 또는 내일)이다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(selected: LocalDate?, targetTime: LocalTime, onDate: (LocalDate?) -> Unit) {
    val colors = RouteAlarmTheme.colors
    var showPicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val effective = selected ?: if (targetTime.isAfter(LocalTime.now())) today else today.plusDays(1)
    val relative = when (effective) {
        today -> stringResource(R.string.common_today)
        today.plusDays(1) -> stringResource(R.string.common_tomorrow)
        else -> null
    }
    val dateText = TimeFormat.dateWithWeekday(effective)

    SurfaceCard(modifier = Modifier.fillMaxWidth(), onClick = { showPicker = true }, shape = Radius.field) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.create_date_label), style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(
                if (relative != null) stringResource(R.string.create_date_relative_format, relative, dateText) else dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(22.dp))
        }
    }

    if (showPicker) {
        val todayStartUtc = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = effective.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            // 지난 날짜는 고를 수 없다(SFR-003 입력값 검증).
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayStartUtc
                override fun isSelectableYear(year: Int): Boolean = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text(stringResource(R.string.common_confirm)) }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.common_cancel)) } },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

/* ───────────── 4단계: 준비 시간 (UI-C-004) ───────────── */

@Composable
private fun PreparationStep(minutes: Int, onChange: (Int) -> Unit) {
    StepColumn {
        StepTitle(stringResource(R.string.create_preparation_question), stringResource(R.string.create_preparation_body))
        WheelDurationPicker(minutes = minutes, onMinutesChange = onChange, range = UserPreferences.PREPARATION_MINUTES_RANGE)
        Spacer(Modifier.height(Spacing.md))
        // 자주 쓰는 값은 한 번에 고른다. 누르면 휠이 그 값으로 돌아간다.
        // 글자를 크게 쓰는 좁은 화면에서는 한 줄에 다 들어가지 않으므로 옆으로 밀어 볼 수 있게 한다.
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            UserPreferences.PREPARATION_MINUTES_PRESETS.forEach { preset ->
                ChoicePill(label = minutesText(preset), selected = minutes == preset, onClick = { onChange(preset) })
            }
        }
    }
}

/* ───────────── 5단계: 여유 시간 (UI-C-005) ───────────── */

@Composable
private fun BufferStep(
    state: CreateScheduleUiState,
    onBufferPreset: (BufferPreset?) -> Unit,
    onCustomBuffer: (Int) -> Unit,
    onAlarmMode: (AlarmMode) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    StepColumn {
        StepTitle(stringResource(R.string.create_buffer_question), stringResource(R.string.create_buffer_body))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(
                BufferPreset.TIGHT to R.string.create_buffer_tight,
                BufferPreset.NORMAL to R.string.create_buffer_normal,
                BufferPreset.RELAXED to R.string.create_buffer_relaxed,
            ).forEach { (preset, labelRes) ->
                BufferOption(
                    label = stringResource(labelRes),
                    value = minutesText(preset.minutes),
                    selected = state.bufferPreset == preset,
                    onClick = { onBufferPreset(preset) },
                )
            }
            val custom = state.bufferPreset == null
            BufferOption(
                label = stringResource(R.string.create_buffer_custom),
                value = minutesText(state.customBufferMinutes),
                selected = custom,
                onClick = { onCustomBuffer(state.customBufferMinutes) },
            ) {
                if (custom) MinuteSlider(state.customBufferMinutes, 0..60, onCustomBuffer, Modifier.padding(horizontal = 12.dp))
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        SectionLabel(stringResource(R.string.create_alarm_mode_label))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoicePill(
                stringResource(R.string.create_alarm_mode_wake_and_departure),
                state.alarmMode == AlarmMode.WAKE_UP_AND_DEPARTURE,
                { onAlarmMode(AlarmMode.WAKE_UP_AND_DEPARTURE) },
            )
            ChoicePill(
                stringResource(R.string.create_alarm_mode_wake_only),
                state.alarmMode == AlarmMode.WAKE_UP_ONLY,
                { onAlarmMode(AlarmMode.WAKE_UP_ONLY) },
            )
        }
        Spacer(Modifier.height(8.dp))
        Paragraph(stringResource(R.string.create_alarm_mode_body), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BufferOption(
    label: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val colors = RouteAlarmTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, selected = selected, shape = Radius.group) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(14.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = if (selected) colors.accentText else colors.textSecondary)
        }
        extra?.invoke()
    }
}
