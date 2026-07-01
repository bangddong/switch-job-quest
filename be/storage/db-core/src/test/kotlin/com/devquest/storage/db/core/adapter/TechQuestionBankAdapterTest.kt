package com.devquest.storage.db.core.adapter

import com.devquest.storage.db.core.TechQuestionBankEntity
import com.devquest.storage.db.core.TechQuestionBankRepository
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

@ExtendWith(MockitoExtension::class)
class TechQuestionBankAdapterTest {

    @Mock
    private lateinit var repository: TechQuestionBankRepository

    @InjectMocks
    private lateinit var adapter: TechQuestionBankAdapter

    @Test
    fun `카테고리와 제외목록이 모두 있으면 카테고리+제외 조건으로 조회한다`() {
        val entity = TechQuestionBankEntity(
            category = "java-spring",
            question = "새 질문",
            referenceUrl = null,
            source = "manual-seed",
        )
        whenever(
            repository.findAllByCategoryAndQuestionNotIn(eq("java-spring"), any())
        ).thenReturn(listOf(entity))

        val result = adapter.findUnused(listOf("이미 사용된 질문"), "java-spring")

        assertThat(result).isNotNull()
        assertThat(result!!.question).isEqualTo("새 질문")
        assertThat(result.category).isEqualTo("java-spring")
    }

    @Test
    fun `제외목록만 있으면 제외 조건만으로 조회한다`() {
        val entity = TechQuestionBankEntity(category = "database", question = "새 질문2", source = "manual-seed")
        whenever(repository.findAllByQuestionNotIn(any())).thenReturn(listOf(entity))

        val result = adapter.findUnused(listOf("이미 사용된 질문"), null)

        assertThat(result).isNotNull()
        assertThat(result!!.question).isEqualTo("새 질문2")
        verify(repository).findAllByQuestionNotIn(listOf("이미 사용된 질문"))
    }

    @Test
    fun `제외목록도 카테고리도 없으면 전체에서 하나를 조회한다`() {
        val entity = TechQuestionBankEntity(category = "database", question = "새 질문3", source = "manual-seed")
        whenever(repository.findAll()).thenReturn(listOf(entity))

        val result = adapter.findUnused(emptyList(), null)

        assertThat(result).isNotNull()
        assertThat(result!!.question).isEqualTo("새 질문3")
    }

    @Test
    fun `조건에 맞는 미사용 질문이 없으면 null을 반환한다`() {
        whenever(repository.findAll()).thenReturn(emptyList())

        val result = adapter.findUnused(emptyList(), null)

        assertThat(result).isNull()
    }

    @Test
    fun `후보가 여러 건이면 그 중 하나를 무작위로 반환한다`() {
        val entities = (1..20).map {
            TechQuestionBankEntity(category = "database", question = "질문$it", source = "manual-seed")
        }
        whenever(repository.findAll()).thenReturn(entities)

        val result = adapter.findUnused(emptyList(), null)

        assertThat(result).isNotNull()
        assertThat(entities.map { it.question }).contains(result!!.question)
    }

    @Test
    fun `여러 번 호출하면 매번 같은 질문만 나오지 않는다`() {
        val entities = (1..30).map {
            TechQuestionBankEntity(category = "database", question = "질문$it", source = "manual-seed")
        }
        whenever(repository.findAll()).thenReturn(entities)

        val results = (1..30).map { adapter.findUnused(emptyList(), null)?.question }.toSet()

        assertThat(results.size).isGreaterThan(1)
    }
}
