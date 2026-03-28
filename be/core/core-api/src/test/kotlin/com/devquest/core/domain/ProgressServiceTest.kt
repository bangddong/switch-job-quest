package com.devquest.core.domain

import com.devquest.core.domain.model.ProgressResult
import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.core.enums.QuestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class ProgressServiceTest {

    @Mock
    private lateinit var progressPort: QuestProgressPort

    @InjectMocks
    private lateinit var service: ProgressService

    @Test
    fun `완료된 퀘스트 XP만 합산한다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED, earnedXp = 160),
            progress("2-1", QuestStatus.COMPLETED, earnedXp = 900),
            progress("2-2", QuestStatus.AI_FAILED, earnedXp = 0),
        ))

        val result = service.getProgress("user-1")

        assertThat(result.totalXp).isEqualTo(1060)
    }

    @Test
    fun `레벨은 totalXp 500 당 1씩 오른다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED, earnedXp = 1500),
        ))

        val result = service.getProgress("user-1")

        // level = 1500 / 500 + 1 = 4
        assertThat(result.level).isEqualTo(4)
    }

    @Test
    fun `completedQuests는 COMPLETED 상태 questId만 포함한다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED),
            progress("2-1", QuestStatus.COMPLETED),
            progress("2-2", QuestStatus.AI_FAILED),
            progress("3-1", QuestStatus.NOT_STARTED),
        ))

        val result = service.getProgress("user-1")

        assertThat(result.completedQuests).containsExactlyInAnyOrder("1-2", "2-1")
        assertThat(result.completedQuests).doesNotContain("2-2", "3-1")
    }

    @Test
    fun `진행 내역 없으면 totalXp=0, level=1, completedQuests 빈 목록 반환`() {
        whenever(progressPort.findAllByUserId("new-user")).thenReturn(emptyList())

        val result = service.getProgress("new-user")

        assertThat(result.totalXp).isEqualTo(0)
        assertThat(result.level).isEqualTo(1)
        assertThat(result.completedQuests).isEmpty()
        assertThat(result.userId).isEqualTo("new-user")
    }

    @Test
    fun `questDetails에 모든 퀘스트의 상태, 점수, XP가 포함된다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED, aiScore = 80, earnedXp = 160),
        ))

        val result = service.getProgress("user-1")

        val detail = result.questDetails["1-2"]!!
        assertThat(detail.status).isEqualTo(QuestStatus.COMPLETED)
        assertThat(detail.score).isEqualTo(80)
        assertThat(detail.xp).isEqualTo(160)
    }

    @Test
    fun `completeQuest - 미완료 퀘스트는 COMPLETED로 저장한다`() {
        whenever(progressPort.findByUserIdAndQuestId("user-1", "1-1")).thenReturn(null)

        service.completeQuest("user-1", "1-1", 1, 100)

        verify(progressPort).save(any())
    }

    @Test
    fun `completeQuest - 이미 완료된 퀘스트는 저장하지 않는다`() {
        whenever(progressPort.findByUserIdAndQuestId("user-1", "1-1")).thenReturn(
            progress("1-1", QuestStatus.COMPLETED, earnedXp = 100)
        )

        service.completeQuest("user-1", "1-1", 1, 100)

        verify(progressPort, never()).save(any())
    }

    private fun progress(
        questId: String,
        status: QuestStatus,
        aiScore: Int = 0,
        earnedXp: Int = 0
    ) = QuestProgress(
        userId = "user-1",
        questId = questId,
        actId = 1,
        status = status,
        aiScore = aiScore,
        earnedXp = earnedXp
    )
}
