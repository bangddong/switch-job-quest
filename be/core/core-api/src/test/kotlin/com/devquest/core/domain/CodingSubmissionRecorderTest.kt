package com.devquest.core.domain

import com.devquest.core.domain.port.CodingSubmissionPort
import com.devquest.core.domain.port.UserCodingLevelPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.transaction.annotation.Transactional

@ExtendWith(MockitoExtension::class)
class CodingSubmissionRecorderTest {

    @Mock lateinit var codingSubmissionPort: CodingSubmissionPort
    @Mock lateinit var userCodingLevelPort: UserCodingLevelPort

    @InjectMocks
    private lateinit var recorder: CodingSubmissionRecorder

    // CodingQuestService 에서 @Transactional 을 걷어낸 대신, 제출 저장 + solveCount + 레벨업의 원자성은
    // 이 별도 빈의 메서드가 맡는다. 프록시를 거치려면 별도 빈이어야 하고 어노테이션이 살아 있어야 한다.
    @Test
    fun `record 는 @Transactional 이 선언돼 있다 - 제출 저장·solveCount·레벨업 원자성 (B7-27)`() {
        val method = CodingSubmissionRecorder::class.java.getMethod(
            "record",
            String::class.java, Long::class.javaPrimitiveType!!, String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType!!, String::class.java, String::class.java
        )

        assertThat(AnnotatedElementUtils.findMergedAnnotation(method, Transactional::class.java))
            .`as`("record() 는 서비스 밖 별도 빈에서 @Transactional 로 DB 쓰기 묶음을 원자적으로 유지해야 한다")
            .isNotNull()
    }

    @Test
    fun `record - 통과하면 제출 저장 후 solveCount 를 증가시킨다`() {
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(1)

        recorder.record("user1", 1L, "JAVA", "class Main {}", true, "{}", "ARRAY")

        verify(codingSubmissionPort).save("user1", 1L, "JAVA", "class Main {}", true, "{}", "ARRAY")
        verify(userCodingLevelPort).incrementSolveCount("user1")
        verify(userCodingLevelPort, never()).incrementLevel(any())
    }

    @Test
    fun `record - solveCount 가 3의 배수가 되면 레벨 업`() {
        // solveCount 가 2 이면 incrementSolveCount 후 3 → 레벨업
        whenever(userCodingLevelPort.getSolveCount("user1")).thenReturn(2)

        recorder.record("user1", 1L, "JAVA", "class Main {}", true, "{}", "ARRAY")

        verify(userCodingLevelPort).incrementSolveCount("user1")
        verify(userCodingLevelPort).incrementLevel("user1")
    }

    @Test
    fun `record - 실패하면 제출만 저장하고 solveCount 는 건드리지 않는다`() {
        recorder.record("user1", 1L, "JAVA", "class Main {}", false, "{}", "ARRAY")

        verify(codingSubmissionPort).save(eq("user1"), eq(1L), eq("JAVA"), any(), eq(false), any(), eq("ARRAY"))
        verifyNoInteractions(userCodingLevelPort)
    }
}
