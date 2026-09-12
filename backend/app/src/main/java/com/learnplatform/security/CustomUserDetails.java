package com.learnplatform.security;

/**
 * 自定义用户详情，存储在 SecurityContext 中
 */
public class CustomUserDetails {

    private final Long userId;
    private final String username;
    private final String role;

    public CustomUserDetails(Long userId, String username, String role) {
        this.userId = userId;
        this.username = username;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}