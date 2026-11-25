package com.example.routealarm.data.habit

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import com.example.routealarm.domain.repository.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** 일정마다 기상 습관 제안을 적용하거나 넘긴 시각을 DataStore 에 둔다(일정 하나에 숫자 하나). */
@Singleton
class DataStoreHabitRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : HabitRepository {

    override fun resetAt(scheduleId: Long): Flow<Instant?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[key(scheduleId)]?.let(Instant::ofEpochMilli) }
        .distinctUntilChanged()

    override suspend fun reset(scheduleId: Long, at: Instant) {
        dataStore.edit { prefs -> prefs[key(scheduleId)] = at.toEpochMilli() }
    }

    private fun key(scheduleId: Long) = longPreferencesKey("habit_reset_at_$scheduleId")
}
