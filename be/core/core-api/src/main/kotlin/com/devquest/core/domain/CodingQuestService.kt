package com.devquest.core.domain

import com.devquest.core.domain.model.coding.CategoryProgressResult
import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.CodingRankResult
import com.devquest.core.domain.model.coding.CodingSubmissionResult
import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.port.CodingHintPort
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.CodingProblemPort
import com.devquest.core.domain.port.CodingRankPort
import com.devquest.core.domain.port.CodingRoadmapProgressPort
import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.UserCodingLevelPort
import java.time.LocalDate
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CodingQuestService(
    private val codingProblemGeneratorPort: CodingProblemGeneratorPort,
    private val codingProblemPort: CodingProblemPort,
    private val userCodingLevelPort: UserCodingLevelPort,
    private val codingSubmissionPort: CodingSubmissionPort,
    private val judge0Port: Judge0Port,
    private val codingHintPort: CodingHintPort,
    private val codingRoadmapProgressPort: CodingRoadmapProgressPort,
    private val codingRankPort: CodingRankPort
) {
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val JAVA_LANGUAGE_ID = 62
        private const val KOTLIN_LANGUAGE_ID = 78
        private const val MAX_RETRY = 2
        private const val LEVEL_UP_INTERVAL = 3
        private const val ROADMAP_UNLOCK_THRESHOLD = 3

        private val TIER_THRESHOLDS = listOf(
            0 to "아이언",
            100 to "브론즈",
            300 to "실버",
            600 to "골드",
            1000 to "플래티넘",
            1500 to "다이아",
            2500 to "마스터",
            4000 to "챌린저"
        )

        fun tierOf(score: Int): String =
            TIER_THRESHOLDS.lastOrNull { score >= it.first }?.second ?: "아이언"

        fun nextTierInfo(score: Int): Pair<String?, Int?> {
            val idx = TIER_THRESHOLDS.indexOfLast { score >= it.first }
            return if (idx < TIER_THRESHOLDS.size - 1) {
                val next = TIER_THRESHOLDS[idx + 1]
                next.second to next.first
            } else {
                null to null
            }
        }

        fun difficultyForLevel(level: Int): String = when {
            level <= 3 -> "EASY"
            level <= 7 -> "MEDIUM"
            else -> "HARD"
        }

        val ROADMAP_CATEGORIES = listOf(
            Triple("ARRAY", "배열/투포인터/슬라이딩윈도우", 1),
            Triple("HASH_MAP", "해시맵/빈도카운팅", 2),
            Triple("STACK_QUEUE", "스택/큐/모노토닉스택", 3),
            Triple("BINARY_SEARCH", "이진탐색", 4),
            Triple("RECURSION", "재귀/백트래킹", 5),
            Triple("TREE", "트리순회/BFS/DFS", 6),
            Triple("GRAPH", "그래프/위상정렬", 7),
            Triple("GREEDY", "그리디", 8),
            Triple("DP", "동적프로그래밍", 9)
        )
    }

    @Transactional
    fun generateProblem(userId: String, language: String, category: String): CodingProblem {
        require(language in listOf("JAVA", "KOTLIN")) { "지원하지 않는 언어입니다: $language" }
        val level = userCodingLevelPort.getLevel(userId)
        val difficulty = difficultyForLevel(level)

        val existing = codingProblemPort.findByCategoryAndLanguage(category, language)
        if (existing.isNotEmpty()) {
            return existing.random()
        }

        return generateAndVerify(difficulty, language, category)
    }

    fun getRoadmapProgress(userId: String): List<CategoryProgressResult> {
        val solvedCounts = ROADMAP_CATEGORIES.associate { (cat, _, _) ->
            cat to codingRoadmapProgressPort.countSolvedByUserAndCategory(userId, cat)
        }

        return ROADMAP_CATEGORIES.mapIndexed { index, (cat, displayName, order) ->
            val locked = if (index == 0) false else {
                val prevCat = ROADMAP_CATEGORIES[index - 1].first
                (solvedCounts[prevCat] ?: 0) < ROADMAP_UNLOCK_THRESHOLD
            }
            CategoryProgressResult(
                category = cat,
                displayName = displayName,
                order = order,
                solvedCount = solvedCounts[cat] ?: 0,
                locked = locked
            )
        }
    }

    private fun generateAndVerify(difficulty: String, language: String, category: String): CodingProblem {
        val languageId = if (language == "KOTLIN") KOTLIN_LANGUAGE_ID else JAVA_LANGUAGE_ID

        repeat(MAX_RETRY) { attempt ->
            try {
                val generated = codingProblemGeneratorPort.generate(difficulty, language, category)
                val allPassed = generated.testCases.all { tc ->
                    val result = judge0Port.execute(generated.solutionCode, languageId, tc.input, tc.expectedOutput)
                    result.passed
                }
                if (allPassed) {
                    val problem = CodingProblem(
                        title = generated.title,
                        description = generated.description,
                        difficulty = difficulty,
                        language = language,
                        category = category,
                        solutionCode = generated.solutionCode,
                        testCases = generated.testCases
                    )
                    return codingProblemPort.save(problem)
                }
                log.warn("AI 생성 문제 검증 실패 (시도 ${attempt + 1}/$MAX_RETRY): difficulty=$difficulty, language=$language, category=$category")
            } catch (e: Exception) {
                log.error("문제 생성 중 오류 (시도 ${attempt + 1}/$MAX_RETRY): ${e.message}", e)
            }
        }

        // fallback: 같은 카테고리 또는 EASY 문제 조회
        log.warn("모든 재시도 실패, fallback: language=$language, category=$category")
        val fallback = codingProblemPort.findByCategoryAndLanguage(category, language)
        if (fallback.isNotEmpty()) return fallback.random()
        val easyFallback = codingProblemPort.findByDifficultyAndLanguage("EASY", language)
        if (easyFallback.isNotEmpty()) return easyFallback.random()

        throw AiEvaluationException("코딩 문제 생성에 실패했습니다. 잠시 후 다시 시도해주세요.")
    }

    @Transactional
    fun submitCode(userId: String, problemId: Long, language: String, userCode: String): CodingSubmissionResult {
        val problem = codingProblemPort.findById(problemId)
            ?: return CodingSubmissionResult(problemId = problemId, passed = false, message = "문제를 찾을 수 없습니다")

        val languageId = if (language == "KOTLIN") KOTLIN_LANGUAGE_ID else JAVA_LANGUAGE_ID

        var lastStdout = ""
        var lastStderr = ""
        val passed = problem.testCases.all { tc ->
            val result = judge0Port.execute(userCode, languageId, tc.input, tc.expectedOutput)
            lastStdout = result.stdout
            lastStderr = result.stderr
            result.passed
        }

        val judgeResultJson = objectMapper.writeValueAsString(
            mapOf("passed" to passed, "stdout" to lastStdout, "stderr" to lastStderr)
        )
        codingSubmissionPort.save(userId, problemId, language, userCode, passed, judgeResultJson, problem.category)

        if (passed) {
            val currentSolveCount = userCodingLevelPort.getSolveCount(userId)
            userCodingLevelPort.incrementSolveCount(userId)
            if ((currentSolveCount + 1) % LEVEL_UP_INTERVAL == 0) {
                userCodingLevelPort.incrementLevel(userId)
                log.info("레벨 업: userId=$userId, solveCount=${currentSolveCount + 1}")
            }
        }

        log.info("코드 제출: userId=$userId, problemId=$problemId, passed=$passed")
        return CodingSubmissionResult(
            problemId = problemId,
            passed = passed,
            stdout = lastStdout,
            stderr = lastStderr,
            message = if (passed) "모든 테스트케이스 통과" else "테스트케이스 실패"
        )
    }

    fun getLevel(userId: String): Int {
        return userCodingLevelPort.getLevel(userId)
    }

    fun getRank(userId: String): CodingRankResult {
        val records = codingRankPort.findPassedRecords(userId)

        // 고유 문제별 첫 통과 날짜로 집계
        val uniqueByProblem = records
            .groupBy { it.problemId }
            .mapValues { (_, list) -> list.minBy { it.passedDate } }
            .values.toList()

        val easyCount = uniqueByProblem.count { it.difficulty == "EASY" }
        val mediumCount = uniqueByProblem.count { it.difficulty == "MEDIUM" }
        val hardCount = uniqueByProblem.count { it.difficulty == "HARD" }

        // 난이도별 기본 점수
        val baseScore = easyCount * 10 + mediumCount * 25 + hardCount * 50

        // 날짜별 그룹화 → 각 날 첫 문제에 +5점 일일 보너스
        val dailyBonus = uniqueByProblem
            .groupBy { it.passedDate }
            .size * 5

        // 연속 스트릭 계산
        val solvedDates = uniqueByProblem.map { it.passedDate }.toSortedSet()
        val currentStreak = calculateStreak(solvedDates)

        val streakBonus = currentStreak * 2
        val totalScore = baseScore + dailyBonus + streakBonus

        val tier = tierOf(totalScore)
        val (nextTier, nextTierScore) = nextTierInfo(totalScore)

        log.info("코딩 랭크 조회: userId=$userId, score=$totalScore, tier=$tier, streak=$currentStreak")
        return CodingRankResult(
            totalScore = totalScore,
            tier = tier,
            nextTier = nextTier,
            nextTierScore = nextTierScore,
            easyCount = easyCount,
            mediumCount = mediumCount,
            hardCount = hardCount,
            currentStreak = currentStreak
        )
    }

    private fun calculateStreak(solvedDates: Set<LocalDate>): Int {
        if (solvedDates.isEmpty()) return 0
        val today = LocalDate.now()
        // 오늘 또는 어제부터 역산
        val startDate = if (solvedDates.contains(today)) today else today.minusDays(1)
        if (!solvedDates.contains(startDate)) return 0

        var streak = 0
        var current = startDate
        while (solvedDates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint {
        require(hintLevel in 1..3) { "hintLevel은 1~3 사이여야 합니다: ${hintLevel}" }
        log.info("코딩 힌트 요청: problemId=$problemId, hintLevel=${hintLevel}")
        return codingHintPort.getHint(problemId, title, description, hintLevel)
    }
}
