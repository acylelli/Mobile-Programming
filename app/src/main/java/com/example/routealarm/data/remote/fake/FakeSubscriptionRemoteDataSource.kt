package com.example.routealarm.data.remote.fake

import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.remote.GroupPassNotFoundException
import com.example.routealarm.data.remote.SubscriptionRemoteDataSource
import com.example.routealarm.data.remote.dto.GroupPassDto
import com.example.routealarm.data.remote.dto.SubscriptionStatusDto
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.domain.repository.PreferencesRepository
import kotlinx.coroutines.delay
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Console 상품과 검증 서버 없이 구독·단체 이용권 흐름을 시연하기 위한 가짜 결제.
 *
 * 실제 결제는 일어나지 않는다. 화면은 [isSimulated] 를 보고 "시연용" 안내를 표시한다.
 * 설정 > 데모의 "네트워크 오류 시뮬레이션"을 켜면 검증 실패 시의 동작(마지막 상태 유지)도 확인할 수 있다.
 */
@Singleton
class FakeSubscriptionRemoteDataSource @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val timeProvider: TimeProvider,
) : SubscriptionRemoteDataSource {

    override val isSimulated: Boolean = true

    override suspend fun startSubscription(productId: String): SubscriptionStatusDto {
        simulateServer(PURCHASE_LATENCY_MILLIS)
        val now = timeProvider.now()
        val trialEnd = now.plus(Duration.ofDays(SubscriptionProduct.TRIAL_DAYS))
        return SubscriptionStatusDto(
            productId = productId,
            startedAt = now.toString(),
            trialEndsAt = trialEnd.toString(),
            // 무료체험이 끝나는 시점에 첫 결제가 이루어진다.
            expiresAt = trialEnd.toString(),
            autoRenew = true,
        )
    }

    override suspend fun verifyGroupPass(code: String): GroupPassDto {
        simulateServer(VERIFY_LATENCY_MILLIS)
        return PASSES[code] ?: throw GroupPassNotFoundException(code)
    }

    override suspend fun redeemGroupPass(code: String): GroupPassDto {
        simulateServer(VERIFY_LATENCY_MILLIS)
        val pass = PASSES[code] ?: throw GroupPassNotFoundException(code)
        return pass.copy(remainingSeats = (pass.remainingSeats - 1).coerceAtLeast(0))
    }

    private suspend fun simulateServer(latencyMillis: Long) {
        delay(latencyMillis)
        if (preferencesRepository.current().demo.simulateNetworkFailure) throw IOException("Simulated network failure")
    }

    companion object {
        private const val PURCHASE_LATENCY_MILLIS = 700L
        private const val VERIFY_LATENCY_MILLIS = 450L

        /** 시연용 이용권. 첫 번째 코드는 UI 설계서(UI-E-002) 예시와 같다. */
        const val DEMO_CODE = "HSU-2026-WAKE7"

        private val PASSES = listOf(
            GroupPassDto(DEMO_CODE, "한성대학교", "2026-03-01", "2027-02-28", remainingSeats = 128),
            GroupPassDto("WAKE-2026-TEAM1", "WAKE 물류센터", "2026-01-01", "2026-12-31", remainingSeats = 12),
            // 만료·정원 초과 처리 확인용
            GroupPassDto("HSU-2025-PAST1", "한성대학교", "2025-03-01", "2026-02-28", remainingSeats = 40),
            GroupPassDto("HSU-2026-FULL0", "한성대학교", "2026-03-01", "2027-02-28", remainingSeats = 0),
        ).associateBy { it.code }
    }
}
