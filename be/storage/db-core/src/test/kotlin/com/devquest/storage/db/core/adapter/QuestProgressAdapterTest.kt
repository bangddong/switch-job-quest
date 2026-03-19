package com.devquest.storage.db.core.adapter

import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.enums.QuestStatus
import com.devquest.storage.db.core.QuestProgressEntity
import com.devquest.storage.db.core.QuestProgressRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.util.Optional

@ExtendWith(MockitoExtension::class)
class QuestProgressAdapterTest {

    @Mock
    private lateinit var repository: QuestProgressRepository

    @InjectMocks
    private lateinit var adapter: QuestProgressAdapter

    @Test
    fun `findByUserIdAndQuestId - 존재하지 않으면 null 반환`() {
        whenever(repository.findByUserIdAndQuestId("없는유저", "1-1")).thenReturn(null)

        val result = adapter.findByUserIdAndQuestId("없는유저", "1-1")

        assertThat(result).isNull()
    }

    @Test
    fun `findByUserIdAndQuestId - 존재하면 도메인으로 매핑하여 반환`() {
        val entity = QuestProgressEntity(
            userId = "user-1",
            questId = "1-1",
            actId = 1,
            status = QuestStatus.COMPLETED,
            aiScore = 90,
            earnedXp = 500
        )
        whenever(repository.findByUserIdAndQuestId("user-1", "1-1")).thenReturn(entity)

        val result = adapter.findByUserIdAndQuestId("user-1", "1-1")

        assertThat(result).isNotNull()
        assertThat(result!!.userId).isEqualTo("user-1")
        assertThat(result.questId).isEqualTo("1-1")
        assertThat(result.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(result.aiScore).isEqualTo(90)
        assertThat(result.earnedXp).isEqualTo(500)
    }

    @Test
    fun `findAllByUserId - 해당 유저의 모든 progress 반환`() {
        val entities = listOf(
            QuestProgressEntity(userId = "user-1", questId = "1-1", actId = 1),
            QuestProgressEntity(userId = "user-1", questId = "1-2", actId = 1)
        )
        whenever(repository.findAllByUserId("user-1")).thenReturn(entities)

        val result = adapter.findAllByUserId("user-1")

        assertThat(result).hasSize(2)
        assertThat(result.map { it.questId }).containsExactlyInAnyOrder("1-1", "1-2")
    }

    @Test
    fun `save - id가 없으면 새 엔티티로 저장하고 도메인 반환`() {
        val progress = QuestProgress(
            userId = "user-1",
            questId = "1-2",
            actId = 1,
            status = QuestStatus.NOT_STARTED
        )
        val savedEntity = QuestProgressEntity(
            userId = "user-1",
            questId = "1-2",
            actId = 1,
            status = QuestStatus.NOT_STARTED
        )
        whenever(repository.save(any())).thenReturn(savedEntity)

        val result = adapter.save(progress)

        assertThat(result.userId).isEqualTo("user-1")
        assertThat(result.questId).isEqualTo("1-2")
        assertThat(result.status).isEqualTo(QuestStatus.NOT_STARTED)
    }

    @Test
    fun `save - 기존 id로 저장 시 기존 엔티티의 필드가 업데이트된다`() {
        val existingEntity = QuestProgressEntity(
            userId = "user-2",
            questId = "2-1",
            actId = 2,
            status = QuestStatus.NOT_STARTED,
            aiScore = 0,
            earnedXp = 0
        )
        val progress = QuestProgress(
            id = 1L,
            userId = "user-2",
            questId = "2-1",
            actId = 2,
            status = QuestStatus.COMPLETED,
            aiScore = 85,
            earnedXp = 500
        )
        whenever(repository.findById(1L)).thenReturn(Optional.of(existingEntity))
        whenever(repository.save(any())).thenAnswer { it.arguments[0] as QuestProgressEntity }

        val result = adapter.save(progress)

        assertThat(result.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(result.aiScore).isEqualTo(85)
        assertThat(result.earnedXp).isEqualTo(500)
    }
}
