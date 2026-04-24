package com.devquest.support.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class LogtailHttpAppender : AppenderBase<ILoggingEvent>() {

    var sourceToken: String = ""
    var endpoint: String = "https://in.logs.betterstack.com"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    override fun append(event: ILoggingEvent) {
        if (sourceToken.isBlank()) {
            return
        }

        try {
            val body = buildJsonBody(event)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer ${sourceToken}")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())

            if (response.statusCode() !in 200..299) {
                addError("Logtail HTTP 전송 실패 — 상태 코드: ${response.statusCode()}")
            }
        } catch (e: Exception) {
            addError("Logtail HTTP 전송 중 예외 발생: ${e.message}", e)
        }
    }

    private fun buildJsonBody(event: ILoggingEvent): String {
        val timestamp = timestampFormatter.format(Instant.ofEpochMilli(event.timeStamp))
        val message = escapeJson(event.formattedMessage)
        val level = escapeJson(event.level.toString())
        val logger = escapeJson(event.loggerName)
        val thread = escapeJson(event.threadName)

        val mdcEntries = buildString {
            event.mdcPropertyMap.entries.forEach { (key, value) ->
                append(", \"${escapeJson(key)}\": \"${escapeJson(value)}\"")
            }
        }

        val throwableProxy = event.throwableProxy
        val throwableInfo = if (throwableProxy != null) {
            val exMsg = escapeJson(throwableProxy.message ?: "")
            val exClass = escapeJson(throwableProxy.className)
            ", \"exception\": \"${exClass}: ${exMsg}\""
        } else {
            ""
        }

        return """[{"dt":"$timestamp","message":"$message","level":"$level","logger":"$logger","thread":"$thread"$mdcEntries$throwableInfo}]"""
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
