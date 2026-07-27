package com.devquest.storage.db.core.adapter

import com.devquest.storage.db.core.DailyMailLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class DailyMailLogAdapterTest {

    @Mock
    private lateinit var repository: DailyMailLogRepository

    @InjectMocks
    private lateinit var adapter: DailyMailLogAdapter

    @Test
    fun `동일 질문이 여러 사용자에 의해 중복 저장돼 있어도 중복 없이 반환한다`() {
        // 사용자 N명이 같은 날 같은 질문을 받으면 N개의 행이 저장된다.
        // 이 중복이 그대로 반환되면 실제로 덮는 "질문 종류" 수가 줄어들어
        // 제외 목록의 실효 기간이 사용자 수에 반비례해 짧아진다.
        val since = LocalDateTime.now().minusDays(20)
        whenever(repository.findQuestionContentsSince(eq("TECH_INTERVIEW"), eq(since)))
            .thenReturn(listOf("질문A", "질문A", "질문A", "질문B", "질문B"))

        val result = adapter.findQuestionsSince("TECH_INTERVIEW", since)

        assertThat(result).containsExactlyInAnyOrder("질문A", "질문B")
    }

    @Test
    fun `조회 시 지정한 기준 시각을 repository에 그대로 전달한다`() {
        val since = LocalDateTime.now().minusDays(20)
        whenever(repository.findQuestionContentsSince(eq("TECH_INTERVIEW"), eq(since)))
            .thenReturn(emptyList())

        adapter.findQuestionsSince("TECH_INTERVIEW", since)

        verify(repository).findQuestionContentsSince("TECH_INTERVIEW", since)
    }
}
