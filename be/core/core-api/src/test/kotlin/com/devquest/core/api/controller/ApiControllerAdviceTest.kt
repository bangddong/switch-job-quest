package com.devquest.core.api.controller

import com.devquest.core.domain.support.AiEvaluationException
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

class ApiControllerAdviceTest {

    private lateinit var mockMvc: MockMvc

    // 각 예외 케이스를 발생시키는 테스트용 컨트롤러
    @RestController
    class TestController {

        data class Payload(@field:NotBlank val name: String = "")

        @GetMapping("/test/core-exception")
        fun throwCoreException(): String = throw CoreException(ErrorType.AI_EVALUATION_FAILED)

        @GetMapping("/test/ai-evaluation-exception")
        fun throwAiEvaluationException(): String = throw AiEvaluationException("AI 파싱 실패")

        @GetMapping("/test/default-exception")
        fun throwDefaultException(): String = throw RuntimeException("알 수 없는 오류")

        @PostMapping("/test/validation", consumes = ["application/json"])
        fun validateBody(@Valid @RequestBody body: Payload): String = body.name

        @PostMapping("/test/parse", consumes = ["application/json"])
        fun parseBody(@RequestBody body: Payload): String = body.name
    }

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(TestController())
            .setControllerAdvice(ApiControllerAdvice())
            .build()
    }

    @Test
    fun `CoreException - ErrorType에 해당하는 상태코드와 코드 반환`() {
        mockMvc.get("/test/core-exception")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.result") { value("ERROR") }
                jsonPath("$.error.code") { value("AI_EVALUATION_FAILED") }
                jsonPath("$.error.message") { value("AI 평가 중 오류가 발생했습니다") }
            }
    }

    @Test
    fun `AiEvaluationException - 500 AI_EVALUATION_FAILED 반환`() {
        mockMvc.get("/test/ai-evaluation-exception")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.result") { value("ERROR") }
                jsonPath("$.error.code") { value("AI_EVALUATION_FAILED") }
            }
    }

    @Test
    fun `일반 Exception - 500 DEFAULT 반환`() {
        mockMvc.get("/test/default-exception")
            .andExpect {
                status { isInternalServerError() }
                jsonPath("$.result") { value("ERROR") }
                jsonPath("$.error.code") { value("DEFAULT") }
            }
    }

    @Test
    fun `MethodArgumentNotValidException - 400 INVALID_REQUEST 반환`() {
        mockMvc.post("/test/validation") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":""}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }

    @Test
    fun `HttpMessageNotReadableException - 400 INVALID_REQUEST 반환`() {
        mockMvc.post("/test/parse") {
            contentType = MediaType.APPLICATION_JSON
            content = "invalid-json-!!!"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.result") { value("ERROR") }
            jsonPath("$.error.code") { value("INVALID_REQUEST") }
        }
    }
}
