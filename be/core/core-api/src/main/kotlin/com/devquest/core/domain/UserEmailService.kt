package com.devquest.core.domain

import com.devquest.core.domain.port.UserEmailPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UserEmailService(
    private val userEmailPort: UserEmailPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun saveEmail(userId: String, email: String) {
        userEmailPort.save(userId, email)
        log.info("이메일 저장: userId=$userId")
    }

    fun getEmail(userId: String): String? {
        return userEmailPort.findByUserId(userId)
    }
}
