package com.devquest.core.domain

import com.devquest.core.domain.model.QuestProgress
import com.devquest.core.domain.port.QuestProgressPort
import com.devquest.core.enums.QuestStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
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

        assertThat(result["totalXp"]).isEqualTo(1060)
    }

    @Test
    fun `레벨은 totalXp 500 당 1씩 오른다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED, earnedXp = 1500),
        ))

        val result = service.getProgress("user-1")

        // level = 1500 / 500 + 1 = 4
        assertThat(result["level"]).isEqualTo(4)
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

        @Suppress("UNCHECKED_CAST")
        val completedQuests = result["completedQuests"] as List<String>
        assertThat(completedQuests).containsExactlyInAnyOrder("1-2", "2-1")
        assertThat(completedQuests).doesNotContain("2-2", "3-1")
    }

    @Test
    fun `진행 내역 없으면 totalXp=0, level=1, completedQuests 빈 목록 반환`() {
        whenever(progressPort.findAllByUserId("new-user")).thenReturn(emptyList())

        val result = service.getProgress("new-user")

        assertThat(result["totalXp"]).isEqualTo(0)
        assertThat(result["level"]).isEqualTo(1)
        @Suppress("UNCHECKED_CAST")
        assertThat(result["completedQuests"] as List<*>).isEmpty()
        assertThat(result["userId"]).isEqualTo("new-user")
    }

    @Test
    fun `questDetails에 모든 퀘스트의 상태, 점수, XP가 포함된다`() {
        whenever(progressPort.findAllByUserId("user-1")).thenReturn(listOf(
            progress("1-2", QuestStatus.COMPLETED, aiScore = 80, earnedXp = 160),
        ))

        val result = service.getProgress("user-1")

        @Suppress("UNCHECKED_CAST")
        val details = result["questDetails"] as Map<String, Map<String, Any>>
        assertThat(details["1-2"]!!["status"]).isEqualTo(QuestStatus.COMPLETED)
        assertThat(details["1-2"]!!["score"]).isEqualTo(80)
        assertThat(details["1-2"]!!["xp"]).isEqualTo(160)
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
