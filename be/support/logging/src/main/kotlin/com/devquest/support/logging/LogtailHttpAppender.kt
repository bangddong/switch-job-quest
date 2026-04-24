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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class LogtailHttpAppender : AppenderBase<ILoggingEvent>() {

    var sourceToken: String = ""
    var endpoint: String = "https://in.logs.betterstack.com"

    private val queue = ConcurrentLinkedQueue<ILoggingEvent>()
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "logtail-flusher").also { it.isDaemon = true }
    }

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC)

    override fun start() {
        super.start()
        scheduler.scheduleAtFixedRate(::flush, 1, 1, TimeUnit.SECONDS)
    }

    override fun stop() {
        scheduler.shutdown()
        try {
            scheduler.awaitTermination(10, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        flush()
        super.stop()
    }

    override fun append(event: ILoggingEvent) {
        if (sourceToken.isBlank()) return
        event.prepareForDeferredProcessing()
        queue.add(event)
        if (queue.size >= 100) scheduler.execute(::flush)
    }

    private fun flush() {
        if (sourceToken.isBlank() || queue.isEmpty()) return
        val batch = mutableListOf<ILoggingEvent>()
        while (true) { batch.add(queue.poll() ?: break) }
        if (batch.isEmpty()) return

        try {
            val body = buildJsonArrayBody(batch)
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

    private fun buildJsonArrayBody(events: List<ILoggingEvent>): String {
        val entries = events.joinToString(",") { buildJsonEntry(it) }
        return "[$entries]"
    }

    private fun buildJsonEntry(event: ILoggingEvent): String {
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

        return """{"dt":"$timestamp","message":"$message","level":"$level","logger":"$logger","thread":"$thread"$mdcEntries$throwableInfo}"""
    }

    private fun escapeJson(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
