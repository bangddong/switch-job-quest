package org.springframework.boot.autoconfigure.web.client

import org.springframework.context.annotation.Configuration

/**
 * Bridge class for Spring AI 1.1.x compatibility with Spring Boot 4.x.
 * Spring Boot 4.x moved RestClientAutoConfiguration to org.springframework.boot.restclient.autoconfigure,
 * but Spring AI 1.1.x still references the old package path.
 */
@Configuration
class RestClientAutoConfiguration
