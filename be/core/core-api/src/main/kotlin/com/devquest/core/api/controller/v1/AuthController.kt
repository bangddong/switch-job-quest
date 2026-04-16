package com.devquest.core.api.controller.v1

import com.devquest.core.security.JwtProvider
import com.devquest.core.support.response.ApiResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val jwtProvider: JwtProvider,
    @Value("\${devquest.auth.github-client-id}") private val clientId: String,
    @Value("\${devquest.auth.github-client-secret}") private val clientSecret: String,
) {
    private val restClient = RestClient.create()

    data class GithubAuthRequest(val code: String, val redirectUri: String)
    data class TokenResponse(val token: String)

    @PostMapping("/github")
    fun githubAuth(@RequestBody request: GithubAuthRequest): ApiResponse<TokenResponse> {
        // 1. code → access_token
        val tokenResponse = restClient.post()
            .uri("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .body(mapOf(
                "client_id" to clientId,
                "client_secret" to clientSecret,
                "code" to request.code,
                "redirect_uri" to request.redirectUri,
            ))
            .retrieve()
            .body(Map::class.java) ?: error("Failed to get access token from GitHub")

        if (tokenResponse.containsKey("error")) {
            val githubError = tokenResponse["error"]
            val description = tokenResponse["error_description"]
            error("GitHub OAuth error: $githubError — $description")
        }

        val accessToken = tokenResponse["access_token"]?.toString()
            ?: error("No access token in response")

        // 2. access_token → user info
        val userInfo = restClient.get()
            .uri("https://api.github.com/user")
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/vnd.github+json")
            .retrieve()
            .body(Map::class.java) ?: error("Failed to get user info")

        val githubId = userInfo["id"]?.toString() ?: error("No user id in response")

        // 3. JWT 발급
        val jwt = jwtProvider.generate("github-$githubId")
        return ApiResponse.success(TokenResponse(jwt))
    }
}
