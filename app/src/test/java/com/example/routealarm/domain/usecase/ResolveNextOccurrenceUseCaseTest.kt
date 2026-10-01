package com.example.routealarm.domain.usecase

import com.example.routealarm.fake.TestData.SEOUL
import com.example.routealarm.fake.TestData.seoul
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

class ResolveNextOccurrenceUseCaseTest {

    private val resolve = ResolveNextOccurrenceUseCase()
    private val monday = LocalDate.of(2026, 10, 5)
    private val nine = LocalTime.of(9, 0)
    private val lead = Duration.ofMinutes(100)

    @Test
    fun `오늘이 반복 요일이고 아직 준비 전이면 오늘`() {
        val result = resolve(nine, setOf(DayOfWeek.MONDAY), null, seoul(monday, 6, 0), SEOUL, lead)
        assertEquals(seoul(monday, 9, 0), result)
    }

    @Test
    fun `오늘 회차의 마지막 이벤트가 지났으면 다음 반복 요일`() {
        val result = resolve(
            nine, setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), null, seoul(monday, 7, 30), SEOUL, lead,
        )
        assertEquals(seoul(monday.plusDays(2), 9, 0), result)
    }

    @Test
    fun `한 요일만 반복하면 다음 주 같은 요일`() {
        val result = resolve(nine, setOf(DayOfWeek.MONDAY), null, seoul(monday, 10, 0), SEOUL, lead)
        assertEquals(seoul(monday.plusWeeks(1), 9, 0), result)
    }

    @Test
    fun `1회 일정은 지정 날짜를 그대로 사용`() {
        val date = monday.plusDays(3)
        assertEquals(seoul(date, 9, 0), resolve(nine, emptySet(), date, seoul(monday, 10, 0), SEOUL, lead))
        assertNull(resolve(nine, emptySet(), null, seoul(monday, 10, 0), SEOUL, lead))
    }

    @Test
    fun `1회 일정 날짜는 목표시각이 지났으면 내일`() {
        assertEquals(monday, resolve.resolveOneTimeDate(nine, seoul(monday, 8, 0), SEOUL))
        assertEquals(monday.plusDays(1), resolve.resolveOneTimeDate(nine, seoul(monday, 9, 30), SEOUL))
    }
}
