package com.example.routealarm.domain.policy

import kotlinx.datetime.toKotlinLocalDate
import java.time.LocalDate
import com.example.routealarm.shared.KoreanHolidays as SharedHolidays

/**
 * 한국 공휴일 표(대체공휴일·선거일 포함). 표는 shared 모듈에 있어 iOS 앱도 같은 표를 쓴다.
 * 해마다 다음 해 표를 추가해야 한다(표에 없는 해는 공휴일이 없는 것으로 본다).
 */
object KoreanHolidays {
    fun isHoliday(date: LocalDate): Boolean = SharedHolidays.isHoliday(date.toKotlinLocalDate())

    fun name(date: LocalDate): String? = SharedHolidays.name(date.toKotlinLocalDate())

    /** 표에 들어 있는 마지막 해. 화면에서 "언제까지 반영되는지" 알릴 때 쓴다. */
    val lastCoveredYear: Int get() = SharedHolidays.lastCoveredYear
}
