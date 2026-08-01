package com.learnplatform.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {
    public String resolve(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (isUsable(realIp)) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private boolean isUsable(String value) {
        return value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value);
    }
}
