package com.devquest.core.api.controller.v1

import com.devquest.core.api.controller.ApiControllerAdvice
import com.devquest.core.domain.ProgressService
import com.devquest.core.domain.model.ProgressResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
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
            ProgressResult(
                userId = "user-1",
                totalXp = 1500,
                level = 4,
                completedQuests = listOf("1-2", "2-1"),
                questDetails = emptyMap()
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
            ProgressResult(
                userId = "unknown",
                totalXp = 0,
                level = 1,
                completedQuests = emptyList(),
                questDetails = emptyMap()
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

    @Test
    fun `completeQuest - 정상 요청이면 200과 SUCCESS 반환`() {
        mockMvc.post("/api/v1/progress/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"user-1","questId":"1-1","actId":1,"earnedXp":100}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.result") { value("SUCCESS") }
        }

        verify(progressService).completeQuest("user-1", "1-1", 1, 100)
    }

    @Test
    fun `completeQuest - userId 누락이면 400 반환`() {
        mockMvc.post("/api/v1/progress/complete") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"userId":"","questId":"1-1","actId":1,"earnedXp":100}"""
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
