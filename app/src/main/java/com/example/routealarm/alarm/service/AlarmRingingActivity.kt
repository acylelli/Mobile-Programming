package com.example.routealarm.alarm.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("Ringing") }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "schedule_id"
        fun createIntent(context: Context, scheduleId: Long): Intent =
            Intent(context, AlarmRingingActivity::class.java)
                .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}
