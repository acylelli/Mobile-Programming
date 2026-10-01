package com.example.routealarm.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.routealarm.data.local.datastore.DataStorePreferencesRepository
import com.example.routealarm.data.local.db.RouteAlarmDatabase
import com.example.routealarm.data.location.FusedLocationRepository
import com.example.routealarm.data.mapper.ScheduleEntityMapper
import com.example.routealarm.data.repository.DefaultPlaceRepository
import com.example.routealarm.data.repository.DefaultTransitRepository
import com.example.routealarm.data.repository.DemoDataProviderImpl
import com.example.routealarm.data.repository.RoomAlarmHistoryRepository
import com.example.routealarm.data.repository.RoomMetricsRepository
import com.example.routealarm.data.repository.RoomScheduleRepository
import com.example.routealarm.domain.repository.AlarmHistoryRepository
import com.example.routealarm.domain.repository.DemoDataProvider
import com.example.routealarm.domain.repository.LocationRepository
import com.example.routealarm.domain.repository.MetricsRepository
import com.example.routealarm.domain.repository.PlaceRepository
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.domain.repository.TransitRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindTransitRepository(impl: DefaultTransitRepository): TransitRepository
    @Binds abstract fun bindScheduleRepository(impl: RoomScheduleRepository): ScheduleRepository
    @Binds abstract fun bindPlaceRepository(impl: DefaultPlaceRepository): PlaceRepository
    @Binds abstract fun bindPreferencesRepository(impl: DataStorePreferencesRepository): PreferencesRepository
    @Binds abstract fun bindAlarmHistoryRepository(impl: RoomAlarmHistoryRepository): AlarmHistoryRepository
    @Binds abstract fun bindMetricsRepository(impl: RoomMetricsRepository): MetricsRepository
    @Binds abstract fun bindLocationRepository(impl: FusedLocationRepository): LocationRepository
    @Binds abstract fun bindDemoDataProvider(impl: DemoDataProviderImpl): DemoDataProvider
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): RouteAlarmDatabase =
        Room.databaseBuilder(context, RouteAlarmDatabase::class.java, RouteAlarmDatabase.NAME)
            // 포트폴리오 범위에서는 스키마 변경 시 재설치가 허용되지만, 실제 서비스라면 Migration 을 작성한다.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides fun providePlaceDao(db: RouteAlarmDatabase) = db.placeDao()
    @Provides fun provideScheduleDao(db: RouteAlarmDatabase) = db.scheduleDao()
    @Provides fun provideRouteCacheDao(db: RouteAlarmDatabase) = db.routeCacheDao()
    @Provides fun provideAlarmHistoryDao(db: RouteAlarmDatabase) = db.alarmHistoryDao()
    @Provides fun provideMetricDao(db: RouteAlarmDatabase) = db.metricDao()

    @Provides
    @Singleton
    fun provideScheduleEntityMapper(json: Json) = ScheduleEntityMapper(json)

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.preferencesDataStore
}
