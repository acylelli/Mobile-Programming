package com.example.routealarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.presentation.MainViewModel
import com.example.routealarm.presentation.navigation.RouteAlarmApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var requestedScheduleId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // setKeepOnScreenCondition 은 쓰지 않는다. 앱 업데이트 직후 시스템이 Activity 를 되살리는 경로에서
        // Splash 창이 제거되지 않고 남는 현상을 에뮬레이터에서 확인했다. 설정 로딩은 수 ms 라서
        // Compose 쪽에서 isLoading 동안 아무것도 그리지 않는 것으로 충분하다.
        requestedScheduleId = intent.scheduleIdOrNull()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            RouteAlarmTheme(themeMode = state.themeMode) {
                if (!state.isLoading) {
                    RouteAlarmApp(
                        onboardingCompleted = state.onboardingCompleted,
                        onCompleteOnboarding = viewModel::completeOnboarding,
                        initialScheduleId = requestedScheduleId,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedScheduleId = intent.scheduleIdOrNull()
    }

    private fun Intent.scheduleIdOrNull(): Long? =
        if (hasExtra(EXTRA_SCHEDULE_ID)) getLongExtra(EXTRA_SCHEDULE_ID, -1).takeIf { it >= 0 } else null

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        /** 알림을 눌렀을 때 해당 일정 상세로 들어가는 인텐트 */
        fun createIntent(context: Context, scheduleId: Long? = null): Intent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { scheduleId?.let { putExtra(EXTRA_SCHEDULE_ID, it) } }
    }
}
