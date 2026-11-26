package com.example.routealarm.presentation.timetable

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppBottomSheet
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.DayChip
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.TextLink
import com.example.routealarm.core.designsystem.component.WheelTimePicker
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.TimetableAlarmProposal
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.dayShortName
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import com.example.routealarm.presentation.schedule.create.PlaceRow
import com.example.routealarm.presentation.schedule.create.SearchField
import com.example.routealarm.presentation.schedule.create.SearchResults
import com.example.routealarm.presentation.schedule.create.placeIcon
import com.example.routealarm.presentation.schedule.create.placeSubtitle
import java.time.DayOfWeek

@Composable
fun TimetableRoute(
    onBack: () -> Unit,
    onOpenCalendar: () -> Unit,
    onAlarmsCreated: () -> Unit,
    viewModel: TimetableViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalAppSnackbar.current
    // 사진 선택기: 저장소 권한 없이 사용자가 고른 사진 한 장만 받는다.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.importImage(it.toString()) }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TimetableEvent.Recognized ->
                    snackbar.showSnackbar(resources.getString(R.string.timetable_recognized, event.count))
                is TimetableEvent.ShowError -> snackbar.showSnackbar(resources.getString(event.error.messageRes()))
                is TimetableEvent.AlarmsCreated -> {
                    snackbar.showSnackbar(
                        if (event.failed == 0 && event.replaced > 0) {
                            resources.getString(R.string.timetable_alarms_replaced, event.created, event.replaced)
                        } else if (event.failed == 0) {
                            resources.getString(R.string.timetable_alarms_created, event.created)
                        } else {
                            resources.getString(R.string.timetable_alarms_partly_created, event.created, event.failed)
                        },
                    )
                    onAlarmsCreated()
                }
            }
        }
    }

    TimetableScreen(
        state = state,
        onBack = onBack,
        onPickImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onOpenCalendar = onOpenCalendar,
        onAdd = viewModel::startAdding,
        onEdit = viewModel::startEditing,
        onClearAll = viewModel::clearAll,
        onCreateAlarms = viewModel::openAlarmSheet,
    )

    state.editor?.let { editor ->
        ClassEditorSheet(
            editor = editor,
            name = viewModel.className,
            room = viewModel.classRoom,
            onNameChange = viewModel::onClassNameChange,
            onRoomChange = viewModel::onClassRoomChange,
            onDay = viewModel::setEditorDay,
            onStart = viewModel::setEditorStart,
            onSave = viewModel::saveEditor,
            onDelete = { editor.id?.let(viewModel::delete); viewModel.dismissEditor() },
            onDismiss = viewModel::dismissEditor,
        )
    }

    state.alarmSheet?.let { sheet ->
        AlarmSheet(
            sheet = sheet,
            proposals = state.proposals,
            previousAlarmCount = state.previousAlarmCount,
            savedPlaces = state.savedPlaces,
            query = viewModel.placeQuery,
            onQueryChange = viewModel::onPlaceQueryChange,
            onSearch = viewModel::searchPlacesNow,
            onStartPicking = viewModel::startPicking,
            onCancelPicking = viewModel::cancelPicking,
            onPick = viewModel::pickPlace,
            onCreate = viewModel::createAlarms,
            onDismiss = viewModel::dismissAlarmSheet,
        )
    }
}

/**
 * 학교 시간표 — 수업을 직접 입력하거나, 시간표 사진·캘린더에서 가져온 뒤 요일별 첫 수업으로 알람을 만든다(PRO).
 */
@Composable
fun TimetableScreen(
    state: TimetableUiState,
    onBack: () -> Unit,
    onPickImage: () -> Unit,
    onOpenCalendar: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (TimetableClass) -> Unit,
    onClearAll: () -> Unit,
    onCreateAlarms: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    var confirmClear by remember { mutableStateOf(false) }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.timetable_clear_confirm_title)) },
            text = { Text(stringResource(R.string.timetable_clear_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClearAll() }) {
                    Text(stringResource(R.string.common_delete), color = colors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(onBack = onBack, title = stringResource(R.string.timetable_title))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Paragraph(stringResource(R.string.timetable_body))
            Spacer(Modifier.height(16.dp))
            // 가져오기 방법 세 가지
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImportButton(Icons.Outlined.Image, stringResource(R.string.timetable_from_image), onPickImage, Modifier.weight(1f), loading = state.isRecognizing)
                ImportButton(Icons.Outlined.CalendarToday, stringResource(R.string.timetable_from_calendar), onOpenCalendar, Modifier.weight(1f))
                ImportButton(Icons.Rounded.Add, stringResource(R.string.timetable_add_class), onAdd, Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.lg))

            if (state.classes.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.timetable_empty_title),
                    body = stringResource(R.string.timetable_empty_body),
                    icon = Icons.Outlined.CalendarToday,
                )
            } else {
                state.classes.groupBy { it.day }.forEach { (day, classes) ->
                    SectionLabel(dayLongName(day))
                    Spacer(Modifier.height(8.dp))
                    SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
                        classes.forEach { item -> ClassRow(item, onClick = { onEdit(item) }) }
                    }
                    Spacer(Modifier.height(Spacing.md))
                }
                TextLink(stringResource(R.string.timetable_clear_all), onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        if (state.proposals.isNotEmpty()) {
            PrimaryButton(
                text = stringResource(R.string.timetable_create_alarms, state.proposals.size),
                onClick = onCreateAlarms,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun ImportButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier, loading: Boolean = false) {
    val colors = RouteAlarmTheme.colors
    Column(
        modifier
            .background(colors.surface, Radius.tile)
            .clickable(enabled = !loading, onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.padding(1.dp).width(22.dp).height(22.dp), color = colors.accent, strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.width(24.dp).height(24.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.textPrimary, maxLines = 1)
    }
}

@Composable
private fun ClassRow(item: TimetableClass, onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(TimeFormat.time(item.start), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, modifier = Modifier.widthIn(min = 56.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.room?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
        }
    }
}

@Composable
private fun dayLongName(day: DayOfWeek): String = stringResource(R.string.timetable_day_format, dayShortName(day))

/* ───────────── 수업 추가·수정 ───────────── */

@Composable
private fun ClassEditorSheet(
    editor: ClassEditor,
    name: String,
    room: String,
    onNameChange: (String) -> Unit,
    onRoomChange: (String) -> Unit,
    onDay: (DayOfWeek) -> Unit,
    onStart: (java.time.LocalTime) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.screen).padding(bottom = 12.dp)) {
            Text(
                stringResource(if (editor.id == null) R.string.timetable_add_class else R.string.timetable_edit_class),
                style = MaterialTheme.typography.headlineSmall,
                color = RouteAlarmTheme.colors.textPrimary,
            )
            Spacer(Modifier.height(16.dp))
            TextInput(name, onNameChange, stringResource(R.string.timetable_class_name))
            Spacer(Modifier.height(8.dp))
            TextInput(room, onRoomChange, stringResource(R.string.timetable_class_room))
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    DayChip(dayShortName(day), selected = editor.day == day, onClick = { onDay(day) }, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.timetable_class_start))
            WheelTimePicker(time = editor.start, onTimeChange = onStart, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            PrimaryButton(stringResource(R.string.common_save), onClick = onSave, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth())
            if (editor.id != null) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton(stringResource(R.string.timetable_delete_class), onClick = onDelete, danger = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TextInput(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = RouteAlarmTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, Radius.field)
            .semantics { contentDescription = placeholder }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = colors.textTertiary)
            inner()
        },
    )
}

