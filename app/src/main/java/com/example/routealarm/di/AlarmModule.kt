package com.example.routealarm.di

import com.example.routealarm.alarm.notification.AlarmNotifications
import com.example.routealarm.alarm.scheduler.AndroidAlarmScheduler
import com.example.routealarm.alarm.worker.WorkManagerRefreshScheduler
import com.example.routealarm.domain.repository.AlarmNotifier
import com.example.routealarm.domain.repository.AlarmScheduler
import com.example.routealarm.domain.repository.TransitRefreshScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AlarmModule {
    @Binds abstract fun bindAlarmScheduler(impl: AndroidAlarmScheduler): AlarmScheduler
    @Binds abstract fun bindRefreshScheduler(impl: WorkManagerRefreshScheduler): TransitRefreshScheduler
    @Binds abstract fun bindAlarmNotifier(impl: AlarmNotifications): AlarmNotifier
}
