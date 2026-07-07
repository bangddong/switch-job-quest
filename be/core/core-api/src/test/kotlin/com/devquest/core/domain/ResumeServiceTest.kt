package com.devquest.core.domain

import com.devquest.core.domain.model.UserResume
import com.devquest.core.domain.port.UserResumePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ResumeServiceTest {

    @Mock
    private lateinit var userResumePort: UserResumePort

    @InjectMocks
    private lateinit var service: ResumeService

    @Test
    fun `이력서 조회 시 등록된 이력서가 있으면 반환한다`() {
        whenever(userResumePort.findByUserId("user-1")).thenReturn(
            UserResume(id = 1L, userId = "user-1", content = "5년차 백엔드 개발자")
        )

        val result = service.getResume("user-1")

        assertThat(result).isNotNull
        assertThat(result?.content).isEqualTo("5년차 백엔드 개발자")
    }

    @Test
    fun `이력서 조회 시 등록된 이력서가 없으면 null을 반환한다`() {
        whenever(userResumePort.findByUserId("user-1")).thenReturn(null)

        val result = service.getResume("user-1")

        assertThat(result).isNull()
    }

    @Test
    fun `이력서 저장 시 저장된 결과를 반환한다`() {
        val saved = UserResume(id = 1L, userId = "user-1", content = "새 이력서 내용")
        whenever(userResumePort.save(any())).thenReturn(saved)

        val result = service.saveResume("user-1", "새 이력서 내용")

        assertThat(result.content).isEqualTo("새 이력서 내용")
        assertThat(result.userId).isEqualTo("user-1")
        verify(userResumePort).save(any())
    }
}
