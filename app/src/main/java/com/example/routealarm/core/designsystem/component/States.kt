package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Alarm,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = RouteAlarmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).background(colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = colors.textPrimary)
        }
        Spacer(Modifier.height(Spacing.md))
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary, textAlign = TextAlign.Center)
        if (body.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            Paragraph(body, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.height(Spacing.lg))
            action()
        }
    }
}

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val colors = RouteAlarmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 3.dp, color = colors.accent, trackColor = colors.surfaceStrong)
        if (message != null) {
            Spacer(Modifier.height(Spacing.md))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    onRetry: (() -> Unit)? = null,
    retryText: String = "",
) {
    val colors = RouteAlarmTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(44.dp), tint = colors.danger)
        Spacer(Modifier.height(Spacing.md))
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Spacing.xs))
        }
        Paragraph(message, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(Spacing.lg))
            SecondaryButton(text = retryText, onClick = onRetry)
        }
    }
}

/**
 * 화면 안에 끼워 넣는 안내 띠(권한 필요, 오프라인, 꺼진 알람). 아이콘 + 문구 + 선택적 행동.
 * [danger] 면 빨강 계열로 주의를 끈다. 색과 함께 아이콘이 바뀌어 색만으로 구분하지 않는다.
 */
@Composable
fun NoticeStrip(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    danger: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = RouteAlarmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (danger) colors.dangerContainer else colors.surface, Radius.field)
            .padding(start = 16.dp, end = if (action != null) 4.dp else 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (danger) colors.danger else colors.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (danger) colors.danger else colors.textPrimary)
            }
            Paragraph(text, style = MaterialTheme.typography.bodySmall)
        }
        if (action != null) action()
    }
}

/** 데이터 로딩 중 자리를 차지하는 Skeleton. 화려한 shimmer 대신 단색 블록으로 절제한다. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Box(modifier.height(height).background(RouteAlarmTheme.colors.surface, RoundedCornerShape(8.dp)))
}

@Preview(showBackground = true)
@Composable
private fun StatesPreview() {
    RouteAlarmTheme {
        Column {
            EmptyState(title = "아직 등록된 일정이 없어요", body = "도착지와 도착 시간만 알려주시면 알람을 계산해드려요.")
            NoticeStrip(title = "권한 필요", text = "알림을 허용해야 알람이 제때 울려요.", danger = true, modifier = Modifier.padding(16.dp))
            LoadingState(message = "불러오는 중…")
            ErrorState(message = "인터넷 연결을 확인해 주세요.", onRetry = {}, retryText = "다시 시도")
        }
    }
}
