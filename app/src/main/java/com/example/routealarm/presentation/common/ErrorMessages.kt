package com.example.routealarm.presentation.common

import androidx.annotation.StringRes
import com.example.routealarm.R
import com.example.routealarm.core.common.AppError
import com.example.routealarm.core.common.InvalidInputReason

/** AppError → 사용자 메시지 리소스. UI 는 AppError 의 종류만 알고 문자열은 리소스에서 가져온다. */
@StringRes
fun AppError.messageRes(): Int = when (this) {
    AppError.Network -> R.string.error_network
    is AppError.Server -> R.string.error_server
    AppError.NoRoute -> R.string.error_no_route
    AppError.PlaceSearchFailed -> R.string.error_place_search
    AppError.LocationPermissionDenied -> R.string.error_location_permission
    AppError.LocationUnavailable -> R.string.error_location_unavailable
    AppError.Database -> R.string.error_database
    AppError.AlarmSchedulingFailed -> R.string.error_alarm
    AppError.NotFound -> R.string.error_not_found
    is AppError.InvalidInput -> when (reason) {
        InvalidInputReason.MISSING_ORIGIN -> R.string.error_missing_origin
        InvalidInputReason.MISSING_DESTINATION -> R.string.error_missing_destination
        InvalidInputReason.SAME_ORIGIN_AND_DESTINATION -> R.string.error_same_place
        InvalidInputReason.NEGATIVE_DURATION -> R.string.error_negative_duration
        InvalidInputReason.TARGET_IN_PAST -> R.string.error_target_in_past
        InvalidInputReason.NO_REPEAT_DAY -> R.string.error_no_repeat_day
    }
    is AppError.Unknown -> R.string.error_unknown
}
