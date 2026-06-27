package com.devquest.client.ai.support

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ConferenceReferenceLoaderTest {

    private lateinit var loader: ConferenceReferenceLoader

    @BeforeEach
    fun setup() {
        loader = ConferenceReferenceLoader()
    }

    @Test
    fun `Virtual Thread와 코루틴 비교 — java 카테고리 매칭 — youtube 링크 포함`() {
        val result = loader.findByQuestion("Virtual Thread와 코루틴 비교")

        assertThat(result).contains("youtube.com")
        assertThat(result).contains("JVM/GC/동시성")
    }

    @Test
    fun `Kafka 컨슈머 offset 관리 — messaging 카테고리 매칭`() {
        val result = loader.findByQuestion("Kafka 컨슈머 offset 관리")

        assertThat(result).contains("Kafka/메시지큐")
        assertThat(result).contains("youtube.com")
    }

    @Test
    fun `HTTP 연결 유지 — network 카테고리 매칭 — references 없음 — 빈 문자열 반환`() {
        val result = loader.findByQuestion("HTTP 연결 유지")

        assertThat(result).isEmpty()
    }

    @Test
    fun `Spring Bean 생명주기 — spring 카테고리 — references 없음 — 빈 문자열 반환`() {
        val result = loader.findByQuestion("Spring Bean 생명주기")

        assertThat(result).isEmpty()
    }

    @Test
    fun `JPA N+1 문제와 Kafka 메시지 — database + messaging 두 카테고리 모두 포함`() {
        val result = loader.findByQuestion("JPA N+1 문제와 Kafka 메시지")

        assertThat(result).contains("DB/JPA/인덱스")
        assertThat(result).contains("Kafka/메시지큐")
    }
}
