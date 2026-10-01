package com.example.routealarm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("RouteAlarm") }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"

        /** 알림을 눌렀을 때 해당 일정 상세로 들어가는 인텐트 */
        fun createIntent(context: Context, scheduleId: Long? = null): Intent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { scheduleId?.let { putExtra(EXTRA_SCHEDULE_ID, it) } }
    }
}
