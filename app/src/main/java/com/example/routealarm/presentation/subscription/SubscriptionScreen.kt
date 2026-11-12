package com.example.routealarm.presentation.subscription

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.routealarm.R
import com.example.routealarm.core.designsystem.component.AppCard
import com.example.routealarm.core.designsystem.component.BackHeader
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.component.ProAccentBadge
import com.example.routealarm.core.designsystem.component.SecondaryButton
import com.example.routealarm.core.designsystem.component.SectionLabel
import com.example.routealarm.core.designsystem.component.SurfaceCard
import com.example.routealarm.core.designsystem.component.TagBadge
import com.example.routealarm.core.designsystem.component.TextLink
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.core.designsystem.theme.Spacing
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.PlanLimits
import com.example.routealarm.domain.model.PlanType
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.wonText
import com.example.routealarm.presentation.navigation.LocalAppSnackbar
import com.example.routealarm.presentation.settings.planName
import kotlinx.coroutines.launch

@Composable
fun SubscriptionRoute(
    onBack: () -> Unit,
    onShowPaywall: () -> Unit,
    onOpenGroupPass: () -> Unit,
    viewModel: SubscriptionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalAppSnackbar.current
    val scope = rememberCoroutineScope()
    val playUnavailable = stringResource(R.string.subscription_play_unavailable)
    SubscriptionScreen(
        state = state,
        onBack = onBack,
        onShowPaywall = onShowPaywall,
        onOpenGroupPass = onOpenGroupPass,
        onManageOnPlay = { if (!context.openPlaySubscriptions()) scope.launch { snackbar.showSnackbar(playUnavailable) } },
        onResetToFree = viewModel::resetToFree,
    )
}

/**
 * 구독 관리 — UI-F-003.
 * 현재 플랜과 결제 내역을 보여주고, 해지는 Google Play 구독 관리 화면으로 연결한다.
 */
