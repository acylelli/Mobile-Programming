package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Schedule
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeSchedules(): Flow<List<Schedule>>
    fun observeSchedule(id: Long): Flow<Schedule?>
    suspend fun getSchedule(id: Long): Schedule?
    suspend fun getEnabledSchedules(): List<Schedule>
    suspend fun upsert(schedule: Schedule): AppResult<Long>
    suspend fun delete(id: Long): AppResult<Unit>
    suspend fun deleteDemoSchedules(): AppResult<Unit>
}
