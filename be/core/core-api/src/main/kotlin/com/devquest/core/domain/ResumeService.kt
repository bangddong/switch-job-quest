package com.devquest.core.domain

import com.devquest.core.domain.model.UserResume
import com.devquest.core.domain.port.UserResumePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ResumeService(
    private val userResumePort: UserResumePort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getResume(userId: String): UserResume? {
        return userResumePort.findByUserId(userId)
    }

    @Transactional
    fun saveResume(userId: String, content: String): UserResume {
        val resume = UserResume(userId = userId, content = content)
        val saved = userResumePort.save(resume)
        log.info("이력서 저장: userId=${userId}")
        return saved
    }
}
