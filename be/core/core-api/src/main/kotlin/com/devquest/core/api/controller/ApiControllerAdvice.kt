package com.devquest.core.api.controller

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import com.devquest.core.support.response.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ApiControllerAdvice {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Any>> {
        log.warn("Request parse failed: ${e.message}")
        return ResponseEntity
            .status(ErrorType.INVALID_REQUEST.status)
            .body(ApiResponse.error(ErrorType.INVALID_REQUEST))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Any>> {
        log.warn("Validation failed: ${e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }}")
        return ResponseEntity
            .status(ErrorType.INVALID_REQUEST.status)
            .body(ApiResponse.error(ErrorType.INVALID_REQUEST))
    }

    @ExceptionHandler(CoreException::class)
    fun handleCoreException(e: CoreException): ResponseEntity<ApiResponse<Any>> {
        log.error("CoreException: ${e.errorType.message}", e)
        return ResponseEntity
            .status(e.errorType.status)
            .body(ApiResponse.error(e.errorType, e.data))
    }

    @ExceptionHandler(AiEvaluationException::class)
    fun handleAiEvaluationException(e: AiEvaluationException): ResponseEntity<ApiResponse<Any>> {
        log.error("AiEvaluationException: ${e.message}", e)
        return ResponseEntity
            .status(ErrorType.AI_EVALUATION_FAILED.status)
            .body(ApiResponse.error(ErrorType.AI_EVALUATION_FAILED))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Any>> {
        log.error("Unexpected error: ${e.message}", e)
        return ResponseEntity
            .status(ErrorType.DEFAULT.status)
            .body(ApiResponse.error(ErrorType.DEFAULT))
    }
}
