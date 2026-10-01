package com.example.routealarm.domain.repository

import com.example.routealarm.core.common.AppResult
import com.example.routealarm.domain.model.Place
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    fun observeSavedPlaces(): Flow<List<Place>>
    suspend fun getPlace(id: Long): Place?
    suspend fun savePlace(place: Place): AppResult<Long>
    suspend fun deletePlace(id: Long): AppResult<Unit>
    suspend fun search(query: String): AppResult<List<Place>>
}

/** 첫 실행 예시 데이터(집 → 한성대학교). 문자열 리소스를 쓰기 위해 data 계층에서 구현한다. */
interface DemoDataProvider {
    fun demoOrigin(): Place
    fun demoDestination(): Place
    fun demoTitle(): String
}
