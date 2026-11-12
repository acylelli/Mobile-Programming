package com.example.routealarm.presentation.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.AppIcons
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.Pill
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SampleRouteStrip
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.TagBadge
import com.example.routealarm.core.designsystem.component.TextLink
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.presentation.common.SampleLineColors
import com.example.routealarm.presentation.common.transitLineTint
import com.example.routealarm.presentation.permission.PermissionStatus
import com.example.routealarm.presentation.permission.readPermissionSnapshot
import kotlinx.coroutines.launch

private const val PAGE_COUNT = 3

/**
 * 온보딩, 권한 안내 (UI-A-001).
 *
 * 서비스 소개 3페이지를 좌우로 넘기고, 마지막 페이지에서 권한이 왜 필요한지 먼저 설명한 뒤
 * 알림 → 정확한 알람 → 위치 순서로 요청한다. 권한을 거절하거나 [나중에 설정할게요]를 눌러도
 * 앱은 그대로 시작되고, 홈과 설정에 '권한 필요' 상태가 표시된다.
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    val pagerState = rememberPagerState { PAGE_COUNT }
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == PAGE_COUNT - 1
    val requestPermissions = rememberPermissionSequence(onFinish)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageIndicator(current = pagerState.currentPage)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.onboarding_page_format, pagerState.currentPage + 1, PAGE_COUNT),
                style = MaterialTheme.typography.titleSmall,
                color = colors.textSecondary,
            )
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen),
            ) {
                Spacer(Modifier.height(36.dp))
                when (page) {
                    0 -> {
                        Intro(R.string.onboarding_title_1, R.string.onboarding_body_1)
                        TargetCard()
                    }
                    1 -> {
                        Intro(R.string.onboarding_title_2, R.string.onboarding_body_2)
                        TransitCard()
                    }
                    else -> {
                        Intro(R.string.onboarding_title_3, R.string.onboarding_body_3)
                        ResultCard()
                        Spacer(Modifier.height(28.dp))
                        PermissionSection()
                    }
                }
                Spacer(Modifier.height(Spacing.lg))
            }
        }

        PrimaryButton(
            text = stringResource(if (isLast) R.string.onboarding_start else R.string.common_next),
            onClick = {
                if (isLast) requestPermissions() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        )
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            if (isLast) {
                TextLink(stringResource(R.string.onboarding_later), onClick = onFinish)
            } else {
                TextLink(
                    stringResource(R.string.onboarding_skip),
                    onClick = { scope.launch { pagerState.animateScrollToPage(PAGE_COUNT - 1) } },
                )
            }
        }
    }
}

/**
 * 알림 → 정확한 알람 → 위치 순서로 시스템 권한 창을 띄우고, 결과와 상관없이 마지막에 [onFinish] 를 호출한다.
 * 이미 허용됐거나 해당 Android 버전에서 필요 없는 권한은 건너뛴다.
 */
@Composable
private fun rememberPermissionSequence(onFinish: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val finish by rememberUpdatedState(onFinish)

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { finish() }
    val requestLocation = {
        if (readPermissionSnapshot(context).location == PermissionStatus.DENIED) {
            locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        } else {
            finish()
        }
    }

    val exactAlarmLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { requestLocation() }
    val requestExactAlarm = {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            readPermissionSnapshot(context).exactAlarm == PermissionStatus.DENIED
        val launched = needed && runCatching {
            exactAlarmLauncher.launch(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
            )
        }.isSuccess
        if (!launched) requestLocation()
    }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { requestExactAlarm() }
    return {
        val needed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            readPermissionSnapshot(context).notifications == PermissionStatus.DENIED
        if (needed) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else requestExactAlarm()
    }
}

/** 진행 표시(①): 현재 페이지는 긴 검정 막대, 나머지는 작은 점 */
@Composable
private fun PageIndicator(current: Int) {
    val colors = RouteAlarmTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(PAGE_COUNT) { index ->
            Box(
                Modifier
                    .size(width = if (index == current) 24.dp else 7.dp, height = 7.dp)
                    .background(if (index == current) colors.textPrimary else colors.outline, CircleShape),
            )
        }
    }
}

