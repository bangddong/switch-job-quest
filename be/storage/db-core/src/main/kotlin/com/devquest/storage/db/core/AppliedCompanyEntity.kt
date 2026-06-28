package com.devquest.storage.db.core

import com.devquest.core.domain.model.ApplicationStatus
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "applied_company")
class AppliedCompanyEntity(
    @Column(nullable = false)
    val userId: String = "",

    @Column(nullable = false)
    val companyName: String = "",

    @Column(nullable = false)
    var position: String = "",

    @Column(length = 2048)
    var jdUrl: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    var status: ApplicationStatus = ApplicationStatus.INTERESTED,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null,

    var appliedAt: LocalDateTime? = null,
) : BaseEntity()
