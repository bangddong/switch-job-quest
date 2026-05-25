package com.devquest.core.domain

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.core.domain.port.CodingHintPort
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.CodingProblemPort
import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import com.devquest.core.domain.port.UserCodingLevelPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CodingQuestServiceTest {

    @Mock lateinit var codingProblemGeneratorPort: CodingProblemGeneratorPort
    @Mock lateinit var codingProblemPort: CodingProblemPort
    @Mock lateinit var userCodingLevelPort: UserCodingLevelPort
    @Mock lateinit var codingSubmissionPort: CodingSubmissionPort
    @Mock lateinit var judge0Port: Judge0Port
    @Mock lateinit var codingHintPort: CodingHintPort

    @InjectMocks
    private lateinit var service: CodingQuestService

    private val testCases = listOf(
        TestCase(input = "5", expectedOutput = "25"),
        TestCase(input = "3", expectedOutput = "9"),
        TestCase(input = "0", expectedOutput = "0")
    )

    private val sampleProblem = CodingProblem(
        id = 1L,
        title = "제곱 계산",
        description = "정수를 입력받아 제곱을 출력하세요.",
        difficulty = "EASY",
        language = "JAVA",
        solutionCode = "import java.util.Scanner; class Main { public static void main(String[] args) { Scanner sc = new Scanner(System.in); int n = sc.nextInt(); System.out.println(n * n); } }",
        testCases = testCases
    )

    // ===== generateProblem 테스트 =====

    @Test
    fun `generateProblem - 기존 문제가 있으면 AI 호출 없이 반환`() {
        whenever(userCodingLevelPort.getLevel("user1")).thenReturn(2)
        whenever(codingProblemPort.findByDifficultyAndLanguage("EASY", "JAVA"))
            .thenReturn(listOf(sampleProblem))

        val result = service.generateProblem("user1", "JAVA")

        assertThat(result.title).isEqualTo("제곱 계산")
        verifyNoInteractions(codingProblemGeneratorPort)
    }

    @Test
    fun `generateProblem - 기존 문제가 없으면 AI 생성 후 저장`() {
        val generationResult = CodingProblemGenerationResult(
            title = "새 문제",
            description = "새 문제 설명",
            solutionCode = "class Main {}",
            testCases = testCases
        )
        whenever(userCodingLevelPort.getLevel("user1")).thenReturn(2)
        whenever(codingProblemPort.findByDifficultyAndLanguage("EASY", "JAVA")).thenReturn(emptyList())
        whenever(codingProblemGeneratorPort.generate("EASY", "JAVA")).thenReturn(generationResult)
        // testCases: input="5"→"25", "3"→"9", "0"→"0"
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "25", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("3"), eq("9"))).thenReturn(Judge0Result(stdout = "9", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("0"), eq("0"))).thenReturn(Judge0Result(stdout = "0", status = "Accepted", passed = true))
        whenever(codingProblemPort.save(any())).thenReturn(sampleProblem.copy(title = "새 문제"))

        val result = service.generateProblem("user1", "JAVA")

        assertThat(result.title).isEqualTo("새 문제")
        verify(codingProblemPort).save(any())
    }

    @Test
    fun `getLevel - userId에 해당하는 레벨 반환`() {
        whenever(userCodingLevelPort.getLevel("user1")).thenReturn(5)

        val level = service.getLevel("user1")

        assertThat(level).isEqualTo(5)
    }

    // ===== submitCode 테스트 =====

    @Test
    fun `submitCode - 모든 테스트케이스 통과 시 passed=true 반환`() {
        whenever(codingProblemPort.findById(1L)).thenReturn(sampleProblem)
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "25", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("3"), eq("9"))).thenReturn(Judge0Result(stdout = "9", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("0"), eq("0"))).thenReturn(Judge0Result(stdout = "0", status = "Accepted", passed = true))
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any())).thenReturn(10L)
        // solveCount 1 → 레벨업 조건 불충족
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(1)

        val result = service.submitCode("user1", 1L, "JAVA", "class Main {}")

        assertThat(result.passed).isEqualTo(true)
        verify(codingSubmissionPort).save(eq("user1"), eq(1L), eq("JAVA"), any(), eq(true), any())
    }

    @Test
    fun `submitCode - 하나라도 실패 시 passed=false 반환`() {
        whenever(codingProblemPort.findById(1L)).thenReturn(sampleProblem)
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "wrong", status = "Wrong Answer", passed = false))
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any())).thenReturn(11L)

        val result = service.submitCode("user1", 1L, "JAVA", "class Main {}")

        assertThat(result.passed).isEqualTo(false)
        verify(codingSubmissionPort).save(eq("user1"), eq(1L), eq("JAVA"), any(), eq(false), any())
    }

    // ===== getHint 테스트 =====

    @Test
    fun `getHint - hintLevel 1로 요청하면 힌트를 반환`() {
        val expected = CodingHint(hint = "문제를 다른 관점으로 바라보세요.")
        whenever(codingHintPort.getHint(1L, "두 수의 합", "두 정수를 더하세요", 1))
            .thenReturn(expected)

        val result = service.getHint(1L, "두 수의 합", "두 정수를 더하세요", 1)

        assertThat(result.hint).isEqualTo("문제를 다른 관점으로 바라보세요.")
        verify(codingHintPort).getHint(1L, "두 수의 합", "두 정수를 더하세요", 1)
    }

    @Test
    fun `getHint - hintLevel이 1~3 범위를 벗어나면 예외 발생`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            service.getHint(1L, "제목", "설명", 4)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `submitCode - solve_count가 3의 배수가 되면 레벨 업`() {
        whenever(codingProblemPort.findById(1L)).thenReturn(sampleProblem)
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "25", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("3"), eq("9"))).thenReturn(Judge0Result(stdout = "9", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("0"), eq("0"))).thenReturn(Judge0Result(stdout = "0", status = "Accepted", passed = true))
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any())).thenReturn(12L)
        // solveCount가 2이면 incrementSolveCount 후 3 → 레벨업
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(2)

        service.submitCode("user1", 1L, "JAVA", "class Main {}")

        verify(userCodingLevelPort).incrementSolveCount("user1")
        verify(userCodingLevelPort).incrementLevel("user1")
    }
}
