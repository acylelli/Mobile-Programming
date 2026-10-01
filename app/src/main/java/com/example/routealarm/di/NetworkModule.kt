package com.example.routealarm.di

import com.example.routealarm.BuildConfig
import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.TransitRemoteDataSource
import com.example.routealarm.data.remote.fake.FakePlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.fake.FakeTransitRemoteDataSource
import com.example.routealarm.data.remote.proxy.ProxyPlaceSearchRemoteDataSource
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
 * 교통 데이터 제공자 선택 지점.
 *
 * local.properties 의 routealarm.transitProvider 가
 *  - "fake"  : FakeTransitRemoteDataSource (API Key 불필요, 기본값)
 *  - "proxy" : Backend Proxy (routealarm.proxyBaseUrl) 호출
 *
 * 앱 어디에도 교통 API Secret 은 없다. 제공자 변경은 이 모듈 밖의 코드에 영향을 주지 않는다.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val PROVIDER_PROXY = "proxy"
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
    fun provideTransitRemoteDataSource(
        fake: FakeTransitRemoteDataSource,
        proxy: ProxyTransitRemoteDataSource,
    ): TransitRemoteDataSource = if (BuildConfig.TRANSIT_PROVIDER == PROVIDER_PROXY) proxy else fake

    @Provides
    @Singleton
    fun providePlaceSearchRemoteDataSource(
        fake: FakePlaceSearchRemoteDataSource,
        proxy: ProxyPlaceSearchRemoteDataSource,
    ): PlaceSearchRemoteDataSource = if (BuildConfig.TRANSIT_PROVIDER == PROVIDER_PROXY) proxy else fake
}
