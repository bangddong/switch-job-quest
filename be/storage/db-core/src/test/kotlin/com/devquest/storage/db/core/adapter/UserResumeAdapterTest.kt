package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.UserResume
import com.devquest.storage.db.core.UserResumeEntity
import com.devquest.storage.db.core.UserResumeRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class UserResumeAdapterTest {

    @Mock
    private lateinit var repository: UserResumeRepository

    @InjectMocks
    private lateinit var adapter: UserResumeAdapter

    @Test
    fun `findByUserId - 존재하면 도메인으로 반환한다`() {
        val entity = UserResumeEntity(userId = "user-1", content = "5년차 백엔드 개발자")
        whenever(repository.findByUserId("user-1")).thenReturn(entity)

        val result = adapter.findByUserId("user-1")

        assertThat(result).isNotNull()
        assertThat(result!!.userId).isEqualTo("user-1")
        assertThat(result.content).isEqualTo("5년차 백엔드 개발자")
    }

    @Test
    fun `findByUserId - 존재하지 않으면 null을 반환한다`() {
        whenever(repository.findByUserId("user-1")).thenReturn(null)

        val result = adapter.findByUserId("user-1")

        assertThat(result).isNull()
    }

    @Test
    fun `save - 신규 이력서면 새로 저장한다`() {
        whenever(repository.findByUserId("user-1")).thenReturn(null)
        val savedEntity = UserResumeEntity(userId = "user-1", content = "새 이력서")
        whenever(repository.save(any())).thenReturn(savedEntity)

        val result = adapter.save(UserResume(userId = "user-1", content = "새 이력서"))

        assertThat(result.userId).isEqualTo("user-1")
        assertThat(result.content).isEqualTo("새 이력서")
    }

    @Test
    fun `save - 기존 이력서가 있으면 내용을 갱신한다`() {
        val existing = UserResumeEntity(userId = "user-1", content = "기존 이력서")
        whenever(repository.findByUserId("user-1")).thenReturn(existing)
        whenever(repository.save(any())).thenReturn(existing)

        val result = adapter.save(UserResume(userId = "user-1", content = "수정된 이력서"))

        assertThat(result.content).isEqualTo("수정된 이력서")
    }
}
