package com.example.routealarm.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.core.util.keepWords

/** 탭 화면(홈·알람·설정)의 큰 제목 줄. 오른쪽에는 교통 상태 칩이나 + 버튼이 온다. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.screen, end = Spacing.screen, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            color = RouteAlarmTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** 하위 화면의 상단 줄: 뒤로 가기 + 제목. 제목이 없으면(새 일정 단계) 오른쪽 슬롯만 쓴다. */
@Composable
fun BackHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            // 뒤로 버튼의 터치 영역(48dp)을 유지하면서 아이콘을 화면 여백에 맞춘다.
            .padding(start = Spacing.screen - 14.dp, end = Spacing.screen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.ArrowBackIosNew,
                contentDescription = stringResource(R.string.cd_back),
                tint = RouteAlarmTheme.colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
        if (title != null) {
            Spacer(Modifier.width(2.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = RouteAlarmTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (trailing != null) Box(contentAlignment = Alignment.CenterEnd) { trailing() }
    }
}

/** 묶음 위의 작은 회색 제목(즐겨찾기, 알람 기본값, 컬러 테마) */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = RouteAlarmTheme.colors.textSecondary,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * 여러 줄로 흐르는 설명 문단. 한국어 낱말이 줄 끝에서 쪼개지지 않게 낱말 단위로 줄을 바꾼다.
 * 화면 낭독기는 줄바꿈용 문자가 섞이지 않은 원문을 읽는다.
 */
@Composable
fun Paragraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = RouteAlarmTheme.colors.textSecondary,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = remember(text) { text.keepWords() },
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = text },
    )
}
