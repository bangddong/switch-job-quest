package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.ProgressService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.setup.MockMvcBuilders

@ExtendWith(MockitoExtension::class)
class ProgressControllerTest {

    @Mock
    private lateinit var progressService: ProgressService

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(ProgressController(progressService))
            .setControllerAdvice(ApiControllerAdvice())
            .build()
    }

    @Test
    fun `getProgress - 정상 요청이면 200과 SUCCESS 반환`() {
        whenever(progressService.getProgress("user-1")).thenReturn(
            mapOf(
                "userId" to "user-1",
                "totalXp" to 1500,
                "completedQuests" to listOf("1-2", "2-1"),
                "level" to 4,
                "questDetails" to emptyMap<String, Any>()
            )
        )

        mockMvc.get("/api/v1/progress/user-1")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.userId") { value("user-1") }
                jsonPath("$.data.totalXp") { value(1500) }
                jsonPath("$.data.level") { value(4) }
            }
    }

    @Test
    fun `getProgress - 진행 내역 없는 유저도 200과 빈 결과 반환`() {
        whenever(progressService.getProgress("unknown")).thenReturn(
            mapOf(
                "userId" to "unknown",
                "totalXp" to 0,
                "completedQuests" to emptyList<String>(),
                "level" to 1,
                "questDetails" to emptyMap<String, Any>()
            )
        )

        mockMvc.get("/api/v1/progress/unknown")
            .andExpect {
                status { isOk() }
                jsonPath("$.result") { value("SUCCESS") }
                jsonPath("$.data.totalXp") { value(0) }
                jsonPath("$.data.level") { value(1) }
            }
    }
}
