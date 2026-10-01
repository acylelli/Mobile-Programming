package com.example.routealarm.presentation.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.AlarmSound
import com.example.routealarm.domain.model.ThemeMode
import com.example.routealarm.domain.model.UserPreferences
import com.example.routealarm.presentation.permission.PermissionStatus
import com.example.routealarm.presentation.permission.rememberPermissionStatus

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val refreshDone by viewModel.refreshDone.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(refreshDone) {
        if (refreshDone) {
            snackbar.showSnackbar(context.getString(R.string.common_done))
            viewModel.consumeRefreshDone()
        }
    }
    SettingsScreen(
        state = state,
        snackbarHostState = snackbar,
        onPreparation = viewModel::setDefaultPreparation,
        onBuffer = viewModel::setDefaultBuffer,
        onAutoAdjust = viewModel::setAutoAdjustment,
        onTrafficNotifications = viewModel::setTrafficNotifications,
        onVibration = viewModel::setVibration,
        onSound = viewModel::setAlarmSound,
        onSnooze = viewModel::setSnoozeMinutes,
        onTheme = viewModel::setThemeMode,
        onDemoDelay = viewModel::setDemoExtraDelay,
        onDemoNetworkFailure = viewModel::setDemoNetworkFailure,
        onRefreshAll = viewModel::refreshAllNow,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    snackbarHostState: SnackbarHostState,
    onPreparation: (Int) -> Unit,
    onBuffer: (Int) -> Unit,
    onAutoAdjust: (Boolean) -> Unit,
    onTrafficNotifications: (Boolean) -> Unit,
    onVibration: (Boolean) -> Unit,
    onSound: (AlarmSound) -> Unit,
    onSnooze: (Int) -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onDemoDelay: (Int) -> Unit,
    onDemoNetworkFailure: (Boolean) -> Unit,
    onRefreshAll: () -> Unit,
) {
    val prefs = state.preferences
    val context = LocalContext.current
    val permissions = rememberPermissionStatus()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Spacing.md, end = Spacing.md, top = Spacing.sm, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Section(stringResource(R.string.settings_section_alarm)) {
                    MinuteSlider(
                        label = stringResource(R.string.settings_default_preparation),
                        value = prefs.defaultPreparationMinutes,
                        range = 5..120,
                        step = 5,
                        onChange = onPreparation,
                    )
                    MinuteSlider(
                        label = stringResource(R.string.settings_default_buffer),
                        value = prefs.defaultBufferMinutes,
                        range = 0..60,
                        step = 5,
                        onChange = onBuffer,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_auto_adjust),
                        body = stringResource(R.string.settings_auto_adjust_body),
                        checked = prefs.autoAdjustment,
                        onChange = onAutoAdjust,
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_traffic_notifications),
                        checked = prefs.trafficNotificationsEnabled,
                        onChange = onTrafficNotifications,
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_sound)) {
                    SwitchRow(title = stringResource(R.string.settings_vibration), checked = prefs.vibrationEnabled, onChange = onVibration)
                    ChoiceRow(
                        label = stringResource(R.string.settings_alarm_sound),
                        options = AlarmSound.entries.map {
                            it to stringResource(
                                when (it) {
                                    AlarmSound.DEFAULT_ALARM -> R.string.settings_sound_default
                                    AlarmSound.GENTLE -> R.string.settings_sound_gentle
                                    AlarmSound.SILENT -> R.string.settings_sound_silent
                                },
                            )
                        },
                        selected = prefs.alarmSound,
                        onSelect = onSound,
                    )
                    ChoiceRow(
                        label = stringResource(R.string.settings_snooze),
                        options = UserPreferences.SNOOZE_OPTIONS.map { it to stringResource(R.string.common_minutes_format, it) },
                        selected = prefs.snoozeMinutes,
                        onSelect = onSnooze,
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_display)) {
                    ChoiceRow(
                        label = stringResource(R.string.settings_theme),
                        options = listOf(
                            ThemeMode.SYSTEM to stringResource(R.string.settings_theme_system),
                            ThemeMode.LIGHT to stringResource(R.string.settings_theme_light),
                            ThemeMode.DARK to stringResource(R.string.settings_theme_dark),
                        ),
                        selected = prefs.themeMode,
                        onSelect = onTheme,
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_permissions)) {
                    PermissionRow(stringResource(R.string.permission_notifications_title), permissions.notifications)
                    PermissionRow(stringResource(R.string.permission_exact_alarm_title), permissions.exactAlarm)
                    PermissionRow(stringResource(R.string.permission_location_title), permissions.location)
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryButton(
                        text = stringResource(R.string.permission_open_settings),
                        onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_demo)) {
                    Text(
                        stringResource(R.string.settings_demo_provider_format, state.transitProvider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MinuteSlider(
                        label = stringResource(R.string.settings_demo_delay),
                        value = prefs.demo.extraDelayMinutes,
                        range = 0..60,
                        step = 5,
                        onChange = onDemoDelay,
                        body = stringResource(R.string.settings_demo_delay_body),
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_demo_network_failure),
                        body = stringResource(R.string.settings_demo_network_failure_body),
                        checked = prefs.demo.simulateNetworkFailure,
                        onChange = onDemoNetworkFailure,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    SecondaryButton(
                        text = if (state.isRefreshingAll) stringResource(R.string.home_refreshing) else stringResource(R.string.settings_demo_run_refresh),
                        onClick = onRefreshAll,
                        enabled = !state.isRefreshingAll,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                val m = state.metrics
                Section(stringResource(R.string.settings_section_metrics)) {
                    MetricLine(stringResource(R.string.settings_metrics_api_calls, m.apiCallCount, m.apiFailureCount))
                    m.averageApiLatencyMillis?.let { MetricLine(stringResource(R.string.settings_metrics_latency, it)) }
                    MetricLine(stringResource(R.string.settings_metrics_cache, m.cacheHitCount))
                    MetricLine(stringResource(R.string.settings_metrics_recalc, m.recalculationCount, m.autoAdjustedCount))
                    MetricLine(stringResource(R.string.settings_metrics_work, m.refreshWorkRunCount))
                }
            }
            item {
                Text(
                    stringResource(R.string.settings_version_format, state.versionName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.md),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = Spacing.sm, start = Spacing.xs))
        AppCard { Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content() } }
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, body: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun MinuteSlider(label: String, value: Int, range: IntRange, step: Int, onChange: (Int) -> Unit, body: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.common_minutes_format, value), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (body != null) Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first) / step - 1,
        )
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            options.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(text) })
            }
        }
    }
}

@Composable
private fun PermissionRow(title: String, status: PermissionStatus) {
    Row(Modifier.fillMaxWidth().padding(vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            stringResource(if (status == PermissionStatus.GRANTED) R.string.permission_granted else R.string.permission_denied),
            style = MaterialTheme.typography.labelLarge,
            color = if (status == PermissionStatus.GRANTED) com.example.routealarm.core.designsystem.theme.RouteAlarmTheme.appColors.success else MaterialTheme.colorScheme.error,
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun MetricLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
