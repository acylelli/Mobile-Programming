package com.example.routealarm.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Traffic
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.alarm.service.AlarmRingingActivity
import com.example.routealarm.alarm.service.AlarmService
import com.example.routealarm.core.designsystem.component.AppIcons
import com.example.routealarm.core.designsystem.component.AppSwitch
import com.example.routealarm.core.designsystem.component.ChoiceSheet
import com.example.routealarm.core.designsystem.component.MinutePickerSheet
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.ProAccentBadge
import com.example.routealarm.core.designsystem.component.ProBadge
import com.example.routealarm.core.designsystem.component.ScreenHeader
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ColorTheme
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.PlanType
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import com.example.routealarm.presentation.permission.PermissionStatus
import com.example.routealarm.presentation.permission.rememberPermissionStatus
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    onShowPaywall: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenGroupPass: () -> Unit,
    onOpenSubscription: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.RefreshDone -> snackbar.showSnackbar(context.getString(R.string.settings_demo_refresh_done))
                SettingsEvent.NoScheduleToRing -> snackbar.showSnackbar(context.getString(R.string.settings_demo_ring_empty))
                is SettingsEvent.RingAlarm -> {
                    // 실제 알람과 같은 경로: 서비스가 소리·진동을 맡고 화면은 그 상태를 보여준다.
                    AlarmService.start(context, event.scheduleId)
                    context.startActivity(AlarmRingingActivity.createIntent(context, event.scheduleId))
                }
            }
        }
    }

    SettingsScreen(
        state = state,
        onShowPaywall = onShowPaywall,
        onOpenTheme = onOpenTheme,
        onOpenGroupPass = onOpenGroupPass,
        onOpenSubscription = onOpenSubscription,
        onPreparation = viewModel::setDefaultPreparation,
        onBuffer = viewModel::setDefaultBuffer,
        onAutoAdjust = viewModel::setAutoAdjustment,
        onTrafficNotifications = viewModel::setTrafficNotifications,
        onVibration = viewModel::setVibration,
        onSound = viewModel::setAlarmSound,
        onSnooze = viewModel::setSnoozeMinutes,
        onDemoDelay = viewModel::setDemoExtraDelay,
        onDemoNetworkFailure = viewModel::setDemoNetworkFailure,
        onRefreshAll = viewModel::refreshAllNow,
        onRingAlarm = viewModel::ringAlarmNow,
    )
}

private enum class SettingsSheet { PREPARATION, BUFFER, SOUND, SNOOZE, DEMO_DELAY }

