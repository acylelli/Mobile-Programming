package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme

/**
 * 화면의 주 행동 버튼. 설계서의 검정 60dp 버튼으로, 화면마다 하나만 둔다.
 * 높이 60dp 로 터치 영역을 충분히 확보한다(INR-002).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = RouteAlarmTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        enabled = enabled && !loading,
        shape = Radius.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.button,
            contentColor = colors.onButton,
            disabledContainerColor = if (loading) colors.button else colors.surfaceStrong,
            disabledContentColor = if (loading) colors.onButton else colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = colors.onButton)
        } else {
            Text(text, style = AppType.Button, maxLines = 1)
        }
    }
}

/** 보조 행동(다시 계산, 지도 열기 등). 테두리만 있는 52dp 버튼 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = RouteAlarmTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = Radius.field,
        border = BorderStroke(1.dp, if (danger) colors.danger.copy(alpha = 0.4f) else colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (danger) colors.danger else colors.textPrimary,
            disabledContentColor = colors.textTertiary,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** "나중에 설정할게요" 같은 밑줄 글자 링크. 터치 영역은 48dp 를 확보한다. */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(textDecoration = TextDecoration.Underline),
        color = RouteAlarmTheme.colors.textSecondary,
        modifier = modifier
            .clip(Radius.tile)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun ButtonsPreview() {
    RouteAlarmTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(text = "이 시간으로 알람 설정", onClick = {}, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "다음", onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth())
            PrimaryButton(text = "계산 중", onClick = {}, loading = true, modifier = Modifier.fillMaxWidth())
            SecondaryButton(text = "다시 계산", onClick = {}, modifier = Modifier.fillMaxWidth())
            TextLink(text = "나중에 설정할게요", onClick = {})
        }
    }
}
