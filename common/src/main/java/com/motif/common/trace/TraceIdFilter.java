package com.motif.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

// 요청마다 추적 ID → MDC(로그)·응답 헤더 / Security 필터보다 먼저 (401·403 응답에도 붙게)
// 클라이언트가 보낸 X-Request-Id는 형식이 맞을 때만 사용
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceId.acceptOrNew(request.getHeader(HEADER));
        response.setHeader(HEADER, traceId);
        try (TraceId.Scope ignored = TraceId.open(traceId)) {
            chain.doFilter(request, response);
        }
    }
}
