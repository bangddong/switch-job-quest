package com.devquest.core.domain

import com.devquest.core.domain.model.coding.CodingHint
import com.devquest.core.domain.model.coding.CodingProblem
import com.devquest.core.domain.model.coding.CodingSubmissionResult
import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.domain.port.CodingHintPort
import com.devquest.core.domain.port.CodingProblemGeneratorPort
import com.devquest.core.domain.port.CodingProblemPort
import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.Judge0Port
import com.devquest.core.domain.port.UserCodingLevelPort
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
    private val codingHintPort: CodingHintPort
) {
    private val objectMapper = com.fasterxml.jackson.databind.ObjectMapper()
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val JAVA_LANGUAGE_ID = 62
        private const val KOTLIN_LANGUAGE_ID = 78
        private const val MAX_RETRY = 2
        private const val LEVEL_UP_INTERVAL = 3

        fun difficultyForLevel(level: Int): String = when {
            level <= 3 -> "EASY"
            level <= 7 -> "MEDIUM"
            else -> "HARD"
        }
    }

    @Transactional
    fun generateProblem(userId: String, language: String): CodingProblem {
        require(language in listOf("JAVA", "KOTLIN")) { "지원하지 않는 언어입니다: $language" }
        val level = userCodingLevelPort.getLevel(userId)
        val difficulty = difficultyForLevel(level)

        val existing = codingProblemPort.findByDifficultyAndLanguage(difficulty, language)
        if (existing.isNotEmpty()) {
            return existing.random()
        }

        return generateAndVerify(difficulty, language)
    }

    private fun generateAndVerify(difficulty: String, language: String): CodingProblem {
        val languageId = if (language == "KOTLIN") KOTLIN_LANGUAGE_ID else JAVA_LANGUAGE_ID

        repeat(MAX_RETRY) { attempt ->
            try {
                val generated = codingProblemGeneratorPort.generate(difficulty, language)
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
                        solutionCode = generated.solutionCode,
                        testCases = generated.testCases
                    )
                    return codingProblemPort.save(problem)
                }
                log.warn("AI 생성 문제 검증 실패 (시도 ${attempt + 1}/$MAX_RETRY): difficulty=$difficulty, language=$language")
            } catch (e: Exception) {
                log.error("문제 생성 중 오류 (시도 ${attempt + 1}/$MAX_RETRY): ${e.message}", e)
            }
        }

        // fallback: EASY 문제 조회
        log.warn("모든 재시도 실패, EASY 문제로 fallback: language=$language")
        val fallback = codingProblemPort.findByDifficultyAndLanguage("EASY", language)
        if (fallback.isNotEmpty()) return fallback.random()

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
        codingSubmissionPort.save(userId, problemId, language, userCode, passed, judgeResultJson)

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

    fun getHint(problemId: Long, title: String, description: String, hintLevel: Int): CodingHint {
        require(hintLevel in 1..3) { "hintLevel은 1~3 사이여야 합니다: ${hintLevel}" }
        log.info("코딩 힌트 요청: problemId=$problemId, hintLevel=${hintLevel}")
        return codingHintPort.getHint(problemId, title, description, hintLevel)
    }
}
