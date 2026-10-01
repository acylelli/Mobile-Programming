package com.example.routealarm.core.common

import javax.inject.Qualifier

/**
 * Dispatcher 를 직접 하드코딩하지 않고 주입받는다. 테스트에서 TestDispatcher 로 교체하기 위함.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/**
 * 화면 생명주기보다 오래 살아야 하는 작업(BroadcastReceiver 의 goAsync 처리 등)에 쓰는 앱 단위 Scope.
 * GlobalScope 대신 Hilt 가 SupervisorJob 기반 Scope 하나를 관리한다.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
