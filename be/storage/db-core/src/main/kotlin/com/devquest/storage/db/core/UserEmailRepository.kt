package com.devquest.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository

interface UserEmailRepository : JpaRepository<UserEmailEntity, String>
