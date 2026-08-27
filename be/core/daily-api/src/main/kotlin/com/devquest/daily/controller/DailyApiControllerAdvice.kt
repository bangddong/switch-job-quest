package com.devquest.daily.controller

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.daily.controller.response.DailyApiResponse
import com.devquest.daily.controller.response.DailyErrorCode
import com.devquest.daily.support.DailyQuestionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * daily-api 자체 예외 매핑 (D-007 — core-api의 `ApiControllerAdvice`/`CoreException`을 재사용할 수
 * 없다). `AiEvaluationException`은 `core-domain` 소속이라 daily-api도 의존하므로 그대로 쓴다.
 */
@RestControllerAdvice
class DailyApiControllerAdvice {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(e: HttpMessageNotReadableException): ResponseEntity<DailyApiResponse<Nothing>> {
        log.warn("Request parse failed: ${e.message}")
        return respond(DailyErrorCode.INVALID_REQUEST)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<DailyApiResponse<Nothing>> {
        log.warn("Validation failed: ${e.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }}")
        return respond(DailyErrorCode.INVALID_REQUEST)
    }

    @ExceptionHandler(DailyQuestionNotFoundException::class)
    fun handleDailyQuestionNotFound(e: DailyQuestionNotFoundException): ResponseEntity<DailyApiResponse<Nothing>> {
        log.warn("Daily question not found: ${e.message}")
        return respond(DailyErrorCode.DAILY_QUESTION_NOT_FOUND)
    }

    @ExceptionHandler(AiEvaluationException::class)
    fun handleAiEvaluationException(e: AiEvaluationException): ResponseEntity<DailyApiResponse<Nothing>> {
        log.error("AiEvaluationException: ${e.message}", e)
        return respond(DailyErrorCode.AI_EVALUATION_FAILED)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<DailyApiResponse<Nothing>> {
        log.error("Unexpected error: ${e.message}", e)
        return respond(DailyErrorCode.INTERNAL_ERROR)
    }

    private fun respond(code: DailyErrorCode): ResponseEntity<DailyApiResponse<Nothing>> =
        ResponseEntity.status(code.status).body(DailyApiResponse.error(code))
}
