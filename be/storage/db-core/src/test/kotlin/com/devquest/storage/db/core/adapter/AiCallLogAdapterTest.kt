package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.AiCallLog
import com.devquest.storage.db.core.AiCallLogEntity
import com.devquest.storage.db.core.AiCallLogRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class AiCallLogAdapterTest {

    @Mock
    private lateinit var repository: AiCallLogRepository

    @InjectMocks
    private lateinit var adapter: AiCallLogAdapter

    @Test
    fun `record 호출 시 repository에 저장된다`() {
        val log = AiCallLog(
            evaluatorName = "CareerEssayEvaluator",
            modelName = "claude-3-5-sonnet",
            inputTokens = 100,
            outputTokens = 50,
            cacheReadTokens = 20,
            cacheCreationTokens = 0,
            latencyMs = 1234L,
            success = true,
        )
        val savedEntity = AiCallLogEntity(
            evaluatorName = "CareerEssayEvaluator",
            modelName = "claude-3-5-sonnet",
            inputTokens = 100,
            outputTokens = 50,
            cacheReadTokens = 20,
            cacheCreationTokens = 0,
            latencyMs = 1234L,
            success = true,
        )
        whenever(repository.save(any())).thenReturn(savedEntity)

        adapter.record(log)

        val captor = argumentCaptor<AiCallLogEntity>()
        verify(repository).save(captor.capture())
        assertThat(captor.firstValue.evaluatorName).isEqualTo("CareerEssayEvaluator")
        assertThat(captor.firstValue.inputTokens).isEqualTo(100)
        assertThat(captor.firstValue.latencyMs).isEqualTo(1234L)
        assertThat(captor.firstValue.success).isTrue()
    }
}
