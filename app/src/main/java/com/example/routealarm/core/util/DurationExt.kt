package com.example.routealarm.core.util

import java.time.Duration
import kotlin.math.roundToLong

/** 분 단위 반올림. 사용자에게 보여주는 모든 시간은 분 단위다. */
fun Duration.roundedMinutes(): Long = (toMillis() / 60_000.0).roundToLong()

fun Int.minutes(): Duration = Duration.ofMinutes(toLong())
fun Long.minutes(): Duration = Duration.ofMinutes(this)
