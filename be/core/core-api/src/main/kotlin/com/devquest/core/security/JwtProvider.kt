package com.devquest.core.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date

@Component
class JwtProvider(
    @Value("\${devquest.auth.jwt-secret}") secret: String,
    @Value("\${devquest.auth.jwt-expiration-ms}") private val expirationMs: Long,
) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generate(userId: String): String = Jwts.builder()
        .subject(userId)
        .issuedAt(Date())
        .expiration(Date(System.currentTimeMillis() + expirationMs))
        .signWith(key)
        .compact()

    fun extractUserId(token: String): String = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
        .subject
}
