package com.devquest.core.api.controller

import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import com.devquest.core.support.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiControllerAdvice {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        log.error("CoreException: ${e.errorType.message}", e)
        return ResponseEntity
            .status(e.errorType.status)
            .body(ApiResponse.error(e.errorType, e.data))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("Unexpected error: ${e.message}", e)
        return ResponseEntity
            .status(ErrorType.DEFAULT.status)
            .body(ApiResponse.error(ErrorType.DEFAULT))
    }
}
