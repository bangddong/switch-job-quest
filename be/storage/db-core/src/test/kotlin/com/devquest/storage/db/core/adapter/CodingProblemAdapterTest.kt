package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.storage.db.core.CodingProblemEntity
import com.devquest.storage.db.core.CodingProblemRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.whenever
import java.util.Optional

/**
 * J2(com.fasterxml.jackson) -> J3(tools.jackson) 전환 후에도
 * testCases 직렬화/역직렬화 동작이 그대로 유지되는지 검증하는 회귀 테스트.
 * (QA 지적 F-1: DB에 이미 저장된 J2 시절 JSON도 새 J3 코드로 정확히 파싱돼야 한다)
 */
@ExtendWith(MockitoExtension::class)
class CodingProblemAdapterTest {

    @Mock
    private lateinit var repository: CodingProblemRepository

    @InjectMocks
    private lateinit var adapter: CodingProblemAdapter

    @Test
    fun `save 후 findById로 조회하면 testCases가 원본과 동일하게 역직렬화된다`() {
        val testCases = listOf(
            TestCase(input = "1 2", expectedOutput = "3"),
            TestCase(input = "", expectedOutput = "empty"),
            TestCase(input = "hello world", expectedOutput = "world hello"),
        )
        val problem = CodingProblem(
            title = "두 수의 합",
            description = "설명",
            difficulty = "easy",
            language = "kotlin",
            category = "array",
            solutionCode = "fun solve() {}",
            testCases = testCases,
        )
        val captor = argumentCaptor<CodingProblemEntity>()
        whenever(repository.save(captor.capture())).thenAnswer { it.arguments[0] as CodingProblemEntity }

        val saved = adapter.save(problem)

        assertThat(saved.testCases).isEqualTo(testCases)

        val persistedEntity = captor.firstValue
        whenever(repository.findById(1L)).thenReturn(Optional.of(persistedEntity))

        val found = adapter.findById(1L)

        assertThat(found).isNotNull()
        assertThat(found!!.testCases).containsExactlyElementsOf(testCases)
    }

    @Test
    fun `testCases가 빈 리스트여도 정상적으로 저장 및 조회된다`() {
        val problem = CodingProblem(
            title = "빈 테스트케이스 문제",
            description = "설명",
            difficulty = "easy",
            language = "kotlin",
            category = "array",
            solutionCode = "fun solve() {}",
            testCases = emptyList(),
        )
        val captor = argumentCaptor<CodingProblemEntity>()
        whenever(repository.save(captor.capture())).thenAnswer { it.arguments[0] as CodingProblemEntity }

        val saved = adapter.save(problem)

        assertThat(saved.testCases).isEmpty()
        assertThat(captor.firstValue.testCases).isEqualTo("[]")
    }

    @Test
    fun `J2로 저장된 것으로 가정한 레거시 JSON 문자열이 J3로 정확히 파싱된다`() {
        val legacyJson = """[{"input":"1 2","expectedOutput":"3"},{"input":"","expectedOutput":"empty"}]"""
        val entity = CodingProblemEntity(
            title = "레거시 문제",
            description = "설명",
            difficulty = "medium",
            language = "kotlin",
            category = "string",
            solutionCode = "fun solve() {}",
            testCases = legacyJson,
        )
        whenever(repository.findById(1L)).thenReturn(Optional.of(entity))

        val result = adapter.findById(1L)

        assertThat(result).isNotNull()
        // 조용한 실패(파싱 실패 시 emptyList) 가드: 반드시 non-empty로 파싱돼야 한다
        assertThat(result!!.testCases).isNotEmpty()
        assertThat(result.testCases).containsExactly(
            TestCase(input = "1 2", expectedOutput = "3"),
            TestCase(input = "", expectedOutput = "empty"),
        )
    }
}
