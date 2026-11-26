package com.example.routealarm.presentation.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.routealarm.domain.repository.PreferencesRepository
import com.example.routealarm.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 일정·알람 계산이 바뀌면 위젯을 다시 그린다.
 * 알람 재계산은 앱 프로세스 안(Worker·알람 수신 포함)에서만 일어나므로, 앱 시작 시 한 번 구독하면 된다.
 */
object WidgetUpdater {
    @OptIn(FlowPreview::class)
    fun start(context: Context, scope: CoroutineScope, scheduleRepository: ScheduleRepository, preferencesRepository: PreferencesRepository) {
        // 위젯 배경을 바꾸면 바로 다시 그린다.
        scope.launch {
            preferencesRepository.preferences
                .map { it.widgetTheme }
                .distinctUntilChanged()
                .collect { runCatching { NextAlarmWidget().updateAll(context) } }
        }
        scope.launch {
            scheduleRepository.observeSchedules()
                .map { list -> list.map { Triple(it.id, it.enabled, it.plan?.let { p -> p.wakeUp to p.delayMinutes }) to it.title } }
                .distinctUntilChanged()
                .debounce(DEBOUNCE_MILLIS)
                .collect { runCatching { NextAlarmWidget().updateAll(context) } }
        }
    }

    private const val DEBOUNCE_MILLIS = 500L
}
