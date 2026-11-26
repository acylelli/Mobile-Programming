package com.example.routealarm.presentation.calendar

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.EmptyState
import com.example.routealarm.core.designsystem.component.ErrorState
import com.example.routealarm.core.designsystem.component.LoadingState
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.repository.CalendarEvent
import com.example.routealarm.presentation.common.dayShortName
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 한 번뿐인 캘린더 일정을 눌렀을 때 새 일정 만들기에 채워 넘길 값 */
data class CalendarPrefill(val destinationQuery: String, val arrival: String, val date: String)

@Composable
fun CalendarImportRoute(
    onBack: () -> Unit,
    onCreateFromEvent: (CalendarPrefill) -> Unit,
    onOpenTimetable: () -> Unit,
    viewModel: CalendarImportViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = LocalAppSnackbar.current
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), viewModel::onPermissionResult)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CalendarImportEvent.AddedToTimetable -> {
                    snackbar.showSnackbar(resources.getString(R.string.calendar_added_to_timetable, event.count))
                    onOpenTimetable()
                }
            }
        }
    }

    CalendarImportScreen(
        state = state,
        onBack = onBack,
        onRequestPermission = { requestPermission.launch(Manifest.permission.READ_CALENDAR) },
        onRetry = viewModel::load,
        onAddWeekly = viewModel::addWeeklyToTimetable,
        onPickEvent = { event ->
            val local = event.begin.atZone(ZoneId.systemDefault())
            onCreateFromEvent(
                CalendarPrefill(
                    // 장소가 없으면 일정 제목으로 검색한다(예: "한성대 중간고사").
                    destinationQuery = event.location ?: event.title,
                    arrival = local.toLocalTime().withSecond(0).withNano(0).toString(),
                    date = local.toLocalDate().toString(),
                ),
            )
        },
    )
}

/**
 * 캘린더 연동 — 기기 캘린더의 앞으로 2주 일정에서 알람을 만든다(PRO).
 * 기기 캘린더를 읽기만 하며, Google 계정 연결이나 API 키가 필요 없다.
 */
@Composable
fun CalendarImportScreen(
    state: CalendarUiState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onRetry: () -> Unit,
    onAddWeekly: () -> Unit,
    onPickEvent: (CalendarEvent) -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(onBack = onBack, title = stringResource(R.string.calendar_title))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            when (state) {
                CalendarUiState.NeedsPermission -> {
                    Spacer(Modifier.height(Spacing.lg))
                    EmptyState(
                        title = stringResource(R.string.calendar_permission_title),
                        body = stringResource(R.string.calendar_permission_body),
                        icon = Icons.Outlined.CalendarToday,
                    ) {
                        PrimaryButton(stringResource(R.string.calendar_permission_allow), onClick = onRequestPermission)
                    }
                }
                CalendarUiState.Loading -> LoadingState()
                is CalendarUiState.Failed -> ErrorState(
                    message = stringResource(state.error.messageRes()),
                    onRetry = onRetry,
                    retryText = stringResource(R.string.common_retry),
                )
                is CalendarUiState.Loaded -> Loaded(state, onAddWeekly, onPickEvent)
            }
            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

@Composable
private fun Loaded(state: CalendarUiState.Loaded, onAddWeekly: () -> Unit, onPickEvent: (CalendarEvent) -> Unit) {
    Paragraph(stringResource(R.string.calendar_body))
    Spacer(Modifier.height(Spacing.md))
    if (state.weekly.isEmpty() && state.upcoming.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.calendar_empty_title),
            body = stringResource(R.string.calendar_empty_body),
            icon = Icons.Outlined.CalendarToday,
        )
        return
    }
    if (state.weekly.isNotEmpty()) {
        SectionLabel(stringResource(R.string.calendar_weekly))
        Spacer(Modifier.height(8.dp))
        SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
            state.weekly.forEach { EventRow(it, onClick = null) }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(stringResource(R.string.calendar_add_weekly, state.weekly.size), onClick = onAddWeekly, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.lg))
    }
    if (state.upcoming.isNotEmpty()) {
        SectionLabel(stringResource(R.string.calendar_upcoming))
        Spacer(Modifier.height(8.dp))
        SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
            state.upcoming.forEach { event -> EventRow(event, onClick = { onPickEvent(event) }) }
        }
        Spacer(Modifier.height(8.dp))
        Paragraph(stringResource(R.string.calendar_upcoming_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun EventRow(event: CalendarEvent, onClick: (() -> Unit)?) {
    val colors = RouteAlarmTheme.colors
    val local = event.begin.atZone(ZoneId.systemDefault())
    val whenText = if (event.weeklyDays.isNotEmpty()) {
        stringResource(R.string.calendar_weekly_format, event.weeklyDays.sorted().map { dayShortName(it) }.joinToString("·"), TIME.format(local))
    } else {
        DATE_TIME.format(local)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(whenText, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary, modifier = Modifier.widthIn(min = 96.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title.ifBlank { stringResource(R.string.calendar_untitled) }, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            event.location?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E) HH:mm", java.util.Locale.KOREAN)
