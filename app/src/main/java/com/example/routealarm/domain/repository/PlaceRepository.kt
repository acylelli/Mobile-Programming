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

    /** 최근에 검색해서 고른 장소(최신순, 최대 [RECENT_SEARCH_LIMIT]개) */
    fun observeRecentSearches(): Flow<List<Place>>
    suspend fun addRecentSearch(place: Place)

    companion object {
        const val RECENT_SEARCH_LIMIT = 5
    }
}

