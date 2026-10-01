package com.example.routealarm.data.repository

import android.database.sqlite.SQLiteException
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.AppResult
import com.example.routealarm.core.common.IoDispatcher
import com.example.routealarm.core.util.TimeProvider
import com.example.routealarm.data.local.db.PlaceDao
import com.example.routealarm.data.mapper.TransitDtoMapper
import com.example.routealarm.data.mapper.toDomain
import com.example.routealarm.data.mapper.toEntity
import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.domain.model.Place
import com.example.routealarm.domain.repository.PlaceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultPlaceRepository @Inject constructor(
    private val dao: PlaceDao,
    private val remote: PlaceSearchRemoteDataSource,
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
}
