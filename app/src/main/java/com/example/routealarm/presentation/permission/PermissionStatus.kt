package com.example.routealarm.presentation.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

enum class PermissionStatus { GRANTED, DENIED, NOT_REQUIRED }

data class PermissionSnapshot(
    val notifications: PermissionStatus,
    val exactAlarm: PermissionStatus,
    val location: PermissionStatus,
) {
    /** 알람이 제대로 동작하려면 알림 + 정확한 알람이 필요하다. 위치는 선택. */
    val essentialGranted: Boolean get() = notifications != PermissionStatus.DENIED && exactAlarm != PermissionStatus.DENIED
}

fun readPermissionSnapshot(context: Context): PermissionSnapshot {
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        granted(context, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        PermissionStatus.NOT_REQUIRED
    }
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val exactAlarm = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> PermissionStatus.NOT_REQUIRED
        alarmManager.canScheduleExactAlarms() -> PermissionStatus.GRANTED
        else -> PermissionStatus.DENIED
    }
    return PermissionSnapshot(
        notifications = notifications,
        exactAlarm = exactAlarm,
        location = granted(context, Manifest.permission.ACCESS_COARSE_LOCATION),
    )
}

private fun granted(context: Context, permission: String) =
    if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        PermissionStatus.GRANTED
    } else {
        PermissionStatus.DENIED
    }

/** 설정 앱에 다녀온 뒤(ON_RESUME) 권한 상태를 다시 읽는다. */
@Composable
fun rememberPermissionStatus(): PermissionSnapshot {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var snapshot by remember { mutableStateOf(readPermissionSnapshot(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) snapshot = readPermissionSnapshot(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return snapshot
}
