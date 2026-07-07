package com.devquest.storage.db.core

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "user_resume")
class UserResumeEntity(
    @Column(nullable = false, unique = true)
    val userId: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var content: String = "",
) : BaseEntity()
