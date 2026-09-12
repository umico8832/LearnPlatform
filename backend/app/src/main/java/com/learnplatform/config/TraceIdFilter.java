package com.learnplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪 ID 过滤器
 * 为每个 HTTP 请求生成唯一 traceId，写入 SLF4J MDC，
 * 使同一次请求的所有日志共享相同 traceId，便于日志聚合和问题排查。
 *
 * MDC 字段：
 * - traceId：请求级唯一标识（8 位短 UUID）
 * - clientIp：客户端 IP
 * - userId：已认证用户 ID（SecurityContext 中提取）
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String CLIENT_IP_KEY = "clientIp";
    private static final String USER_ID_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 生成 8 位短 UUID 作为 traceId
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        MDC.put(TRACE_ID_KEY, traceId);

        // 提取客户端真实 IP
        String clientIp = extractClientIp(request);
        MDC.put(CLIENT_IP_KEY, clientIp);

        // 将 traceId 写入响应头，便于前端调试
        response.setHeader("X-Trace-Id", traceId);

        try {
            filterChain.doFilter(request, response);

            // 请求处理完成后尝试提取 userId（可能在认证 Filter 后才可用）
            tryExtractUserId();
        } finally {
            MDC.clear();
        }
    }

    /**
     * 提取客户端真实 IP，支持反向代理场景
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    /**
     * 从 SecurityContext 中提取已认证用户的 ID
     */
    private void tryExtractUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof com.learnplatform.security.CustomUserDetails userDetails) {
            MDC.put(USER_ID_KEY, String.valueOf(userDetails.getUserId()));
        }
    }
}