/** 설정 — UI-F-001 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onShowPaywall: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenGroupPass: () -> Unit,
    onOpenSubscription: () -> Unit,
    onPreparation: (Int) -> Unit,
    onBuffer: (Int) -> Unit,
    onAutoAdjust: (Boolean) -> Unit,
    onTrafficNotifications: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onSound: (AlarmSound) -> Unit,
    onSnooze: (Int) -> Unit,
    onDemoDelay: (Int) -> Unit,
    onDemoNetworkFailure: (Boolean) -> Unit,
    onRefreshAll: () -> Unit,
    onRingAlarm: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val prefs = state.preferences
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val permissions = rememberPermissionStatus()
    var sheet by remember { mutableStateOf<SettingsSheet?>(null) }
    val comingSoon = stringResource(R.string.settings_coming_soon)

    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState())) {
        ScreenHeader(stringResource(R.string.settings_title))

        Column(
            Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = 16.dp, bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // PRO 배너(①): 무료 플랜 사용자에게만
            if (!state.isPro) ProBanner(onClick = onShowPaywall)

            // 알람 기본값(②)
            SettingsGroup(stringResource(R.string.settings_section_alarm)) {
                SettingsRow(Icons.Outlined.Schedule, stringResource(R.string.settings_default_preparation), onClick = { sheet = SettingsSheet.PREPARATION }) {
                    RowValue(stringResource(R.string.common_minutes_format, prefs.defaultPreparationMinutes))
                }
                RowDivider()
                SettingsRow(Icons.Outlined.Schedule, stringResource(R.string.settings_default_buffer), onClick = { sheet = SettingsSheet.BUFFER }) {
                    RowValue(stringResource(R.string.common_minutes_format, prefs.defaultBufferMinutes))
                }
                RowDivider()
                // PRO 잠금 항목(③): 무료 플랜에서는 PRO 배지와 자물쇠. 누르면 PRO 페이월
                if (state.isPro) {
                    SettingsRow(
                        Icons.Outlined.Sync,
                        stringResource(R.string.settings_auto_adjust),
                        body = stringResource(R.string.settings_auto_adjust_body),
                        onClick = { onAutoAdjust(!prefs.autoAdjustment) },
                    ) {
                        AppSwitch(checked = prefs.autoAdjustment, onCheckedChange = onAutoAdjust)
                    }
                } else {
                    SettingsRow(Icons.Outlined.Sync, stringResource(R.string.settings_auto_adjust), onClick = onShowPaywall) { RowLock() }
                }
            }

            // 연동
            SettingsGroup(stringResource(R.string.settings_section_integration)) {
                // 시간표·캘린더 연동은 PRO 기능이다. 무료 플랜에서는 페이월, PRO 에서는 준비 중 안내를 보여준다.
                val onIntegration: () -> Unit = {
                    if (state.isPro) {
                        scope.launch { snackbar.showSnackbar(comingSoon) }
                    } else {
                        onShowPaywall()
                    }
                }
                SettingsRow(Icons.AutoMirrored.Outlined.MenuBook, stringResource(R.string.settings_timetable), onClick = onIntegration) {
                    if (state.isPro) RowValue(stringResource(R.string.settings_coming_soon_short)) else RowLock()
                }
                RowDivider()
                SettingsRow(Icons.Outlined.CalendarToday, stringResource(R.string.settings_calendar), onClick = onIntegration) {
                    if (state.isPro) RowValue(stringResource(R.string.settings_coming_soon_short)) else RowLock()
                }
            }

            // 화면(④): 테마
            SettingsGroup(stringResource(R.string.settings_section_display)) {
                SettingsRow(Icons.Outlined.Palette, stringResource(R.string.settings_theme), onClick = onOpenTheme) {
                    RowValue(themeName(prefs.colorTheme, long = true))
                }
            }

            // 구독(⑤⑥)
            SettingsGroup(stringResource(R.string.settings_section_subscription)) {
                SettingsRow(Icons.Outlined.ConfirmationNumber, stringResource(R.string.settings_group_pass), onClick = onOpenGroupPass) {
                    RowValue(stringResource(R.string.settings_group_pass_value))
                }
                RowDivider()
                SettingsRow(Icons.Outlined.CreditCard, stringResource(R.string.settings_subscription), onClick = onOpenSubscription) {
                    RowValue(planName(state.entitlement, state.isPro))
                }
            }

            // 알람 소리와 알림(SFR-013)
            SettingsGroup(stringResource(R.string.settings_section_sound)) {
                SettingsRow(Icons.Outlined.Traffic, stringResource(R.string.settings_traffic_notifications), onClick = { onTrafficNotifications(!prefs.trafficNotificationsEnabled) }) {
                    AppSwitch(checked = prefs.trafficNotificationsEnabled, onCheckedChange = onTrafficNotifications)
                }
                RowDivider()
                SettingsRow(Icons.Outlined.Vibration, stringResource(R.string.settings_vibration), onClick = { onVibration(!prefs.vibrationEnabled) }) {
                    AppSwitch(checked = prefs.vibrationEnabled, onCheckedChange = onVibration)
                }
                RowDivider()
                SettingsRow(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.settings_alarm_sound), onClick = { sheet = SettingsSheet.SOUND }) {
                    RowValue(soundName(prefs.alarmSound))
                }
                RowDivider()
                SettingsRow(Icons.Outlined.Snooze, stringResource(R.string.settings_snooze), onClick = { sheet = SettingsSheet.SNOOZE }) {
                    RowValue(stringResource(R.string.common_minutes_format, prefs.snoozeMinutes))
                }
            }

            // 권한 상태: 허용되지 않은 권한은 '권한 필요'로 표시한다. 누르면 시스템 설정으로 이동
            SettingsGroup(stringResource(R.string.settings_section_permissions)) {
                PermissionRow(Icons.Outlined.NotificationsNone, R.string.permission_notifications_title, permissions.notifications) { context.openAppSettings() }
                RowDivider()
                PermissionRow(AppIcons.Alarm, R.string.permission_exact_alarm_title, permissions.exactAlarm) { context.openAppSettings() }
                RowDivider()
                PermissionRow(Icons.Outlined.MyLocation, R.string.permission_location_title, permissions.location, optional = true) { context.openAppSettings() }
            }

            // 데모: API 키 없이 지연·오프라인·알람 화면을 시연
            SettingsGroup(stringResource(R.string.settings_section_demo)) {
                SettingsRow(Icons.Outlined.Timelapse, stringResource(R.string.settings_demo_delay), onClick = { sheet = SettingsSheet.DEMO_DELAY }) {
                    RowValue(stringResource(R.string.common_minutes_format, prefs.demo.extraDelayMinutes))
                }
                RowDivider()
                SettingsRow(Icons.Outlined.CloudOff, stringResource(R.string.settings_demo_network_failure), onClick = { onDemoNetworkFailure(!prefs.demo.simulateNetworkFailure) }) {
                    AppSwitch(checked = prefs.demo.simulateNetworkFailure, onCheckedChange = onDemoNetworkFailure)
                }
                RowDivider()
                SettingsRow(
                    Icons.Outlined.Sync,
                    stringResource(if (state.isRefreshingAll) R.string.home_refreshing else R.string.settings_demo_run_refresh),
                    onClick = if (state.isRefreshingAll) null else onRefreshAll,
                ) { RowChevron() }
                RowDivider()
                SettingsRow(AppIcons.Alarm, stringResource(R.string.settings_demo_ring), onClick = onRingAlarm) { RowChevron() }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val m = state.metrics
                SectionLabel(stringResource(R.string.settings_section_metrics), Modifier.padding(bottom = 6.dp))
                MetricLine(stringResource(R.string.settings_demo_provider_format, state.transitProvider))
                MetricLine(stringResource(R.string.settings_metrics_api_calls, m.apiCallCount, m.apiFailureCount))
                m.averageApiLatencyMillis?.let { MetricLine(stringResource(R.string.settings_metrics_latency, it)) }
                MetricLine(stringResource(R.string.settings_metrics_cache, m.cacheHitCount))
                MetricLine(stringResource(R.string.settings_metrics_recalc, m.recalculationCount, m.autoAdjustedCount))
                MetricLine(stringResource(R.string.settings_metrics_work, m.refreshWorkRunCount))
            }

            Text(
                stringResource(R.string.settings_version_format, state.versionName),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }

    when (sheet) {
        SettingsSheet.PREPARATION -> MinutePickerSheet(
            title = stringResource(R.string.settings_default_preparation),
            body = stringResource(R.string.settings_default_preparation_body),
            initial = prefs.defaultPreparationMinutes,
            range = 5..120,
            presets = listOf(15, 30, 45, 60),
            onConfirm = { onPreparation(it); sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.BUFFER -> MinutePickerSheet(
            title = stringResource(R.string.settings_default_buffer),
            body = stringResource(R.string.settings_default_buffer_body),
            initial = prefs.defaultBufferMinutes,
            range = 0..60,
            presets = listOf(5, 10, 20),
            onConfirm = { onBuffer(it); sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.DEMO_DELAY -> MinutePickerSheet(
            title = stringResource(R.string.settings_demo_delay),
            body = stringResource(R.string.settings_demo_delay_body),
            initial = prefs.demo.extraDelayMinutes,
            range = 0..60,
            presets = listOf(0, 10, 15, 40),
            onConfirm = { onDemoDelay(it); sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.SOUND -> ChoiceSheet(
            title = stringResource(R.string.settings_alarm_sound),
            options = AlarmSound.entries.map { it to soundName(it) },
            selected = prefs.alarmSound,
            onSelect = { onSound(it); sheet = null },
            onDismiss = { sheet = null },
        )
        SettingsSheet.SNOOZE -> ChoiceSheet(
            title = stringResource(R.string.settings_snooze),
            options = UserPreferences.SNOOZE_OPTIONS.map { it to stringResource(R.string.common_minutes_format, it) },
            selected = prefs.snoozeMinutes,
            onSelect = { onSnooze(it); sheet = null },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }
}

/** PRO 배너(①): 7일 무료체험으로 진입 */
@Composable
private fun ProBanner(onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.button)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 18.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProAccentBadge()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.settings_pro_banner_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
                color = colors.onButton,
            )
            Text(
                stringResource(R.string.settings_pro_banner_body),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onButton.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(32.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        SectionLabel(title, Modifier.padding(bottom = 10.dp))
        SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group, content = content)
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    body: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val colors = RouteAlarmTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 54.dp)
            .padding(start = 16.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (body != null) Paragraph(body, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 1.dp, color = RouteAlarmTheme.colors.outline)
}

