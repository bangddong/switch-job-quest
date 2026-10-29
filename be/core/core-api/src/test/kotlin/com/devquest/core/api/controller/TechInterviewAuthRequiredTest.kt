package com.devquest.core.api.controller

import com.devquest.core.domain.model.evaluation.TechInterviewResult
import com.devquest.core.domain.port.TechInterviewPort
import com.devquest.core.security.JwtProvider
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 📌D-016 B7-19 1단계 — 기술 면접은 로그인 필수, 한도 키는 인증된 사용자 ID.
 * 실제 SecurityConfig + 실제 인터셉터 + 실제 JWT 필터를 통과시켜 "Security 가 앞에서 막는다"와
 * "로그인 사용자는 사용자 단위로 2회 후 429"를 함께 고정한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TechInterviewAuthRequiredTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtProvider: JwtProvider

    @MockitoBean
    private lateinit var techInterviewPort: TechInterviewPort

    private fun bearer(userId: String) = "Bearer ${jwtProvider.generate(userId)}"

    @Test
    fun `로그인 없이 질문 생성을 호출하면 403 이고 AI 포트는 호출되지 않는다`() {
        mockMvc.perform(get("/api/v1/tech-interview/question").param("techStack", "Java"))
            .andExpect(status().isForbidden)

        org.mockito.kotlin.verifyNoInteractions(techInterviewPort)
    }

    @Test
    fun `로그인 없이 평가를 호출하면 403 이다`() {
        mockMvc.perform(
            post("/api/v1/tech-interview/evaluate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"techStack":"Java","questions":["Q1"],"answers":["A1"]}""")
        ).andExpect(status().isForbidden)

        org.mockito.kotlin.verifyNoInteractions(techInterviewPort)
    }

    @Test
    fun `로그인 사용자는 질문 생성을 2회까지 쓰고 3회째는 429 와 새 문구를 받는다`() {
        whenever(techInterviewPort.generateQuestions("Java")).thenReturn(TechInterviewResult(questions = listOf("Q1")))
        val token = bearer("github-rate-limit-user-1")

        repeat(2) {
            mockMvc.perform(get("/api/v1/tech-interview/question").param("techStack", "Java").header("Authorization", token))
                .andExpect(status().isOk)
        }

        mockMvc.perform(get("/api/v1/tech-interview/question").param("techStack", "Java").header("Authorization", token))
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
            .andExpect(jsonPath("$.error.message").value("오늘 사용 가능한 횟수를 모두 사용했습니다. 내일 다시 시도해주세요."))
    }

    @Test
    fun `다른 로그인 사용자는 앞 사용자가 한도를 다 써도 영향받지 않는다`() {
        whenever(techInterviewPort.generateQuestions("Java")).thenReturn(TechInterviewResult(questions = listOf("Q1")))
        val first = bearer("github-rate-limit-user-2")
        repeat(3) {
            mockMvc.perform(get("/api/v1/tech-interview/question").param("techStack", "Java").header("Authorization", first))
        }

        mockMvc.perform(
            get("/api/v1/tech-interview/question").param("techStack", "Java")
                .header("Authorization", bearer("github-rate-limit-user-3"))
        ).andExpect(status().isOk)
    }
}
