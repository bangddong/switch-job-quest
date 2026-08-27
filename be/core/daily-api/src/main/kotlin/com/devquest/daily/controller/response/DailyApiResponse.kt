package com.devquest.daily.controller.response

/**
 * daily-api 자체 응답 형식 (D-007 — core-api의 `ApiResponse<T>`를 재사용할 수 없다).
 * core-api의 `ApiResponse`와 필드 이름·의미는 같지만(`result`/`data`/`error`) 별개 클래스다 —
 * 두 앱의 응답 스키마가 갈라질 수 있음을 D-007이 이미 감수했다.
 */
data class DailyApiResponse<T>(
    val result: String,
    val data: T? = null,
    val error: DailyApiErrorBody? = null,
) {
    companion object {
        fun <T> success(data: T): DailyApiResponse<T> = DailyApiResponse(result = "SUCCESS", data = data)

        fun error(code: DailyErrorCode): DailyApiResponse<Nothing> =
            DailyApiResponse(result = "ERROR", error = DailyApiErrorBody(code.name, code.message))
    }
}

data class DailyApiErrorBody(
    val code: String,
    val message: String,
)
