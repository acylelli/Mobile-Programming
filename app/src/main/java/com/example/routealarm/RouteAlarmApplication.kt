package com.example.routealarm

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.routealarm.alarm.notification.AlarmNotifications
import com.example.routealarm.core.common.ApplicationScope
import com.example.routealarm.domain.repository.ScheduleRepository
import com.example.routealarm.presentation.schedule.detail.KakaoMapSupport
import com.example.routealarm.presentation.widget.WidgetUpdater
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@HiltAndroidApp
class RouteAlarmApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var scheduleRepository: ScheduleRepository
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    /** Worker 에 Hilt 주입을 하기 위해 WorkManager 를 수동 초기화한다(Manifest 에서 기본 초기화 제거). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        KakaoMapSupport.init(this)
        WidgetUpdater.start(this, appScope, scheduleRepository)
    }
}
