package com.devquest.core.api.adapter.ai.http.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

/**
 * `AiHttpAdapterTimeoutAndAcceptHeaderTest` 전용 실측용 가짜 ai-api 서버.
 *
 * `MockRestServiceServer`는 `RestClient`의 요청 팩토리 자체를 교체하므로 실제 커넥트/리드 타임아웃
 * 설정이나 서버 측 `produces`(Content-Type) 협상 로직이 전혀 개입하지 않는다(계획 문서 함정 (a)·(c)를
 * 진짜로 증명하려면 실제 소켓 통신이 필요). 이 헬퍼는 JDK 내장 `com.sun.net.httpserver.HttpServer`로
 * 실제 포트를 열어 응답 지연·`Content-Type` 헤더를 완전히 제어한다 — 추가 테스트 의존성이 필요 없다.
 */
class FakeAiApiServer(
    private val respond: (path: String) -> FakeAiApiResponse,
) : AutoCloseable {

    private val server: HttpServer = HttpServer.create(InetSocketAddress("localhost", 0), 0)

    val port: Int get() = server.address.port

    init {
        server.createContext(
            "/",
            HttpHandler { exchange: HttpExchange ->
                val response = respond(exchange.requestURI.path)
                if (response.delayMillis > 0) {
                    Thread.sleep(response.delayMillis)
                }
                val acceptHeader = exchange.requestHeaders.getFirst("Accept") ?: "*/*"
                if (!isAcceptable(acceptHeader, response.contentType)) {
                    // 실제 Spring MVC(ai-api)가 Accept와 produces가 안 맞을 때 내는 406을 재현한다.
                    exchange.sendResponseHeaders(406, -1)
                    exchange.close()
                    return@HttpHandler
                }
                exchange.responseHeaders.add("Content-Type", response.contentType)
                val bytes = response.body.toByteArray(StandardCharsets.UTF_8)
                exchange.sendResponseHeaders(response.statusCode, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            },
        )
        server.executor = null
        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        /** `Accept: text/plain, application/json;q=0.9, ...` 형태를 단순 비교(타입/서브타입, 파라미터 무시)로 협상한다. */
        fun isAcceptable(acceptHeader: String, contentType: String): Boolean {
            val produces = contentType.substringBefore(";").trim()
            val (producesType, producesSubtype) = produces.split("/", limit = 2).let { it[0] to it.getOrElse(1) { "*" } }
            return acceptHeader.split(",").any { rawEntry ->
                val entry = rawEntry.substringBefore(";").trim()
                if (entry == "*/*") return@any true
                val parts = entry.split("/", limit = 2)
                if (parts.size != 2) return@any false
                val (type, subtype) = parts
                (type == "*" || type == producesType) && (subtype == "*" || subtype == producesSubtype)
            }
        }
    }
}

data class FakeAiApiResponse(
    val statusCode: Int = 200,
    val contentType: String = "application/json",
    val body: String = "{}",
    val delayMillis: Long = 0,
)
