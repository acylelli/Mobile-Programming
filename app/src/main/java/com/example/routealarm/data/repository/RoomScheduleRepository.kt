package com.example.routealarm.data.repository

import android.database.sqlite.SQLiteException
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.data.local.db.ScheduleDao
import com.example.routealarm.data.mapper.ScheduleEntityMapper
import com.example.routealarm.domain.model.Schedule
import com.example.routealarm.domain.repository.ScheduleRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomScheduleRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val mapper: ScheduleEntityMapper,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScheduleRepository {

    override fun observeSchedules(): Flow<List<Schedule>> = dao.observeAll().map { list -> list.map(mapper::toDomain) }

    override fun observeSchedule(id: Long): Flow<Schedule?> = dao.observe(id).map { it?.let(mapper::toDomain) }

    override suspend fun getSchedule(id: Long): Schedule? = withContext(ioDispatcher) {
        dao.get(id)?.let(mapper::toDomain)
    }

    override suspend fun getEnabledSchedules(): List<Schedule> = withContext(ioDispatcher) {
        dao.getEnabled().map(mapper::toDomain)
    }

    override suspend fun upsert(schedule: Schedule): AppResult<Long> = dbCall {
        val rowId = dao.upsert(mapper.toEntity(schedule))
        // @Upsert 는 update 일 때 -1 을 반환한다.
        if (rowId > 0) rowId else schedule.id
    }

    override suspend fun delete(id: Long): AppResult<Unit> = dbCall { dao.delete(id) }

    override suspend fun deleteDemoSchedules(): AppResult<Unit> = dbCall { dao.deleteDemo() }

    private suspend fun <T> dbCall(block: suspend () -> T): AppResult<T> = withContext(ioDispatcher) {
        try {
            AppResult.Success(block())
        } catch (e: SQLiteException) {
            AppResult.Failure(AppError.Database)
        }
    }
}
