package com.example.routealarm.alarm.service

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.presentation.common.TimeFormat
import dagger.hilt.android.AndroidEntryPoint

/**
 * 알람이 울릴 때 잠금화면 위에 뜨는 화면. 소리/진동은 AlarmService 가 담당하고 여기서는 끄기/다시 울림만 보낸다.
 * 서비스가 끝나면(알림 액션으로 껐을 때도) 자동으로 닫힌다.
 */
@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    private val viewModel: AlarmRingingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1)
        if (scheduleId < 0) { finish(); return }
        viewModel.load(scheduleId)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
            LaunchedEffect(ringing) { if (ringing == null) finish() }

            RouteAlarmTheme {
                AlarmRingingScreen(
                    state = state,
                    onSnooze = { AlarmService.snooze(this, scheduleId) },
                    onDismiss = { AlarmService.dismiss(this, scheduleId) },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        fun createIntent(context: Context, scheduleId: Long): Intent =
            Intent(context, AlarmRingingActivity::class.java)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
fun AlarmRingingScreen(state: AlarmRingingUiState, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val plan = state.schedule?.plan
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary).padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            plan?.wakeUp?.let(TimeFormat::time) ?: "",
            style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
        Text(stringResource(R.string.ring_title), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
        Spacer(Modifier.height(Spacing.md))
        if (state.schedule != null && plan != null) {
            Text(state.schedule.destination.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
            Text(
                stringResource(R.string.ring_subtitle_format, TimeFormat.time(plan.departure), TimeFormat.time(plan.estimatedArrival)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(Spacing.xxl))
        PrimaryButton(
            text = stringResource(R.string.ring_dismiss),
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.sm))
        SecondaryButton(
            text = stringResource(R.string.ring_snooze_format, state.snoozeMinutes),
            onClick = onSnooze,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
