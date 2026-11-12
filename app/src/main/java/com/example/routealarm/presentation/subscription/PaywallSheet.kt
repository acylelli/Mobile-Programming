package com.example.routealarm.presentation.subscription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppBottomSheet
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.RadioDot
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.SegmentedControl
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.TagBadge
import com.example.routealarm.core.designsystem.theme.AppType
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.PlanLimits
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.messageRes
import com.example.routealarm.presentation.common.wonText

private const val TAB_PERSONAL = 0
private const val TAB_GROUP = 1

/**
 * PRO 페이월 — UI-E-001.
 * 잠긴 기능을 눌렀을 때만 뜨는 바텀시트다. 스스로 뜨지 않는다(SFR-015 PRO 안내 원칙).
 */
@Composable
fun PaywallSheet(
    onDismiss: () -> Unit,
    onOpenGroupPass: () -> Unit,
    onSubscribed: () -> Unit,
    viewModel: PaywallViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(TAB_PERSONAL) }

    LaunchedEffect(viewModel) {
        viewModel.reset()
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Subscribed -> onSubscribed()
            }
        }
    }

    AppBottomSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium,
                color = RouteAlarmTheme.colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(14.dp))
            // 개인/단체 탭(①)
            SegmentedControl(
                options = listOf(stringResource(R.string.paywall_tab_personal), stringResource(R.string.paywall_tab_group)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(14.dp))
            if (tab == TAB_PERSONAL) {
                PersonalPlans(state, onSelect = viewModel::select, onStart = viewModel::startTrial, onContinueFree = onDismiss)
            } else {
                GroupPlans(onOpenGroupPass = onOpenGroupPass)
            }
        }
    }
}

@Composable
private fun PersonalPlans(
    state: PaywallUiState,
    onSelect: (PaywallOption) -> Unit,
    onStart: () -> Unit,
    onContinueFree: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    // 요금제 선택(②)
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PlanCard(
            title = stringResource(R.string.paywall_yearly),
            body = stringResource(
                R.string.paywall_yearly_body,
                TimeFormat.amount(PlanLimits.YEARLY_MONTHLY_EQUIVALENT_WON),
                PlanLimits.YEARLY_DISCOUNT_PERCENT,
            ),
            price = wonText(SubscriptionProduct.YEARLY.priceWon),
            recommended = true,
            selected = state.option == PaywallOption.YEARLY,
            onClick = { onSelect(PaywallOption.YEARLY) },
        )
        PlanCard(
            title = stringResource(R.string.paywall_monthly),
            body = stringResource(R.string.paywall_monthly_body),
            price = wonText(SubscriptionProduct.MONTHLY.priceWon),
            selected = state.option == PaywallOption.MONTHLY,
            onClick = { onSelect(PaywallOption.MONTHLY) },
        )
        PlanCard(
            title = stringResource(R.string.paywall_free),
            body = stringResource(R.string.paywall_free_body, PlanLimits.FREE_SCHEDULE_LIMIT),
            price = wonText(0),
            selected = state.option == PaywallOption.FREE,
            onClick = { onSelect(PaywallOption.FREE) },
        )
    }

    // PRO 혜택(③)
    Spacer(Modifier.height(14.dp))
    val benefits = listOf(
        R.string.paywall_benefit_realtime,
        R.string.paywall_benefit_unlimited,
        R.string.paywall_benefit_integration,
        R.string.paywall_benefit_no_ads,
        R.string.paywall_benefit_taxi,
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        benefits.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { res -> Benefit(stringResource(res), Modifier.weight(1f)) }
            }
        }
    }

    state.error?.let { error ->
        Spacer(Modifier.height(10.dp))
        Text(stringResource(error.messageRes()), style = MaterialTheme.typography.bodySmall, color = colors.danger)
    }

    // 무료체험 시작(④)
    Spacer(Modifier.height(14.dp))
    val isFree = state.option == PaywallOption.FREE
    PrimaryButton(
        text = stringResource(if (isFree) R.string.paywall_continue_free else R.string.paywall_start_trial, SubscriptionProduct.TRIAL_DAYS.toInt()),
        onClick = if (isFree) onContinueFree else onStart,
        loading = state.isProcessing,
        modifier = Modifier.fillMaxWidth(),
    )
    // 결제 안내(⑤): 체험 종료 알림, 이후 결제 금액, 해지 가능 여부를 분명하게
    Spacer(Modifier.height(10.dp))
    Text(
        when (state.option) {
            PaywallOption.YEARLY -> stringResource(R.string.paywall_footnote_yearly, TimeFormat.amount(SubscriptionProduct.YEARLY.priceWon))
            PaywallOption.MONTHLY -> stringResource(R.string.paywall_footnote_monthly, TimeFormat.amount(SubscriptionProduct.MONTHLY.priceWon))
            PaywallOption.FREE -> stringResource(R.string.paywall_footnote_free)
        },
        style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.1f),
        color = colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.isSimulated) {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.paywall_simulated_notice),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize * 1.1f),
            color = colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    body: String,
    price: String,
    selected: Boolean,
    onClick: () -> Unit,
    recommended: Boolean = false,
) {
    val colors = RouteAlarmTheme.colors
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, selected = selected, shape = Radius.group) {
        Row(Modifier.padding(start = 16.dp, end = 18.dp, top = 13.dp, bottom = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioDot(selected)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    if (recommended) {
                        Spacer(Modifier.width(8.dp))
                        TagBadge(stringResource(R.string.paywall_recommended), containerColor = colors.accent, contentColor = colors.onAccent)
                    }
                }
                Text(body, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(price, style = AppType.Figure, color = colors.textPrimary)
        }
    }
}

@Composable
private fun Benefit(text: String, modifier: Modifier = Modifier) {
    val colors = RouteAlarmTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 단체 탭(①): 학교·기업 도입 문의와 단체 이용권 코드 입력으로 연결한다. */
@Composable
private fun GroupPlans(onOpenGroupPass: () -> Unit) {
    val colors = RouteAlarmTheme.colors
    val context = LocalContext.current
    val inquirySubject = stringResource(R.string.paywall_group_inquiry_subject)

    Paragraph(stringResource(R.string.paywall_group_body), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(14.dp))
    SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Benefit(stringResource(R.string.paywall_group_point_all))
            Benefit(stringResource(R.string.paywall_group_point_bulk))
            Benefit(stringResource(R.string.paywall_group_point_privacy))
        }
    }
    Spacer(Modifier.height(18.dp))
    PrimaryButton(stringResource(R.string.settings_group_pass), onClick = onOpenGroupPass, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    SecondaryButton(
        stringResource(R.string.paywall_group_inquiry),
        onClick = { context.composeInquiryMail(inquirySubject) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
}

/** 도입 문의: 받는 사람은 비워 둔 채 메일 앱의 작성 화면만 연다. 메일 앱이 없으면 그 사실을 알려준다. */
private fun Context.composeInquiryMail(subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri()).putExtra(Intent.EXTRA_SUBJECT, subject)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // 시트가 Snackbar 를 가리므로 시트 위에 뜨는 Toast 로 안내한다.
        Toast.makeText(this, R.string.paywall_group_inquiry_unavailable, Toast.LENGTH_SHORT).show()
    }
}
