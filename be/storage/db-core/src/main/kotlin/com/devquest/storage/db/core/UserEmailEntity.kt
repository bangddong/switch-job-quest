package com.devquest.storage.db.core

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "user_email")
class UserEmailEntity(
    @Id
    @Column(name = "user_id", nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    var email: String = "",

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
