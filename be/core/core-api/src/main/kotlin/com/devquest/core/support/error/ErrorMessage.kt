package com.devquest.core.support.error

data class ErrorMessage(
    val code: ErrorCode,
    val message: String,
    val data: Any? = null
) {
    constructor(errorType: ErrorType, data: Any? = null) : this(
        code = errorType.code,
        message = errorType.message,
        data = data
    )
}
