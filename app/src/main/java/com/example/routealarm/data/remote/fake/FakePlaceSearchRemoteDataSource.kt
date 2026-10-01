package com.example.routealarm.data.remote.fake

import com.example.routealarm.data.remote.PlaceSearchRemoteDataSource
import com.example.routealarm.data.remote.dto.PlaceDto
import com.example.routealarm.data.remote.dto.PlaceSearchResponseDto
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * API Key 없이 장소 검색을 시연하기 위한 가짜 검색 API. 서울/경기 주요 장소 일부를 내장한다.
 */
class FakePlaceSearchRemoteDataSource @Inject constructor() : PlaceSearchRemoteDataSource {

    override suspend fun search(query: String): PlaceSearchResponseDto {
        delay(SIMULATED_LATENCY_MILLIS)
        val keyword = query.trim()
        if (keyword.isEmpty()) return PlaceSearchResponseDto()
        val normalized = keyword.replace(" ", "")
        return PlaceSearchResponseDto(
            places = PLACES.filter {
                it.name.replace(" ", "").contains(normalized) || it.address.replace(" ", "").contains(normalized)
            },
        )
    }

    private companion object {
        const val SIMULATED_LATENCY_MILLIS = 250L

        val PLACES = listOf(
            PlaceDto("한성대학교", "서울 성북구 삼선교로16길 116", 37.5826, 127.0105),
            PlaceDto("한성대입구역", "서울 성북구 동소문로 지하 2", 37.5885, 127.0060),
            PlaceDto("남양주 다산동", "경기 남양주시 다산동", 37.6245, 127.1532),
            PlaceDto("다산역", "경기 남양주시 다산동 8호선", 37.6249, 127.1501),
            PlaceDto("잠실역", "서울 송파구 올림픽로 지하 265", 37.5133, 127.1001),
            PlaceDto("강남역", "서울 강남구 강남대로 지하 396", 37.4979, 127.0276),
            PlaceDto("서울역", "서울 용산구 한강대로 405", 37.5547, 126.9707),
            PlaceDto("홍대입구역", "서울 마포구 양화로 지하 160", 37.5571, 126.9245),
            PlaceDto("판교역", "경기 성남시 분당구 판교역로 지하 160", 37.3948, 127.1112),
            PlaceDto("여의도역", "서울 영등포구 여의나루로 지하 40", 37.5216, 126.9242),
            PlaceDto("고려대학교", "서울 성북구 안암로 145", 37.5894, 127.0323),
            PlaceDto("서울대학교", "서울 관악구 관악로 1", 37.4599, 126.9519),
            PlaceDto("연세대학교", "서울 서대문구 연세로 50", 37.5658, 126.9386),
            PlaceDto("건국대학교", "서울 광진구 능동로 120", 37.5419, 127.0782),
            PlaceDto("광화문", "서울 종로구 세종대로 172", 37.5716, 126.9769),
            PlaceDto("코엑스", "서울 강남구 영동대로 513", 37.5116, 127.0595),
            PlaceDto("수원역", "경기 수원시 팔달구 덕영대로 924", 37.2659, 127.0000),
            PlaceDto("구리역", "경기 구리시 경춘로 지하 241", 37.6033, 127.1434),
            PlaceDto("별내역", "경기 남양주시 별내동", 37.6423, 127.1272),
            PlaceDto("성수역", "서울 성동구 아차산로 지하 100", 37.5446, 127.0558),
        )
    }
}
