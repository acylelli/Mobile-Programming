package com.example.routealarm.presentation.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.NoticeStrip
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import java.util.Locale

@Composable
fun GroupPassRoute(
    onBack: () -> Unit,
    onApplied: (organization: String) -> Unit,
    viewModel: GroupPassViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = LocalAppSnackbar.current
    val context = LocalContext.current
    // 개인 구독 중에 적용했다면 자동 갱신 해지 안내를 먼저 보여준 뒤 돌아간다(④ 중복 안내).
    var appliedWithSubscription by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupPassEvent.Applied ->
                    if (event.hadPersonalSubscription) appliedWithSubscription = event.organization else onApplied(event.organization)
                is GroupPassEvent.ShowError -> snackbar.showSnackbar(context.getString(event.error.messageRes()))
            }
        }
    }

    GroupPassScreen(state = state, code = viewModel.code, onBack = onBack, onCodeChange = viewModel::onCodeChange, onApply = viewModel::apply)

    appliedWithSubscription?.let { organization ->
        AlertDialog(
            onDismissRequest = { onApplied(organization) },
            title = { Text(stringResource(R.string.group_pass_duplicate_title)) },
            text = { Paragraph(stringResource(R.string.group_pass_duplicate_body), color = RouteAlarmTheme.colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    context.openPlaySubscriptions()
                    onApplied(organization)
                }) { Text(stringResource(R.string.subscription_manage_on_play)) }
            },
            dismissButton = { TextButton(onClick = { onApplied(organization) }) { Text(stringResource(R.string.common_later)) } },
        )
    }
}

/** 단체 이용권 코드 입력 — UI-E-002 */
@Composable
fun GroupPassScreen(
    state: GroupPassUiState,
    code: String,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onApply: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
        BackHeader(onBack = onBack, title = stringResource(R.string.group_pass_title))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.group_pass_headline),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, lineHeight = 37.sp),
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(10.dp))
            Paragraph(stringResource(R.string.group_pass_body))
            Spacer(Modifier.height(20.dp))

            // 코드 입력(①)
            CodeField(code, onCodeChange)
            if (state.isSimulated) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.group_pass_demo_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
            }

            // 확인 결과(②): 서버 검증 후 기관, 이용 기간, 적용 플랜
            Spacer(Modifier.height(16.dp))
            when (val check = state.check) {
                GroupPassCheck.Idle -> Unit
                GroupPassCheck.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.group_pass_checking), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
                is GroupPassCheck.Verified -> VerifiedCard(check.pass)
                is GroupPassCheck.Failed -> NoticeStrip(
                    text = stringResource(check.error.messageRes()),
                    icon = Icons.Outlined.ErrorOutline,
                    danger = true,
                )
            }

            // 개인정보 안내(③), 중복 안내(④)
            Spacer(Modifier.height(18.dp))
            InfoLine(Icons.Outlined.Lock, stringResource(R.string.group_pass_privacy))
            Spacer(Modifier.height(10.dp))
            InfoLine(Icons.Outlined.CreditCard, stringResource(R.string.group_pass_duplicate))
            Spacer(Modifier.height(Spacing.lg))
        }
        // 적용(⑤)
        PrimaryButton(
            text = stringResource(R.string.group_pass_apply),
            onClick = onApply,
            enabled = state.check is GroupPassCheck.Verified,
            loading = state.isApplying,
            modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp),
        )
    }
}

/**
 * 코드 입력창. 굵은 검정 테두리 안에 큰 글자로 보여주고, 입력 중인 마지막 묶음만 진하게 강조한다
 * (HSU-2026-**WAKE7**). 소문자로 입력해도 대문자로 보인다.
 */
@Composable
private fun CodeField(code: String, onCodeChange: (String) -> Unit) {
    val colors = RouteAlarmTheme.colors
    val style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    val fieldLabel = stringResource(R.string.group_pass_field_label)
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        textStyle = style.copy(color = colors.textPrimary),
        singleLine = true,
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        visualTransformation = remember(colors) { LastGroupEmphasis(colors.textTertiary) },
        modifier = Modifier
            .fillMaxWidth()
            .height(66.dp)
            .border(2.dp, colors.textPrimary, Radius.group)
            .semantics { contentDescription = fieldLabel },
        // 테두리 안쪽 전체가 입력창이 되도록 꾸밈을 입력창 안에 그린다(글자 줄만 눌러야 하는 불편을 없앤다).
        decorationBox = { innerTextField ->
            Box(Modifier.padding(horizontal = 20.dp), contentAlignment = Alignment.CenterStart) {
                if (code.isEmpty()) {
                    Text(stringResource(R.string.group_pass_placeholder), style = style, color = colors.textTertiary.copy(alpha = 0.6f), maxLines = 1)
                }
                innerTextField()
            }
        },
    )
}

/**
 * 입력한 코드를 대문자로 보여주고, 마지막 하이픈 앞부분은 옅게 칠해 지금 입력 중인 묶음이 눈에 들어오게 한다.
 * 보이는 모습만 바꾸고 글자 수는 그대로라 커서 위치가 어긋나지 않는다.
 */
private class LastGroupEmphasis(private val mutedColor: androidx.compose.ui.graphics.Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = text.text.uppercase(Locale.ROOT)
        val split = shown.lastIndexOf('-') + 1
        val styled = buildAnnotatedString {
            withStyle(SpanStyle(color = mutedColor)) { append(shown.substring(0, split)) }
            append(shown.substring(split))
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean = other is LastGroupEmphasis && other.mutedColor == mutedColor
    override fun hashCode(): Int = mutedColor.hashCode()
}

@Composable
private fun VerifiedCard(pass: GroupPass) {
    val colors = RouteAlarmTheme.colors
    SurfaceCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.group_pass_verified), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            }
            VerifiedRow(stringResource(R.string.group_pass_organization), pass.organization)
            VerifiedRow(
                stringResource(R.string.group_pass_period),
                stringResource(R.string.group_pass_period_format, TimeFormat.dottedDate(pass.validFrom), TimeFormat.dottedDate(pass.validUntil)),
            )
            VerifiedRow(stringResource(R.string.group_pass_plan), stringResource(R.string.group_pass_plan_value))
        }
    }
}

@Composable
private fun VerifiedRow(label: String, value: String) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String) {
    val colors = RouteAlarmTheme.colors
    Row {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.padding(top = 1.dp).size(17.dp))
        Spacer(Modifier.width(10.dp))
        Paragraph(text, style = MaterialTheme.typography.bodySmall)
    }
}
