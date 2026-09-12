package com.learnplatform.security;

import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * JWT 认证过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserMapper userMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userMapper = userMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractTokenFromRequest(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                Long userId = jwtTokenProvider.getUserIdFromToken(token);
                String role = jwtTokenProvider.getRoleFromToken(token);
                User user = userMapper.selectById(userId);

                if (!isCurrentUserValid(user, username, role,
                        jwtTokenProvider.getIssuedAtFromToken(token),
                        jwtTokenProvider.getAuthVersionFromToken(token))) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 创建认证信息，将 userId 存为 principal
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                        new SimpleGrantedAuthority("ROLE_" + role));

                CustomUserDetails userDetails = new CustomUserDetails(userId, username, role);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            log.error("Could not set user authentication in security context: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isCurrentUserValid(User user, String username, String role, Date issuedAt,
                                       Integer tokenAuthVersion) {
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return false;
        }
        if (!Objects.equals(user.getUsername(), username) || !Objects.equals(user.getRole(), role)) {
            return false;
        }
        int currentVersion = user.getAuthVersion() == null ? 0 : user.getAuthVersion();
        if (!Objects.equals(currentVersion, tokenAuthVersion == null ? 0 : tokenAuthVersion)) {
            return false;
        }
        if (user.getUpdateTime() == null || issuedAt == null) {
            return true;
        }
        Date updatedAt = Date.from(user.getUpdateTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        return !updatedAt.after(issuedAt);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
