package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface AiCallLogRepository : JpaRepository<AiCallLogEntity, Long>