@Composable
private fun RowChevron() {
    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = RouteAlarmTheme.colors.textTertiary, modifier = Modifier.size(22.dp))
}

@Composable
private fun RowValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = RouteAlarmTheme.colors.textSecondary, maxLines = 1)
    Spacer(Modifier.width(4.dp))
    RowChevron()
}

/** PRO 전용 표시: PRO 배지와 자물쇠 아이콘(③) */
@Composable
private fun RowLock() {
    ProBadge()
    Spacer(Modifier.width(10.dp))
    Icon(
        Icons.Outlined.Lock,
        contentDescription = stringResource(R.string.cd_locked),
        tint = RouteAlarmTheme.colors.textTertiary,
        modifier = Modifier.padding(end = 4.dp).size(18.dp),
    )
}

@Composable
private fun PermissionRow(icon: ImageVector, titleRes: Int, status: PermissionStatus, optional: Boolean = false, onClick: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    val granted = status != PermissionStatus.DENIED
    SettingsRow(icon, stringResource(titleRes), onClick = onClick) {
        Text(
            stringResource(
                when {
                    granted -> R.string.permission_granted
                    optional -> R.string.permission_denied
                    else -> R.string.permission_needed
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = if (granted || optional) colors.textSecondary else colors.danger,
        )
        Spacer(Modifier.width(4.dp))
        RowChevron()
    }
}

@Composable
private fun MetricLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = RouteAlarmTheme.colors.textSecondary)
}

@Composable
internal fun themeName(theme: ColorTheme, long: Boolean = false): String = stringResource(
    when (theme) {
        ColorTheme.DEFAULT -> if (long) R.string.theme_default_long else R.string.theme_default
        ColorTheme.INDIGO -> R.string.theme_indigo
        ColorTheme.MONO -> R.string.theme_mono
        ColorTheme.DAWN -> R.string.theme_dawn
        ColorTheme.NIGHT -> R.string.theme_night
    },
)

@Composable
internal fun planName(entitlement: Entitlement, isPro: Boolean): String = stringResource(
    when {
        !isPro -> R.string.plan_free
        entitlement.plan == PlanType.PRO_YEARLY -> R.string.plan_pro_yearly
        entitlement.plan == PlanType.PRO_MONTHLY -> R.string.plan_pro_monthly
        else -> R.string.plan_group_pass
    },
)

@Composable
private fun soundName(sound: AlarmSound): String = stringResource(
    when (sound) {
        AlarmSound.DEFAULT_ALARM -> R.string.settings_sound_default
        AlarmSound.GENTLE -> R.string.settings_sound_gentle
        AlarmSound.SILENT -> R.string.settings_sound_silent
    },
)

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
