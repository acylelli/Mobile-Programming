package com.example.routealarm.di

import com.example.routealarm.BuildConfig
import com.example.routealarm.data.remote.PaymentRemoteDataSource
import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.TossTestPaymentRemoteDataSource
import com.example.routealarm.data.remote.SubscriptionRemoteDataSource
import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.data.remote.proxy.ProxyPlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.proxy.ProxySubscriptionRemoteDataSource
import com.example.routealarm.data.remote.proxy.ProxyTransitRemoteDataSource
import com.example.routealarm.data.remote.proxy.RouteAlarmProxyApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * 네트워크 설정. 교통·장소·결제·단체 이용권 모두 Backend Proxy(routealarm.proxyBaseUrl)를 호출한다.
 * 앱 어디에도 외부 API Secret 은 없다. Proxy 가 키를 보관하고 응답을 앱 계약 형식으로 바꿔 준다.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TIMEOUT_SECONDS = 15L

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                // 좌표/주소가 쿼리에 포함되므로 BODY 가 아닌 BASIC 레벨만 사용한다(개인정보 로그 최소화).
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun provideProxyApi(client: OkHttpClient, json: Json): RouteAlarmProxyApi = Retrofit.Builder()
        .baseUrl(BuildConfig.PROXY_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(RouteAlarmProxyApi::class.java)

    @Provides
    @Singleton
    fun provideTransitRemoteDataSource(proxy: ProxyTransitRemoteDataSource): TransitRemoteDataSource = proxy

    @Provides
    @Singleton
    fun providePlaceSearchRemoteDataSource(proxy: ProxyPlaceSearchRemoteDataSource): PlaceSearchRemoteDataSource = proxy

    /** 단체 이용권 확인·사용 */
    @Provides
    @Singleton
    fun provideSubscriptionRemoteDataSource(proxy: ProxySubscriptionRemoteDataSource): SubscriptionRemoteDataSource = proxy

    /** PRO 기간제 이용권 결제(토스페이먼츠 테스트 결제창, 실제 청구 없음) */
    @Provides
    @Singleton
    fun providePaymentRemoteDataSource(toss: TossTestPaymentRemoteDataSource): PaymentRemoteDataSource = toss
}
