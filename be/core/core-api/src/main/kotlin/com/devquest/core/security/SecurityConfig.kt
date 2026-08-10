package com.devquest.core.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    @Value("\${devquest.cors.allowed-origins}") private val allowedOrigins: String,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .authorizeHttpRequests {
                // kubelet은 파드 IP(127.0.0.1 아님)로 readiness를 찌른다. `/actuator/**` 아래 IP 제한에
                // readiness 경로가 걸리면 파드가 영영 Ready가 안 된다 — readiness 그룹 경로만 별도로 연다.
                // 나머지 actuator 엔드포인트(env 등)는 아래 IP 제한을 그대로 유지한다.
                it.requestMatchers(
                    "/api/v1/auth/**",
                    "/health",
                    "/actuator/health",
                    "/actuator/health/readiness",
                ).permitAll()
                it.requestMatchers("/api/v1/tech-interview/**").permitAll()
                it.requestMatchers("/api/v1/daily-question/**").permitAll()
                it.requestMatchers("/actuator/**").access(
                    WebExpressionAuthorizationManager(
                        "hasIpAddress('127.0.0.1') or hasIpAddress('::1') or hasIpAddress('fdaa::/16')"
                    )
                )
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        config.allowedOrigins = allowedOrigins.split(",").map { it.trim() }
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
        config.allowedHeaders = listOf("*")
        config.allowCredentials = true
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}