@Composable
private fun Intro(titleRes: Int, bodyRes: Int) {
    val colors = RouteAlarmTheme.colors
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.headlineLarge.copy(fontSize = MaterialTheme.typography.headlineLarge.fontSize * 1.07f),
        color = colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Paragraph(stringResource(bodyRes), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(Spacing.lg))
}

/** 1페이지: 사용자가 정하는 것은 "어디까지, 몇 시까지" 뿐 */
@Composable
private fun TargetCard() {
    val colors = RouteAlarmTheme.colors
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SampleRow(stringResource(R.string.onboarding_sample_from), stringResource(R.string.demo_origin_name))
            SampleRow(stringResource(R.string.onboarding_sample_to), stringResource(R.string.demo_destination_name))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.onboarding_sample_when), style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.onboarding_sample_target), style = MaterialTheme.typography.headlineMedium, color = colors.accentText)
            }
        }
    }
}

@Composable
private fun SampleRow(label: String, value: String) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
    }
}

/** 2페이지: 이동시간 = 도보 + 대기 + 탑승 + 환승 */
@Composable
private fun TransitCard() {
    val colors = RouteAlarmTheme.colors
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(stringResource(R.string.onboarding_sample_walk), containerColor = colors.background)
                Pill(
                    stringResource(R.string.onboarding_sample_line8),
                    containerColor = transitLineTint(SampleLineColors.Line8),
                    contentColor = SampleLineColors.Line8,
                )
                Pill(
                    stringResource(R.string.onboarding_sample_line2),
                    containerColor = transitLineTint(SampleLineColors.Line2),
                    contentColor = SampleLineColors.Line2,
                )
            }
            SampleRow(stringResource(R.string.onboarding_sample_travel), stringResource(R.string.common_minutes_format, 65))
            SampleRow(stringResource(R.string.onboarding_sample_buffer), stringResource(R.string.common_minutes_format, 10))
            SampleRow(stringResource(R.string.onboarding_sample_preparation), stringResource(R.string.common_minutes_format, 30))
        }
    }
}

/** 3페이지: 계산 결과 예시(기상 → 출발 → 도착) */
@Composable
private fun ResultCard() {
    val colors = RouteAlarmTheme.colors
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            SampleRouteStrip()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf(R.string.onboarding_sample_wake, R.string.onboarding_sample_depart, R.string.onboarding_sample_arrive).forEach {
                    Text(stringResource(it), style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                }
            }
        }
    }
}

/** 권한 안내(②③): 시스템 권한 창을 띄우기 전에 왜 필요한지 먼저 설명한다. */
@Composable
private fun PermissionSection() {
    Text(
        stringResource(R.string.permission_title),
        style = MaterialTheme.typography.titleMedium,
        color = RouteAlarmTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
    Spacer(Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PermissionCard(Icons.Outlined.NotificationsNone, R.string.permission_notifications_title, R.string.permission_notifications_body, required = true)
        PermissionCard(AppIcons.Alarm, R.string.permission_exact_alarm_title, R.string.permission_exact_alarm_body, required = true)
        PermissionCard(Icons.Outlined.MyLocation, R.string.permission_location_title, R.string.permission_location_body, required = false)
    }
}

@Composable
private fun PermissionCard(icon: ImageVector, titleRes: Int, bodyRes: Int, required: Boolean) {
    val colors = RouteAlarmTheme.colors
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(colors.surface, Radius.tile), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    if (required) {
                        TagBadge(stringResource(R.string.permission_required), containerColor = colors.textPrimary, contentColor = colors.background)
                    } else {
                        TagBadge(stringResource(R.string.permission_optional))
                    }
                }
                Paragraph(stringResource(bodyRes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true, heightDp = 860)
@Composable
private fun OnboardingPreview() {
    RouteAlarmTheme { OnboardingScreen(onFinish = {}) }
}