/* ───────────── 알람 만들기 ───────────── */

@Composable
private fun AlarmSheet(
    sheet: AlarmSheetState,
    proposals: List<TimetableAlarmProposal>,
    previousAlarmCount: Int,
    savedPlaces: List<Place>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onStartPicking: (Boolean) -> Unit,
    onCancelPicking: () -> Unit,
    onPick: (Place) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    AppBottomSheet(onDismiss = if (sheet.pickingOrigin != null) onCancelPicking else onDismiss) {
        Column(
            Modifier
                .padding(horizontal = Spacing.screen)
                .padding(bottom = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (sheet.pickingOrigin != null) {
                // 출발지·도착지 고르기: 즐겨찾기 또는 검색
                Text(
                    stringResource(if (sheet.pickingOrigin) R.string.timetable_pick_origin else R.string.timetable_pick_destination),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                val focus = remember { FocusRequester() }
                SearchField(query, onQueryChange, onSearch, focus, stringResource(R.string.place_search_hint))
                Spacer(Modifier.height(12.dp))
                if (query.isBlank()) {
                    savedPlaces.forEach { place ->
                        PlaceRow(placeIcon(place.type), place.name, place.address, selected = false, onClick = { onPick(place) })
                    }
                } else {
                    SearchResults(sheet.search, selected = null, onSelect = onPick, onRetry = onSearch)
                }
                Spacer(Modifier.height(8.dp))
                TextLink(stringResource(R.string.common_cancel), onClick = onCancelPicking, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text(stringResource(R.string.timetable_alarm_sheet_title), style = MaterialTheme.typography.headlineSmall, color = colors.textPrimary)
                Spacer(Modifier.height(6.dp))
                Paragraph(stringResource(R.string.timetable_alarm_sheet_body), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(14.dp))
                SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
                    proposals.forEach { proposal ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                proposal.days.sorted().map { dayShortName(it) }.joinToString("·"),
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                modifier = Modifier.widthIn(min = 64.dp),
                            )
                            Text(
                                stringResource(R.string.timetable_proposal_arrival, TimeFormat.time(proposal.arrival)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                proposal.firstClassNames.distinct().joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                SectionLabel(stringResource(R.string.timetable_origin))
                Spacer(Modifier.height(6.dp))
                PlaceRow(
                    icon = sheet.origin?.let { placeIcon(it.type) } ?: Icons.Outlined.Home,
                    title = sheet.origin?.name ?: stringResource(R.string.timetable_choose_place),
                    subtitle = sheet.origin?.let { placeSubtitle(it) },
                    selected = false,
                    onClick = { onStartPicking(true) },
                )
                Spacer(Modifier.height(10.dp))
                SectionLabel(stringResource(R.string.timetable_destination))
                Spacer(Modifier.height(6.dp))
                PlaceRow(
                    icon = sheet.destination?.let { placeIcon(it.type) } ?: Icons.Outlined.Place,
                    title = sheet.destination?.name ?: stringResource(R.string.timetable_choose_place),
                    subtitle = sheet.destination?.let { placeSubtitle(it) },
                    selected = false,
                    onClick = { onStartPicking(false) },
                )
                if (previousAlarmCount > 0) {
                    // 학기가 바뀌어 다시 만드는 경우: 예전 시간표 알람이 새 알람으로 바뀐다는 것을 미리 알린다.
                    Spacer(Modifier.height(12.dp))
                    Paragraph(
                        stringResource(R.string.timetable_replaces_previous, previousAlarmCount),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
                PrimaryButton(
                    text = stringResource(R.string.timetable_create_alarms, proposals.size),
                    onClick = onCreate,
                    enabled = sheet.origin != null && sheet.destination != null,
                    loading = sheet.isCreating,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
