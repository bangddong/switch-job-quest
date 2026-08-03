package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.DailyQuestionContent
import com.devquest.storage.db.core.DailyQuestionContentEntity
import com.devquest.storage.db.core.DailyQuestionContentRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class DailyQuestionContentAdapterTest {

    @Mock
    private lateinit var repository: DailyQuestionContentRepository

    @InjectMocks
    private lateinit var adapter: DailyQuestionContentAdapter

    @Test
    fun `findToday - 오늘 질문이 있으면 도메인 모델로 변환해 반환한다`() {
        val today = LocalDate.now()
        whenever(repository.findByMailTypeAndQuestionDate(eq("TECH_INTERVIEW"), eq(today)))
            .thenReturn(
                DailyQuestionContentEntity(
                    questionDate = today,
                    mailType = "TECH_INTERVIEW",
                    question = "질문A",
                    source = "BANK",
                    category = "java-spring",
                )
            )

        val result = adapter.findToday("TECH_INTERVIEW", today)

        assertThat(result?.question).isEqualTo("질문A")
        assertThat(result?.source).isEqualTo("BANK")
    }

    @Test
    fun `findToday - 오늘 질문이 없으면 null을 반환한다`() {
        val today = LocalDate.now()
        whenever(repository.findByMailTypeAndQuestionDate(eq("TECH_INTERVIEW"), eq(today)))
            .thenReturn(null)

        val result = adapter.findToday("TECH_INTERVIEW", today)

        assertThat(result).isNull()
    }

    @Test
    fun `save - 도메인 모델을 엔티티로 변환해 저장하고 저장된 값을 반환한다`() {
        val content = DailyQuestionContent(
            questionDate = LocalDate.now(),
            mailType = "TECH_INTERVIEW",
            question = "질문A",
            source = "AI",
        )
        whenever(repository.save(any<DailyQuestionContentEntity>())).thenAnswer { it.arguments[0] }

        val result = adapter.save(content)

        assertThat(result.question).isEqualTo("질문A")
        assertThat(result.source).isEqualTo("AI")
        verify(repository).save(any<DailyQuestionContentEntity>())
    }

    @Test
    fun `findQuestionsSince - 중복 질문이 있어도 중복 없이 반환한다`() {
        val since = LocalDate.now().minusDays(20)
        whenever(repository.findQuestionsSince(eq("TECH_INTERVIEW"), eq(since)))
            .thenReturn(listOf("질문A", "질문A", "질문B"))

        val result = adapter.findQuestionsSince("TECH_INTERVIEW", since)

        assertThat(result).containsExactlyInAnyOrder("질문A", "질문B")
    }
}
