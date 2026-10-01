package com.example.routealarm.core.common

/**
 * Repository → UseCase → ViewModel 로 전달되는 결과 타입.
 *
 * 설계 이유: Repository 에서 Exception 을 그대로 던지면 UI 가 IOException, SQLiteException 같은
 * 구현 세부사항을 알아야 한다. 경계에서 [AppError] 로 변환해 두면 ViewModel 은 "무슨 일이 일어났는지"만
 * 보고 사용자 메시지를 고를 수 있고, 테스트에서도 예외 대신 값으로 검증할 수 있다.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
    is AppResult.Success -> transform(data)
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) action(data)
    return this
}

inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) action(error)
    return this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.data

fun <T> AppResult<T>.errorOrNull(): AppError? = (this as? AppResult.Failure)?.error
