package com.devquest.storage.db.core

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "daily_mail_log")
class DailyMailLogEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    @Column(name = "mail_type", nullable = false, length = 50)
    val mailType: String = "",

    @Column(name = "question_content", nullable = false, columnDefinition = "TEXT")
    val questionContent: String = "",

    @Column(name = "sent_at", nullable = false)
    val sentAt: LocalDateTime = LocalDateTime.now(),
)
