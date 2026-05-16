package com.devquest.core.domain

import com.devquest.core.domain.port.UserEmailPort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class UserEmailServiceTest {

    @Mock lateinit var userEmailPort: UserEmailPort

    @InjectMocks
    private lateinit var service: UserEmailService

    @Test
    fun `saveEmail - 포트에 저장을 위임한다`() {
        service.saveEmail("user1", "test@example.com")
        verify(userEmailPort).save("user1", "test@example.com")
    }

    @Test
    fun `getEmail - 이메일이 존재하면 반환한다`() {
        whenever(userEmailPort.findByUserId("user1")).thenReturn("test@example.com")
        val email = service.getEmail("user1")
        assertThat(email).isEqualTo("test@example.com")
    }

    @Test
    fun `getEmail - 이메일이 없으면 null을 반환한다`() {
        whenever(userEmailPort.findByUserId("user-none")).thenReturn(null)
        val email = service.getEmail("user-none")
        assertThat(email).isNull()
    }
}
