package com.devquest

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ApplicationContextTest {

    @Test
    fun `Spring ApplicationContext loads successfully`() {
        // Context load itself is the assertion.
        // If any bean fails to wire (e.g., missing ObjectMapper bean),
        // this test fails and CI catches it before deployment.
    }
}
