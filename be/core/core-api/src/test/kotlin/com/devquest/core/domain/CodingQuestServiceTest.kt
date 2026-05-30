package com.devquest.core.domain

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.CodingProblemGenerationResult
import com.devquest.core.domain.model.coding.TestCase
import com.devquest.core.domain.port.CodingHintPort
import com.devquest.core.domain.port.CodingPassRecord
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.CodingProblemPort
import com.devquest.core.domain.port.CodingRankPort
import com.devquest.core.domain.port.CodingRoadmapProgressPort
import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.Judge0Result
import com.devquest.core.domain.port.UserCodingLevelPort
import java.time.LocalDate
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
    @Mock lateinit var codingRoadmapProgressPort: CodingRoadmapProgressPort
    @Mock lateinit var codingRankPort: CodingRankPort

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
        whenever(codingProblemPort.findByCategoryAndLanguage("ARRAY", "JAVA"))
            .thenReturn(listOf(sampleProblem))

        val result = service.generateProblem("user1", "JAVA", "ARRAY")

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
        whenever(codingProblemPort.findByCategoryAndLanguage("ARRAY", "JAVA")).thenReturn(emptyList())
        whenever(codingProblemGeneratorPort.generate("EASY", "JAVA", "ARRAY")).thenReturn(generationResult)
        // testCases: input="5"→"25", "3"→"9", "0"→"0"
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "25", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("3"), eq("9"))).thenReturn(Judge0Result(stdout = "9", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("0"), eq("0"))).thenReturn(Judge0Result(stdout = "0", status = "Accepted", passed = true))
        whenever(codingProblemPort.save(any())).thenReturn(sampleProblem.copy(title = "새 문제"))

        val result = service.generateProblem("user1", "JAVA", "ARRAY")

        assertThat(result.title).isEqualTo("새 문제")
        verify(codingProblemPort).save(any())
    }

    // ===== getRoadmapProgress 테스트 =====

    @Test
    fun `getRoadmapProgress - 첫 번째 카테고리는 항상 잠금 해제`() {
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "ARRAY")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "HASH_MAP")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "STACK_QUEUE")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "BINARY_SEARCH")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "RECURSION")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "TREE")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "GRAPH")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "GREEDY")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "DP")).thenReturn(0)

        val result = service.getRoadmapProgress("user1")

        assertThat(result).hasSize(9)
        assertThat(result[0].locked).isFalse()
        assertThat(result[1].locked).isTrue()
    }

    @Test
    fun `getRoadmapProgress - 이전 카테고리 3문제 통과 시 다음 카테고리 잠금 해제`() {
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "ARRAY")).thenReturn(3)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "HASH_MAP")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "STACK_QUEUE")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "BINARY_SEARCH")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "RECURSION")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "TREE")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "GRAPH")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "GREEDY")).thenReturn(0)
        whenever(codingRoadmapProgressPort.countSolvedByUserAndCategory("user1", "DP")).thenReturn(0)

        val result = service.getRoadmapProgress("user1")

        assertThat(result[0].locked).isFalse()
        assertThat(result[1].locked).isFalse()
        assertThat(result[2].locked).isTrue()
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
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any(), any())).thenReturn(10L)
        // solveCount 1 → 레벨업 조건 불충족
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(1)

        val result = service.submitCode("user1", 1L, "JAVA", "class Main {}")

        assertThat(result.passed).isEqualTo(true)
        verify(codingSubmissionPort).save(eq("user1"), eq(1L), eq("JAVA"), any(), eq(true), any(), any())
    }

    @Test
    fun `submitCode - 하나라도 실패 시 passed=false 반환`() {
        whenever(codingProblemPort.findById(1L)).thenReturn(sampleProblem)
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "wrong", status = "Wrong Answer", passed = false))
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any(), any())).thenReturn(11L)

        val result = service.submitCode("user1", 1L, "JAVA", "class Main {}")

        assertThat(result.passed).isEqualTo(false)
        verify(codingSubmissionPort).save(eq("user1"), eq(1L), eq("JAVA"), any(), eq(false), any(), any())
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

    // ===== getRank 테스트 =====

    @Test
    fun `getRank - 통과 기록이 없으면 totalScore=0, tier=아이언 반환`() {
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(emptyList())

        val result = service.getRank("user1")

        assertThat(result.totalScore).isEqualTo(0)
        assertThat(result.tier).isEqualTo("아이언")
        assertThat(result.easyCount).isEqualTo(0)
        assertThat(result.mediumCount).isEqualTo(0)
        assertThat(result.hardCount).isEqualTo(0)
        assertThat(result.currentStreak).isEqualTo(0)
    }

    @Test
    fun `getRank - EASY 1문제 통과 시 기본 점수 10점`() {
        val today = LocalDate.now()
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(
            listOf(CodingPassRecord(problemId = 1L, difficulty = "EASY", passedDate = today))
        )

        val result = service.getRank("user1")

        // EASY 10점 + 일일 보너스 5점 + 스트릭 1일 × 2점 = 17점
        assertThat(result.totalScore).isEqualTo(17)
        assertThat(result.easyCount).isEqualTo(1)
        assertThat(result.tier).isEqualTo("아이언")
    }

    @Test
    fun `getRank - 같은 문제 여러 번 통과해도 1회만 계산`() {
        val today = LocalDate.now()
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(
            listOf(
                CodingPassRecord(problemId = 1L, difficulty = "EASY", passedDate = today),
                CodingPassRecord(problemId = 1L, difficulty = "EASY", passedDate = today)
            )
        )

        val result = service.getRank("user1")

        assertThat(result.easyCount).isEqualTo(1)
    }

    @Test
    fun `getRank - 100점 이상이면 브론즈 티어`() {
        val today = LocalDate.now()
        // MEDIUM 4문제(고유) = 4 × 25 = 100점 + 일일 보너스 5점 + 스트릭 1일 × 2점 = 107점
        val records = (1L..4L).map {
            CodingPassRecord(problemId = it, difficulty = "MEDIUM", passedDate = today)
        }
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(records)

        val result = service.getRank("user1")

        assertThat(result.tier).isEqualTo("브론즈")
        assertThat(result.mediumCount).isEqualTo(4)
    }

    @Test
    fun `getRank - 연속 2일 풀이 시 스트릭=2, 보너스 4점 추가`() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(
            listOf(
                CodingPassRecord(problemId = 1L, difficulty = "EASY", passedDate = yesterday),
                CodingPassRecord(problemId = 2L, difficulty = "EASY", passedDate = today)
            )
        )

        val result = service.getRank("user1")

        assertThat(result.currentStreak).isEqualTo(2)
        // EASY 2문제 × 10 = 20점 + 일일 보너스 2일 × 5 = 10점 + 스트릭 2 × 2 = 4점 = 34점
        assertThat(result.totalScore).isEqualTo(34)
    }

    @Test
    fun `getRank - 오늘 풀이가 없고 어제까지 연속 풀었으면 스트릭 유지`() {
        val yesterday = LocalDate.now().minusDays(1)
        val dayBefore = LocalDate.now().minusDays(2)
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(
            listOf(
                CodingPassRecord(problemId = 1L, difficulty = "EASY", passedDate = dayBefore),
                CodingPassRecord(problemId = 2L, difficulty = "EASY", passedDate = yesterday)
            )
        )

        val result = service.getRank("user1")

        assertThat(result.currentStreak).isEqualTo(2)
    }

    @Test
    fun `getRank - nextTier와 nextTierScore가 올바르게 반환`() {
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(emptyList())

        val result = service.getRank("user1")

        assertThat(result.nextTier).isEqualTo("브론즈")
        assertThat(result.nextTierScore).isEqualTo(100)
    }

    @Test
    fun `getRank - 챌린저 티어는 nextTier가 null`() {
        val today = LocalDate.now()
        // HARD 80문제(고유) = 80 × 50 = 4000점 이상 → 챌린저
        val records = (1L..80L).map {
            CodingPassRecord(problemId = it, difficulty = "HARD", passedDate = today)
        }
        whenever(codingRankPort.findPassedRecords("user1")).thenReturn(records)

        val result = service.getRank("user1")

        assertThat(result.tier).isEqualTo("챌린저")
        assertThat(result.nextTier).isNull()
        assertThat(result.nextTierScore).isNull()
    }

    @Test
    fun `submitCode - solve_count가 3의 배수가 되면 레벨 업`() {
        whenever(codingProblemPort.findById(1L)).thenReturn(sampleProblem)
        whenever(judge0Port.execute(any(), eq(62), eq("5"), eq("25"))).thenReturn(Judge0Result(stdout = "25", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("3"), eq("9"))).thenReturn(Judge0Result(stdout = "9", status = "Accepted", passed = true))
        whenever(judge0Port.execute(any(), eq(62), eq("0"), eq("0"))).thenReturn(Judge0Result(stdout = "0", status = "Accepted", passed = true))
        whenever(codingSubmissionPort.save(any(), any(), any(), any(), any(), any(), any())).thenReturn(12L)
        // solveCount가 2이면 incrementSolveCount 후 3 → 레벨업
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(2)

        service.submitCode("user1", 1L, "JAVA", "class Main {}")

        verify(userCodingLevelPort).incrementSolveCount("user1")
        verify(userCodingLevelPort).incrementLevel("user1")
    }
}
