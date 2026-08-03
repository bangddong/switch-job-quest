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
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class DailyMailLogAdapterTest {

    @Mock
    private lateinit var repository: DailyMailLogRepository

    @InjectMocks
    private lateinit var adapter: DailyMailLogAdapter

    @Test
    fun `existsTodayLog - 오늘 발송 이력이 있으면 true를 반환한다`() {
        val today = LocalDate.now()
        val start = today.atStartOfDay()
        val end = today.plusDays(1).atStartOfDay()
        whenever(
            repository.existsByUserIdAndMailTypeAndSentAtBetween(eq("user1"), eq("TECH_INTERVIEW"), eq(start), eq(end))
        ).thenReturn(true)

        val result = adapter.existsTodayLog("user1", "TECH_INTERVIEW", today)

        assertThat(result).isTrue()
    }

    @Test
    fun `save - 지정한 파라미터로 저장을 위임한다`() {
        val sentAt = LocalDateTime.now()

        adapter.save("user1", "TECH_INTERVIEW", "질문A", sentAt)

        verify(repository).save(org.mockito.kotlin.any())
    }
}
