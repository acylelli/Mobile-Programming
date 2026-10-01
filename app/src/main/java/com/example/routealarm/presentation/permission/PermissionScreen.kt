package com.example.routealarm.presentation.permission

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing

/**
 * 권한을 요청하기 전에 "왜 필요한지"를 먼저 설명한다. 거부해도 앱은 계속 사용할 수 있다.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    // 권한 요청 결과가 돌아오면 다시 읽기 위한 트리거
    var refreshTick by remember { mutableIntStateOf(0) }
    val snapshot = remember(refreshTick) { readPermissionSnapshot(context) }.let { rememberPermissionStatus().takeIf { refreshTick >= 0 } ?: it }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refreshTick++ }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshTick++ }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xl))
        Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.sm))

        PermissionItem(
            title = stringResource(R.string.permission_notifications_title),
            body = stringResource(R.string.permission_notifications_body),
            status = snapshot.notifications,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenSettings = { context.openAppSettings() },
        )
        PermissionItem(
            title = stringResource(R.string.permission_exact_alarm_title),
            body = stringResource(R.string.permission_exact_alarm_body),
            status = snapshot.exactAlarm,
            onGrant = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }
            },
            onOpenSettings = { context.openAppSettings() },
        )
        PermissionItem(
            title = stringResource(R.string.permission_location_title),
            body = stringResource(R.string.permission_location_body),
            status = snapshot.location,
            onGrant = {
                locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            },
            onOpenSettings = { context.openAppSettings() },
        )

        Spacer(Modifier.weight(1f))
        PrimaryButton(text = stringResource(R.string.permission_continue), onClick = onContinue, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun PermissionItem(
    title: String,
    body: String,
    status: PermissionStatus,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AppCard {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(Spacing.sm))
                when (status) {
                    PermissionStatus.GRANTED, PermissionStatus.NOT_REQUIRED -> Text(
                        stringResource(R.string.permission_granted),
                        style = MaterialTheme.typography.labelLarge,
                        color = RouteAlarmTheme.appColors.success,
                    )
                    PermissionStatus.DENIED -> TextButton(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
                }
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (status == PermissionStatus.DENIED) {
                TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
            }
        }
    }
}

private fun android.content.Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
