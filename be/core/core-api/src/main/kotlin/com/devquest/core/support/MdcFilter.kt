package com.devquest.core.support

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        try {
            MDC.put("requestId", UUID.randomUUID().toString())
            MDC.put("method", request.method)
            MDC.put("uri", request.requestURI)
            chain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
            MDC.remove("method")
            MDC.remove("uri")
        }
    }
}
