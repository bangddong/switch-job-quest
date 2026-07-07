package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.ActivityType
import com.devquest.core.domain.model.CompanyActivity
import com.devquest.storage.db.core.CompanyActivityEntity
import com.devquest.storage.db.core.CompanyActivityRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class CompanyActivityAdapterTest {

    @Mock
    private lateinit var repository: CompanyActivityRepository

    @InjectMocks
    private lateinit var adapter: CompanyActivityAdapter

    @Test
    fun `save - 활동을 저장하고 도메인으로 반환한다`() {
        val activity = CompanyActivity(
            companyId = 1L,
            userId = "user-1",
            activityType = ActivityType.JD_ANALYSIS,
            aiScore = 80,
            aiResultJson = "{}",
        )
        val savedEntity = CompanyActivityEntity(
            companyId = 1L,
            userId = "user-1",
            activityType = ActivityType.JD_ANALYSIS,
            aiScore = 80,
            aiResultJson = "{}",
        )
        whenever(repository.save(any())).thenReturn(savedEntity)

        val result = adapter.save(activity)

        assertThat(result.companyId).isEqualTo(1L)
        assertThat(result.userId).isEqualTo("user-1")
        assertThat(result.activityType).isEqualTo(ActivityType.JD_ANALYSIS)
        assertThat(result.aiScore).isEqualTo(80)
    }

    @Test
    fun `findLatestByCompanyIdAndType - 존재하면 도메인으로 반환한다`() {
        val entity = CompanyActivityEntity(
            companyId = 1L,
            userId = "user-1",
            activityType = ActivityType.JD_ANALYSIS,
            aiScore = 75,
            aiResultJson = """{"companyName":"카카오"}""",
        )
        whenever(
            repository.findTopByCompanyIdAndActivityTypeOrderByCreatedAtDesc(1L, ActivityType.JD_ANALYSIS)
        ).thenReturn(entity)

        val result = adapter.findLatestByCompanyIdAndType(1L, ActivityType.JD_ANALYSIS)

        assertThat(result).isNotNull()
        assertThat(result!!.companyId).isEqualTo(1L)
        assertThat(result.aiScore).isEqualTo(75)
    }

    @Test
    fun `findLatestByCompanyIdAndType - 존재하지 않으면 null을 반환한다`() {
        whenever(
            repository.findTopByCompanyIdAndActivityTypeOrderByCreatedAtDesc(999L, ActivityType.JD_ANALYSIS)
        ).thenReturn(null)

        val result = adapter.findLatestByCompanyIdAndType(999L, ActivityType.JD_ANALYSIS)

        assertThat(result).isNull()
    }

    @Test
    fun `findAllByCompanyId - 최신순으로 도메인 목록을 반환한다`() {
        val entities = listOf(
            CompanyActivityEntity(companyId = 1L, userId = "user-1", activityType = ActivityType.RESUME_CHECK, aiScore = 90, aiResultJson = "{}"),
            CompanyActivityEntity(companyId = 1L, userId = "user-1", activityType = ActivityType.JD_ANALYSIS, aiScore = 80, aiResultJson = "{}"),
        )
        whenever(repository.findAllByCompanyIdOrderByCreatedAtDesc(1L)).thenReturn(entities)

        val result = adapter.findAllByCompanyId(1L)

        assertThat(result).hasSize(2)
        assertThat(result[0].activityType).isEqualTo(ActivityType.RESUME_CHECK)
    }

    @Test
    fun `findAllByCompanyId - 활동이 없으면 빈 목록을 반환한다`() {
        whenever(repository.findAllByCompanyIdOrderByCreatedAtDesc(999L)).thenReturn(emptyList())

        val result = adapter.findAllByCompanyId(999L)

        assertThat(result).isEmpty()
    }
}
