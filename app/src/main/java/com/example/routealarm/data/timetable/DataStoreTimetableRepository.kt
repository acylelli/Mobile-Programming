package com.example.routealarm.data.timetable

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.domain.model.TimetableClass
import com.example.routealarm.domain.repository.TimetableRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 시간표는 DataStore 에 작은 JSON 목록으로 둔다.
 * 설계 이유: 한 학기 수업은 많아야 수십 개라 Room 테이블·마이그레이션이 필요 없고, 알람은 시간표에서 만든 일정(Room)으로 동작한다.
 */
@Singleton
class DataStoreTimetableRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TimetableRepository {

    override val classes: Flow<List<TimetableClass>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decode(prefs[KEY]).sortedWith(compareBy({ it.day }, { it.start })) }

    override suspend fun upsert(item: TimetableClass) = update { list -> list.filterNot { it.id == item.id } + item }

    override suspend fun addAll(items: List<TimetableClass>) = update { list -> list + items }

    override suspend fun replaceAll(items: List<TimetableClass>) = update { items }

    override suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

    override val alarmScheduleIds: Flow<List<Long>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[ALARM_IDS_KEY]?.split(',')?.mapNotNull { it.toLongOrNull() }.orEmpty() }

    override suspend fun setAlarmScheduleIds(ids: List<Long>) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs -> prefs[ALARM_IDS_KEY] = ids.joinToString(",") }
        }
    }

    private suspend fun update(transform: (List<TimetableClass>) -> List<TimetableClass>) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs -> prefs[KEY] = encode(transform(decode(prefs[KEY]))) }
        }
    }

    private fun decode(raw: String?): List<TimetableClass> =
        raw?.let { runCatching { json.decodeFromString(SERIALIZER, it) }.getOrNull() }.orEmpty().mapNotNull { it.toDomain() }

    private fun encode(list: List<TimetableClass>): String = json.encodeToString(SERIALIZER, list.map { it.toStored() })

    @Serializable
    private data class StoredClass(
        val id: String,
        val name: String,
        val day: String,
        val start: String,
        val end: String? = null,
        val room: String? = null,
    )

    private fun StoredClass.toDomain(): TimetableClass? = runCatching {
        TimetableClass(id, name, DayOfWeek.valueOf(day), LocalTime.parse(start), end?.let(LocalTime::parse), room)
    }.getOrNull()

    private fun TimetableClass.toStored() = StoredClass(id, name, day.name, start.toString(), end?.toString(), room)

    private companion object {
        val KEY = stringPreferencesKey("timetable_classes")
        val ALARM_IDS_KEY = stringPreferencesKey("timetable_alarm_schedule_ids")
        val SERIALIZER = ListSerializer(StoredClass.serializer())
    }
}