@Composable
fun SubscriptionScreen(
    state: SubscriptionUiState,
    onBack: () -> Unit,
    onShowPaywall: () -> Unit,
    onOpenGroupPass: () -> Unit,
    onManageOnPlay: () -> Unit,
    onResetToFree: () -> Unit,
) {
    val colors = RouteAlarmTheme.colors
    val entitlement = state.entitlement
    Column(Modifier.fillMaxSize().background(colors.background)) {
        BackHeader(onBack = onBack, title = stringResource(R.string.settings_subscription))
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.subscription_current_plan))
            Spacer(Modifier.height(10.dp))
            CurrentPlanCard(state)

            if (state.isPro) {
                Spacer(Modifier.height(Spacing.lg))
                SectionLabel(stringResource(R.string.subscription_details))
                Spacer(Modifier.height(10.dp))
                SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        entitlement.organization?.let { DetailRow(stringResource(R.string.group_pass_organization), it) }
                        entitlement.startedAt?.let { DetailRow(stringResource(R.string.subscription_started), TimeFormat.dottedDate(it)) }
                        if (entitlement.isPersonalSubscription) {
                            entitlement.expiresAt?.let {
                                DetailRow(
                                    stringResource(if (state.isInTrial) R.string.subscription_first_payment else R.string.subscription_next_payment),
                                    TimeFormat.dottedDate(it),
                                )
                            }
                            DetailRow(stringResource(R.string.subscription_price), priceText(entitlement))
                            DetailRow(
                                stringResource(R.string.subscription_auto_renew),
                                stringResource(if (entitlement.autoRenew) R.string.subscription_auto_renew_on else R.string.subscription_auto_renew_off),
                            )
                        } else {
                            entitlement.expiresAt?.let {
                                // 이용 종료 시각은 마지막 날의 자정이므로 하루 전 날짜를 보여준다.
                                DetailRow(stringResource(R.string.subscription_valid_until), TimeFormat.dottedDate(it.minusSeconds(1)))
                            }
                        }
                    }
                }
            }

            // 결제 내역
            Spacer(Modifier.height(Spacing.lg))
            SectionLabel(stringResource(R.string.subscription_history))
            Spacer(Modifier.height(10.dp))
            PaymentHistory(state)

            Spacer(Modifier.height(Spacing.lg))
            if (!state.isPro) {
                SecondaryButton(stringResource(R.string.settings_group_pass), onClick = onOpenGroupPass, modifier = Modifier.fillMaxWidth())
            } else if (entitlement.isPersonalSubscription) {
                // 해지는 Google Play 구독 관리에서 한다. 결제 기간이 끝나면 무료 플랜으로 바뀌고 일정은 그대로 남는다.
                SecondaryButton(stringResource(R.string.subscription_manage_on_play), onClick = onManageOnPlay, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Paragraph(stringResource(R.string.subscription_cancel_notice), style = MaterialTheme.typography.bodySmall)
            }
            if (state.isSimulated && state.isPro) {
                Spacer(Modifier.height(8.dp))
                TextLink(stringResource(R.string.subscription_reset_demo), onClick = onResetToFree, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(Spacing.lg))
        }
        if (!state.isPro) {
            PrimaryButton(
                text = stringResource(R.string.paywall_start_trial, SubscriptionProduct.TRIAL_DAYS.toInt()),
                onClick = onShowPaywall,
                modifier = Modifier.fillMaxWidth().padding(start = Spacing.screen, end = Spacing.screen, top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun CurrentPlanCard(state: SubscriptionUiState) {
    val colors = RouteAlarmTheme.colors
    val entitlement = state.entitlement
    AppCard(Modifier.fillMaxWidth(), selected = state.isPro) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    planName(entitlement, state.isPro),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.width(10.dp))
                when {
                    state.isInTrial -> TagBadge(stringResource(R.string.subscription_trial_badge), containerColor = colors.accent, contentColor = colors.onAccent)
                    state.isPro -> ProAccentBadge()
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    !state.isPro -> stringResource(R.string.subscription_free_body, PlanLimits.FREE_SCHEDULE_LIMIT)
                    entitlement.plan == PlanType.GROUP_PASS -> stringResource(R.string.subscription_group_body, entitlement.organization.orEmpty())
                    else -> stringResource(R.string.subscription_pro_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun PaymentHistory(state: SubscriptionUiState) {
    val colors = RouteAlarmTheme.colors
    val entitlement = state.entitlement
    val startedAt = entitlement.startedAt
    if (!state.isPro || startedAt == null) {
        Text(stringResource(R.string.subscription_history_empty), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        return
    }
    SurfaceCard(Modifier.fillMaxWidth(), shape = Radius.group) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (entitlement.plan == PlanType.GROUP_PASS) {
                HistoryRow(TimeFormat.dottedDate(startedAt), stringResource(R.string.subscription_history_group), stringResource(R.string.subscription_history_paid_by_group))
            } else {
                HistoryRow(TimeFormat.dottedDate(startedAt), stringResource(R.string.subscription_history_trial), wonText(0))
                entitlement.expiresAt?.takeIf { entitlement.autoRenew }?.let { next ->
                    HorizontalDivider(thickness = 1.dp, color = colors.outline)
                    HistoryRow(TimeFormat.dottedDate(next), stringResource(R.string.subscription_history_scheduled), priceText(entitlement), muted = true)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(date: String, title: String, amount: String, muted: Boolean = false) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (muted) colors.textSecondary else colors.textPrimary)
            Text(date, style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
        }
        Text(amount, style = MaterialTheme.typography.titleSmall, color = if (muted) colors.textSecondary else colors.textPrimary)
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = RouteAlarmTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun priceText(entitlement: Entitlement): String = when (entitlement.plan) {
    PlanType.PRO_YEARLY -> stringResource(R.string.subscription_price_yearly, TimeFormat.amount(SubscriptionProduct.YEARLY.priceWon))
    PlanType.PRO_MONTHLY -> stringResource(R.string.subscription_price_monthly, TimeFormat.amount(SubscriptionProduct.MONTHLY.priceWon))
    else -> wonText(0)
}

/**
 * Google Play 의 구독 관리 화면을 연다. 해지·결제 수단 변경은 앱이 아니라 Play 에서 처리한다(SER-001).
 * @return 열 수 있는 앱이 없으면 false
 */
internal fun Context.openPlaySubscriptions(): Boolean {
    val uri = "https://play.google.com/store/account/subscriptions?package=$packageName".toUri()
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
