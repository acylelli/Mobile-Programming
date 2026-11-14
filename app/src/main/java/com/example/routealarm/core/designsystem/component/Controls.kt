package com.example.routealarm.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme

/**
 * 설계서의 넓은 스위치(켜짐 강조색, 꺼짐 회색). Material 스위치보다 크고 손잡이에 아이콘이 없다.
 * 켜짐/꺼짐은 색뿐 아니라 손잡이 위치로도 구분된다.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val colors = RouteAlarmTheme.colors
    val track by animateColorAsState(if (checked) colors.accent else colors.switchOff, label = "switchTrack")
    val thumbOffset by animateDpAsState(if (checked) 24.dp else 0.dp, label = "switchThumb")
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription }
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .size(width = 54.dp, height = 30.dp)
            .background(if (enabled) track else track.copy(alpha = 0.5f), CircleShape)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp)
                .background(Color.White, CircleShape),
        )
    }
}

/** 개인/단체처럼 둘 셋 중 하나를 고르는 세그먼트 컨트롤 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RouteAlarmTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(colors.surfaceStrong, RoundedCornerShape(16.dp))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val shape = RoundedCornerShape(12.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (selected) Modifier.shadow(1.dp, shape) else Modifier)
                    .clip(shape)
                    .background(if (selected) colors.background else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** 요금제 선택의 라디오 표시. 선택되면 강조색 원 안에 흰 점이 생긴다. */
@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Box(
        modifier = modifier
            .size(22.dp)
            .background(if (selected) colors.accent else colors.textTertiary.copy(alpha = 0.6f), CircleShape)
            .padding(if (selected) 7.dp else 2.dp)
            .background(if (selected) colors.onAccent else colors.background, CircleShape),
    )
}

/** 요일 선택 칩(계산 미리보기 ④). 선택은 검정 바탕 흰 글자, 미선택은 옅은 회색 */
@Composable
fun DayChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) colors.button else colors.surface)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.onButton else colors.textTertiary,
        )
    }
}

/** 선택형 알약 버튼(반복 프리셋, 준비 시간 프리셋, 알림 방식) */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RouteAlarmTheme.colors
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(if (selected) colors.button else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onButton else colors.textSecondary,
            maxLines = 1,
        )
    }
}

private const val MINUTE_STEP = 5

/** 5분 단위 슬라이더(준비 시간, 여유 시간). 눈금 표시는 숨겨 막대를 깔끔하게 둔다. */
@Composable
fun MinuteSlider(value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange((it / MINUTE_STEP).toInt() * MINUTE_STEP) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = ((range.last - range.first) / MINUTE_STEP - 1).coerceAtLeast(0),
        colors = SliderDefaults.colors(
            thumbColor = colors.accent,
            activeTrackColor = colors.accent,
            inactiveTrackColor = colors.dotted,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}

/** 글자 한 줄을 누를 수 있게 만드는 작은 버튼(변경하기, 유지하기) */
@Composable
fun InlineAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    val colors = RouteAlarmTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (emphasized) colors.accentText else colors.textSecondary,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Preview(showBackground = true)
@Composable
private fun ControlsPreview() {
    RouteAlarmTheme {
        var on by remember { mutableStateOf(true) }
        var tab by remember { mutableIntStateOf(0) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSwitch(checked = on, onCheckedChange = { on = it })
            SegmentedControl(listOf("개인", "단체 (학교, 기업)"), tab, { tab = it })
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("월", "화", "수").forEachIndexed { i, d -> DayChip(d, i != 2, {}, Modifier.width(38.dp)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoicePill("평일", true, {})
                ChoicePill("매일", false, {})
                RadioDot(true)
                RadioDot(false)
            }
        }
    }
}
