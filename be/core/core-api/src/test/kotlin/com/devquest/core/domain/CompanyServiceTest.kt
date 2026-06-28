package com.devquest.core.domain

import com.devquest.core.domain.model.AppliedCompany
import com.devquest.core.domain.model.ApplicationStatus
import com.devquest.core.domain.port.CompanyPort
import com.devquest.core.support.error.CoreException
import com.devquest.core.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CompanyServiceTest {

    @Mock
    private lateinit var companyPort: CompanyPort

    @InjectMocks
    private lateinit var service: CompanyService

    @Test
    fun `회사 생성 시 저장된 결과를 반환한다`() {
        val saved = company(id = 1L, companyName = "카카오", position = "백엔드")
        whenever(companyPort.save(any())).thenReturn(saved)

        val result = service.createCompany("user-1", "카카오", "백엔드", null)

        assertThat(result.companyName).isEqualTo("카카오")
        assertThat(result.userId).isEqualTo("user-1")
        verify(companyPort).save(any())
    }

    @Test
    fun `회사 목록 조회 시 userId에 해당하는 회사 목록을 반환한다`() {
        val companies = listOf(
            company(id = 1L, companyName = "카카오"),
            company(id = 2L, companyName = "네이버"),
        )
        whenever(companyPort.findAllByUserId("user-1")).thenReturn(companies)

        val result = service.getCompanies("user-1")

        assertThat(result).hasSize(2)
        assertThat(result.map { it.companyName }).containsExactlyInAnyOrder("카카오", "네이버")
    }

    @Test
    fun `상태 변경 시 변경된 상태의 회사를 반환한다`() {
        val existing = company(id = 1L, status = ApplicationStatus.INTERESTED)
        val updated = existing.copy(status = ApplicationStatus.APPLIED, appliedAt = LocalDateTime.now())
        whenever(companyPort.findByIdAndUserId(1L, "user-1")).thenReturn(existing)
        whenever(companyPort.save(any())).thenReturn(updated)

        val result = service.updateStatus("user-1", 1L, ApplicationStatus.APPLIED, null)

        assertThat(result.status).isEqualTo(ApplicationStatus.APPLIED)
    }

    @Test
    fun `존재하지 않는 회사 상태 변경 시 COMPANY_NOT_FOUND 예외가 발생한다`() {
        whenever(companyPort.findByIdAndUserId(999L, "user-1")).thenReturn(null)

        assertThatThrownBy { service.updateStatus("user-1", 999L, ApplicationStatus.APPLIED, null) }
            .isInstanceOf(CoreException::class.java)
            .satisfies({ ex ->
                assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.COMPANY_NOT_FOUND)
            })

        verify(companyPort, never()).save(any())
    }

    @Test
    fun `회사 삭제 시 companyPort delete가 호출된다`() {
        val existing = company(id = 1L)
        whenever(companyPort.findByIdAndUserId(1L, "user-1")).thenReturn(existing)

        service.deleteCompany("user-1", 1L)

        verify(companyPort).delete(1L, "user-1")
    }

    @Test
    fun `존재하지 않는 회사 삭제 시 COMPANY_NOT_FOUND 예외가 발생한다`() {
        whenever(companyPort.findByIdAndUserId(999L, "user-1")).thenReturn(null)

        assertThatThrownBy { service.deleteCompany("user-1", 999L) }
            .isInstanceOf(CoreException::class.java)
            .satisfies({ ex ->
                assertThat((ex as CoreException).errorType).isEqualTo(ErrorType.COMPANY_NOT_FOUND)
            })

        verify(companyPort, never()).delete(any(), any())
    }

    @Test
    fun `회사 생성 시 userId가 올바르게 저장된다`() {
        val saved = company(id = 1L, userId = "user-42")
        whenever(companyPort.save(any())).thenReturn(saved)

        val result = service.createCompany("user-42", "삼성", "iOS", "https://job.samsung.com")

        assertThat(result.userId).isEqualTo("user-42")
    }

    private fun company(
        id: Long = 0L,
        userId: String = "user-1",
        companyName: String = "카카오",
        position: String = "백엔드",
        status: ApplicationStatus = ApplicationStatus.INTERESTED,
    ) = AppliedCompany(
        id = id,
        userId = userId,
        companyName = companyName,
        position = position,
        status = status,
    )
}
