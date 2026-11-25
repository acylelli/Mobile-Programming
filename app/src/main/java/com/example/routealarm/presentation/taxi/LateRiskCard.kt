package com.example.routealarm.presentation.taxi

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocalTaxi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.routealarm.R
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.designsystem.component.Paragraph
import com.example.routealarm.core.designsystem.component.PrimaryButton
import com.example.routealarm.core.designsystem.theme.Radius
import com.example.routealarm.core.designsystem.theme.RouteAlarmTheme
import com.example.routealarm.domain.model.AlarmHistoryEvent
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.model.TaxiEstimate
import com.example.routealarm.domain.policy.LateRisk
import com.example.routealarm.domain.policy.LateRiskPolicy
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.TaxiRepository
import com.example.routealarm.presentation.common.TimeFormat
import com.example.routealarm.presentation.common.rememberNow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.NumberFormat
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

sealed interface TaxiState {
    data object Loading : TaxiState
    data class Loaded(val estimate: TaxiEstimate) : TaxiState
    /** 택시 예상을 못 가져왔다. 카드는 지각 경고와 호출 버튼만 보여 준다. */
    data object Unavailable : TaxiState
}

/** 지각 위험일 때만 택시 예상을 한 번 가져온다(카카오 호출을 아끼기 위해 화면에 카드가 뜰 때만). */
@HiltViewModel
class TaxiViewModel @Inject constructor(
    private val taxiRepository: TaxiRepository,
    private val historyRepository: AlarmHistoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<TaxiState>(TaxiState.Loading)
    val state: StateFlow<TaxiState> = _state.asStateFlow()
    private var loadedFor: Pair<Place, Place>? = null

    /** 이 일정의 가장 최근 기상 알람 끈 기록(늦잠 판단 근거) */
    fun lastDismissal(scheduleId: Long): Flow<AlarmHistoryEvent?> =
        historyRepository.observeDismissals(scheduleId, limit = 1).map { it.firstOrNull() }

    fun load(origin: Place, destination: Place) {
        if (loadedFor == origin to destination) return
        loadedFor = origin to destination
        _state.value = TaxiState.Loading
        viewModelScope.launch {
            _state.value = when (val result = taxiRepository.estimate(origin, destination)) {
                is AppResult.Success -> TaxiState.Loaded(result.data)
                is AppResult.Failure -> TaxiState.Unavailable
            }
        }
    }
}

/**
 * 지각 위험 → 택시 안내. 교통이 나빠졌거나 늦게 일어나 대중교통으로는 늦을 때만 보인다([LateRiskPolicy]).
 * 택시로 가면 몇 시에 도착하는지와 예상 요금을 보여 주고, 카카오 T 를 출발지·도착지가 채워진 채로 연다.
 */
@Composable
fun LateRiskCard(schedule: Schedule, modifier: Modifier = Modifier) {
    if (!schedule.enabled) return
    val viewModel: TaxiViewModel = hiltViewModel(key = "taxi-${schedule.id}")
    val dismissalFlow = remember(schedule.id) { viewModel.lastDismissal(schedule.id) }
    val lastDismissal by dismissalFlow.collectAsStateWithLifecycle(initialValue = null)
    val now by rememberNow()
    val risk = LateRiskPolicy.assess(
        plan = schedule.plan,
        targetTime = schedule.targetArrivalTime,
        preparationMinutes = schedule.preparationMinutes,
        lastDismissal = lastDismissal,
        now = now,
        zone = ZoneId.systemDefault(),
    ) ?: return
    LaunchedEffect(schedule.origin, schedule.destination) { viewModel.load(schedule.origin, schedule.destination) }
    val taxi by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = RouteAlarmTheme.colors

    Column(
        modifier
            .fillMaxWidth()
            .background(colors.dangerContainer, Radius.field)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.LocalTaxi, contentDescription = null, tint = colors.danger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(
                    if (risk.reason == LateRisk.Reason.OVERSLEPT) R.string.late_title_overslept else R.string.late_title_traffic,
                    risk.lateMinutes,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = colors.danger,
            )
        }
        Spacer(Modifier.height(4.dp))
        Paragraph(
            stringResource(R.string.late_body, TimeFormat.time(risk.leaveAt), TimeFormat.time(risk.transitArrival), TimeFormat.time(risk.target)),
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(6.dp))
        when (val state = taxi) {
            TaxiState.Loading -> Paragraph(stringResource(R.string.late_taxi_loading), style = MaterialTheme.typography.bodySmall)
            TaxiState.Unavailable -> Paragraph(stringResource(R.string.late_taxi_unavailable), style = MaterialTheme.typography.bodySmall)
            is TaxiState.Loaded -> {
                val arrival = LateRiskPolicy.taxiArrival(risk, now, state.estimate.minutes)
                val taxiLate = java.time.Duration.between(risk.target, arrival).toMinutes()
                Text(
                    stringResource(
                        R.string.late_taxi_estimate,
                        TimeFormat.time(arrival),
                        state.estimate.minutes,
                        won(state.estimate.fareWon),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                )
                Paragraph(
                    if (taxiLate > 0) stringResource(R.string.late_taxi_still_late, taxiLate.toInt()) else stringResource(R.string.late_taxi_on_time),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton(
            text = stringResource(R.string.late_call_taxi),
            onClick = { openKakaoTaxi(context, schedule.origin, schedule.destination) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun won(amount: Int): String = NumberFormat.getNumberInstance(Locale.KOREA).format(amount)

/** 카카오 T 를 출발지·도착지가 채워진 택시 호출 화면으로 연다. 앱이 없으면 설치 페이지로 보낸다. */
private fun openKakaoTaxi(context: Context, origin: Place, destination: Place) {
    val deepLink = "kakaot://taxi/set?origin_lat=${origin.latitude}&origin_lng=${origin.longitude}" +
        "&dest_lat=${destination.latitude}&dest_lng=${destination.longitude}"
    val candidates = listOf(deepLink, "market://details?id=$KAKAO_T_PACKAGE", "https://play.google.com/store/apps/details?id=$KAKAO_T_PACKAGE")
    for (uri in candidates) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // 다음 후보로
        }
    }
}

private const val KAKAO_T_PACKAGE = "com.kakao.taxi"
