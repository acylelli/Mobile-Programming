package com.example.routealarm.core.common

/**
 * 앱 전체에서 사용하는 오류 모델. 화면은 이 값만 보고 사용자 메시지(strings.xml)를 선택한다.
 */
sealed interface AppError {
    /** 인터넷 연결 없음 또는 타임아웃 */
    data object Network : AppError

    /** 교통/검색 서버가 오류 응답을 준 경우 */
    data class Server(val code: Int) : AppError

    /** 출발지-도착지 사이 대중교통 경로가 없음 */
    data object NoRoute : AppError

    data object PlaceSearchFailed : AppError
    data object LocationPermissionDenied : AppError
    data object LocationUnavailable : AppError
    data object Database : AppError
    data object AlarmSchedulingFailed : AppError
    data object NotFound : AppError

    /** 단체 이용권 코드가 형식에 맞지 않거나 등록되지 않은 코드 */
    data object GroupPassInvalid : AppError

    /** 이용 기간이 끝난 단체 이용권 */
    data object GroupPassExpired : AppError

    /** 기관이 구매한 인원을 모두 사용한 단체 이용권 */
    data object GroupPassFull : AppError

    /** 결제를 시작하지 못했거나 구독 상태를 검증하지 못함 */
    data object PurchaseFailed : AppError

    data class InvalidInput(val reason: InvalidInputReason) : AppError

    data class Unknown(val message: String? = null) : AppError
}

enum class InvalidInputReason {
    MISSING_ORIGIN,
    MISSING_DESTINATION,
    SAME_ORIGIN_AND_DESTINATION,
    NEGATIVE_DURATION,
    TARGET_IN_PAST,
    NO_REPEAT_DAY,
}
