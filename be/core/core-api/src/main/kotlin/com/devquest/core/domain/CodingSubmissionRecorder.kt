package com.devquest.core.domain

import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.UserCodingLevelPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 코드 제출의 DB 쓰기 묶음(제출 저장 + solveCount 증가 + 레벨업)을 하나의 트랜잭션으로 묶는다 (B7-27).
 *
 * [CodingQuestService.submitCode] 는 Judge0 실행 동안 DB 커넥션을 쥐지 않도록 `@Transactional` 을
 * 걷어냈고, 그 대신 Judge0 실행이 모두 끝난 뒤 이 빈의 [record] 를 한 번 호출한다. 같은 클래스의
 * private 메서드가 아니라 **별도 빈**이어야 Spring AOP 프록시를 거친다(self-invocation 함정).
 * [QuestProgressRecorder] 와 같은 패턴이다.
 */
@Component
class CodingSubmissionRecorder(
    private val codingSubmissionPort: CodingSubmissionPort,
    private val userCodingLevelPort: UserCodingLevelPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val LEVEL_UP_INTERVAL = 3
    }

    @Transactional
    fun record(
        userId: String,
        problemId: Long,
        language: String,
        userCode: String,
        passed: Boolean,
        judgeResultJson: String,
        category: String,
    ) {
        codingSubmissionPort.save(userId, problemId, language, userCode, passed, judgeResultJson, category)

        if (passed) {
            val currentSolveCount = userCodingLevelPort.getSolveCount(userId)
            userCodingLevelPort.incrementSolveCount(userId)
            if ((currentSolveCount + 1) % LEVEL_UP_INTERVAL == 0) {
                userCodingLevelPort.incrementLevel(userId)
                log.info("레벨 업: userId=$userId, solveCount=${currentSolveCount + 1}")
            }
        }
    }
}
