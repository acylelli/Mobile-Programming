package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme

/**
 * 아이폰 설정의 소리 크기 막대처럼 생긴 볼륨 슬라이더.
 * 둥근 카드 안에 작은/큰 스피커 아이콘, 얇은 막대, 흰 알약 손잡이를 둔다.
 * 채워진 막대는 테마 강조색을 따라가서 테마를 바꾸면 색도 바뀐다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VolumeSlider(
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 5,
    enabled: Boolean = true,
    onChangeFinished: () -> Unit = {},
) {
    val colors = RouteAlarmTheme.colors
    val active = colors.accent
    val inactive = colors.dotted
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.card)
            .background(colors.surface)
            .padding(horizontal = 14.dp)
            .alpha(if (enabled) 1f else 0.4f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.VolumeMute, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(((it / step).toInt() * step).coerceIn(range.first, range.last)) },
            onValueChangeFinished = onChangeFinished,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = enabled,
            thumb = {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 28.dp)
                        .shadow(3.dp, CircleShape)
                        .background(Color.White, CircleShape),
                )
            },
            track = { state ->
                val fraction = ((state.value - state.valueRange.start) / (state.valueRange.endInclusive - state.valueRange.start)).coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxWidth().height(TRACK_HEIGHT)) {
                    val radius = CornerRadius(size.height / 2, size.height / 2)
                    drawRoundRect(inactive, size = size, cornerRadius = radius)
                    drawRoundRect(active, topLeft = Offset.Zero, size = Size(size.width * fraction, size.height), cornerRadius = radius)
                }
            },
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 10.dp),
        )
        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(22.dp))
    }
}

private val TRACK_HEIGHT = 8.dp
