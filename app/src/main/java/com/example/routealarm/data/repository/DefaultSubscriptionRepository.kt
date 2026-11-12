package com.example.routealarm.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.mapper.enumValueOrDefault
import com.example.routealarm.data.remote.GroupPassNotFoundException
import com.example.routealarm.data.remote.SubscriptionRemoteDataSource
import com.example.routealarm.data.remote.dto.GroupPassDto
import com.example.routealarm.domain.model.Entitlement
import com.example.routealarm.domain.model.GroupPass
import com.example.routealarm.domain.model.PlanType
import com.example.routealarm.domain.model.SubscriptionProduct
import com.example.routealarm.domain.repository.SubscriptionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 서버가 검증해 준 구독 상태를 DataStore 에 보관한다.
 *
 * - 네트워크 오류로 검증하지 못하면 저장된 값을 건드리지 않는다 → 마지막으로 확인된 상태가 유지된다.
 * - 카드 정보나 영수증 원문은 저장하지 않는다. 플랜 종류와 기간만 남긴다(SER-001).
 */
@Singleton
class DefaultSubscriptionRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val remote: SubscriptionRemoteDataSource,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SubscriptionRepository {

    private object Keys {
        val PLAN = stringPreferencesKey("entitlement_plan")
        val STARTED_AT = longPreferencesKey("entitlement_started_at")
        val TRIAL_ENDS_AT = longPreferencesKey("entitlement_trial_ends_at")
        val EXPIRES_AT = longPreferencesKey("entitlement_expires_at")
        val AUTO_RENEW = booleanPreferencesKey("entitlement_auto_renew")
        val ORGANIZATION = stringPreferencesKey("entitlement_organization")
        val VERIFIED_AT = longPreferencesKey("entitlement_verified_at")
        val ALL = listOf(PLAN, STARTED_AT, TRIAL_ENDS_AT, EXPIRES_AT, AUTO_RENEW, ORGANIZATION, VERIFIED_AT)
    }

    override val isSimulated: Boolean get() = remote.isSimulated

    override val entitlement: Flow<Entitlement> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toEntitlement() }

    override suspend fun current(): Entitlement = entitlement.first()

    override suspend fun startTrial(product: SubscriptionProduct): AppResult<Entitlement> = remoteCall {
        val status = remote.startSubscription(product.productId)
        val now = timeProvider.now()
        Entitlement(
            plan = if (product == SubscriptionProduct.YEARLY) PlanType.PRO_YEARLY else PlanType.PRO_MONTHLY,
            startedAt = parseInstant(status.startedAt) ?: now,
            trialEndsAt = status.trialEndsAt?.let(::parseInstant),
            expiresAt = parseInstant(status.expiresAt),
            autoRenew = status.autoRenew,
            lastVerifiedAt = now,
        ).also { save(it) }
    }

    override suspend fun verifyGroupPass(code: String): AppResult<GroupPass> = remoteCall {
        remote.verifyGroupPass(code).toDomainOrThrow()
    }

    override suspend fun redeemGroupPass(code: String): AppResult<Entitlement> = remoteCall {
        // 사용 처리 전에 한 번 더 확인한다. 만료·정원 초과 이용권은 서버에 사용 요청을 보내지 않는다.
        remote.verifyGroupPass(code).toDomainOrThrow()
        val pass = remote.redeemGroupPass(code).toDomain() ?: throw GroupPassRejected(AppError.GroupPassInvalid)
        val now = timeProvider.now()
        Entitlement(
            plan = PlanType.GROUP_PASS,
            startedAt = now,
            // 이용 기간의 마지막 날 자정까지 사용할 수 있다.
            expiresAt = pass.validUntil.plusDays(1).atStartOfDay(timeProvider.zone()).toInstant(),
            autoRenew = false,
            organization = pass.organization,
            lastVerifiedAt = now,
        ).also { save(it) }
    }

    override suspend fun clear() {
        withContext(ioDispatcher) { dataStore.edit { prefs -> Keys.ALL.forEach { prefs.remove(it) } } }
    }

    private suspend fun save(entitlement: Entitlement) {
        dataStore.edit { prefs ->
            Keys.ALL.forEach { prefs.remove(it) }
            prefs[Keys.PLAN] = entitlement.plan.name
            prefs[Keys.AUTO_RENEW] = entitlement.autoRenew
            entitlement.startedAt?.let { prefs[Keys.STARTED_AT] = it.toEpochMilli() }
            entitlement.trialEndsAt?.let { prefs[Keys.TRIAL_ENDS_AT] = it.toEpochMilli() }
            entitlement.expiresAt?.let { prefs[Keys.EXPIRES_AT] = it.toEpochMilli() }
            entitlement.organization?.let { prefs[Keys.ORGANIZATION] = it }
            entitlement.lastVerifiedAt?.let { prefs[Keys.VERIFIED_AT] = it.toEpochMilli() }
        }
    }

    private fun Preferences.toEntitlement() = Entitlement(
        plan = enumValueOrDefault(this[Keys.PLAN] ?: "", PlanType.FREE),
        startedAt = this[Keys.STARTED_AT]?.let(Instant::ofEpochMilli),
        trialEndsAt = this[Keys.TRIAL_ENDS_AT]?.let(Instant::ofEpochMilli),
        expiresAt = this[Keys.EXPIRES_AT]?.let(Instant::ofEpochMilli),
        autoRenew = this[Keys.AUTO_RENEW] ?: false,
        organization = this[Keys.ORGANIZATION],
        lastVerifiedAt = this[Keys.VERIFIED_AT]?.let(Instant::ofEpochMilli),
    )

    /** 형식이 잘못됐거나 이미 쓸 수 없는 이용권은 화면에 보여줄 오류로 바꾼다. */
    private fun GroupPassDto.toDomainOrThrow(): GroupPass {
        val pass = toDomain() ?: throw GroupPassRejected(AppError.GroupPassInvalid)
        val today = timeProvider.nowZoned().toLocalDate()
        return when {
            pass.validUntil.isBefore(today) -> throw GroupPassRejected(AppError.GroupPassExpired)
            pass.remainingSeats <= 0 -> throw GroupPassRejected(AppError.GroupPassFull)
            else -> pass
        }
    }

    private fun GroupPassDto.toDomain(): GroupPass? {
        val from = parseDate(validFrom) ?: return null
        val until = parseDate(validUntil) ?: return null
        return GroupPass(code, organization, from, until, remainingSeats)
    }

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        null
    }

    private class GroupPassRejected(val error: AppError) : Exception()

    private suspend fun <T> remoteCall(block: suspend () -> T): AppResult<T> = withContext(ioDispatcher) {
        try {
            AppResult.Success(block())
        } catch (e: GroupPassRejected) {
            AppResult.Failure(e.error)
        } catch (e: GroupPassNotFoundException) {
            AppResult.Failure(AppError.GroupPassInvalid)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server(e.code()))
        }
    }
}
