package com.learnplatform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求日志过滤器
 * 记录每个 HTTP 请求的方法、URI、状态码和耗时
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 跳过健康检查和静态资源
        String uri = request.getRequestURI();
        if (uri.contains("/public/health") || uri.contains("/doc.html")
                || uri.contains("/swagger") || uri.contains("/v3/api-docs")
                || uri.contains("/webjars") || uri.contains("/favicon")) {
            filterChain.doFilter(request, response);
            return;
        }

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String queryString = request.getQueryString();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = response.getStatus();
            String uriWithQuery = queryString != null ? uri + "?" + queryString : uri;

            // 将请求上下文写入 MDC，供结构化 JSON 日志采集
            MDC.put("httpMethod", method);
            MDC.put("httpUri", uri);
            MDC.put("httpStatus", String.valueOf(status));
            MDC.put("durationMs", String.valueOf(duration));

            try {
                if (status >= 400) {
                    log.warn("HTTP {} {} -> {} ({}ms)", method, uriWithQuery, status, duration);
                } else {
                    log.info("HTTP {} {} -> {} ({}ms)", method, uriWithQuery, status, duration);
                }
            } finally {
                MDC.remove("httpMethod");
                MDC.remove("httpUri");
                MDC.remove("httpStatus");
                MDC.remove("durationMs");
            }
        }
    }
}