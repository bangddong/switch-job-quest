package com.devquest.core.support.response

import com.devquest.core.support.error.ErrorMessage
import com.devquest.core.support.error.ErrorType

class ApiResponse<T> private constructor(
    val result: ResultType,
    val data: T? = null,
    val error: ErrorMessage? = null
) {
    companion object {
        fun success(): ApiResponse<Any> {
            return ApiResponse(ResultType.SUCCESS, null, null)
        }

        fun <S> success(data: S): ApiResponse<S> {
            return ApiResponse(ResultType.SUCCESS, data, null)
        }

        fun <S> error(error: ErrorType, errorData: Any? = null): ApiResponse<S> {
            return ApiResponse(ResultType.ERROR, null, ErrorMessage(error, errorData))
        }
    }
}
