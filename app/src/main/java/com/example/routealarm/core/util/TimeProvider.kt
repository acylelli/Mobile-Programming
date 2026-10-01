package com.example.routealarm.core.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * 현재 시각과 시간대를 제공한다.
 *
 * 설계 이유:
 * 1. 시간 계산 로직을 테스트할 때 "지금"을 고정할 수 있어야 한다.
 * 2. java.time.Clock.systemDefaultZone() 은 생성 시점의 ZoneId 를 붙잡아 두기 때문에
 *    사용자가 여행 중 시간대를 바꾸면 오래된 Zone 으로 계산하게 된다.
 *    그래서 zone() 은 호출할 때마다 ZoneId.systemDefault() 를 읽는다.
 */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun nowZoned(): ZonedDateTime = now().atZone(zone())
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
