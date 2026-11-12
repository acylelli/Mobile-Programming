package com.example.routealarm.data.repository

import android.database.sqlite.SQLiteException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.local.db.PlaceDao
import com.example.routealarm.data.mapper.StoredRecentPlace
import com.example.routealarm.data.mapper.TransitDtoMapper
import com.example.routealarm.data.mapper.toDomain
import com.example.routealarm.data.mapper.toEntity
import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val remote: PlaceSearchRemoteDataSource,
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val timeProvider: TimeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PlaceRepository {

    override fun observeSavedPlaces(): Flow<List<Place>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getPlace(id: Long): Place? = withContext(ioDispatcher) { dao.get(id)?.toDomain() }

    override suspend fun savePlace(place: Place): AppResult<Long> = withContext(ioDispatcher) {
        try {
            val rowId = dao.upsert(place.toEntity(timeProvider.now()))
            AppResult.Success(if (rowId > 0) rowId else place.id)
        } catch (e: SQLiteException) {
            AppResult.Failure(AppError.Database)
        }
    }

    override suspend fun deletePlace(id: Long): AppResult<Unit> = withContext(ioDispatcher) {
        try {
            AppResult.Success(dao.delete(id))
        } catch (e: SQLiteException) {
            AppResult.Failure(AppError.Database)
        }
    }

    override suspend fun search(query: String): AppResult<List<Place>> = withContext(ioDispatcher) {
        try {
            AppResult.Success(remote.search(query).places.map(TransitDtoMapper::toDomain))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network)
        } catch (e: HttpException) {
            AppResult.Failure(AppError.PlaceSearchFailed)
        }
    }

    /*
     * 최근 검색은 Room 테이블이 아니라 DataStore 에 작은 JSON 목록으로 둔다.
     * 설계 이유: 최대 5건뿐이라 스키마·마이그레이션이 필요 없고, 검색 기록을 길게 쌓지 않는다는 점도 분명해진다.
     */
    override fun observeRecentSearches(): Flow<List<Place>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> decodeRecent(prefs[RECENT_SEARCHES]).map { it.toPlace() } }

    override suspend fun addRecentSearch(place: Place) {
        withContext(ioDispatcher) {
            dataStore.edit { prefs ->
                val entry = StoredRecentPlace(place.name, place.address, place.latitude, place.longitude, place.transitLines)
                val others = decodeRecent(prefs[RECENT_SEARCHES]).filterNot { it.toPlace().isSameLocationAs(place) }
                val updated = (listOf(entry) + others).take(PlaceRepository.RECENT_SEARCH_LIMIT)
                prefs[RECENT_SEARCHES] = json.encodeToString(RECENT_SERIALIZER, updated)
            }
        }
    }

    private fun decodeRecent(raw: String?): List<StoredRecentPlace> =
        raw?.let { runCatching { json.decodeFromString(RECENT_SERIALIZER, it) }.getOrNull() }.orEmpty()

    private fun StoredRecentPlace.toPlace() =
        Place(name = name, address = address, latitude = latitude, longitude = longitude, transitLines = lines)

    private companion object {
        val RECENT_SEARCHES = stringPreferencesKey("recent_place_searches")
        val RECENT_SERIALIZER = ListSerializer(StoredRecentPlace.serializer())
    }
}
