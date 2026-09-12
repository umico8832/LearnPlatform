package com.learnplatform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.OAuthLoginException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.LoginResponse;
import com.learnplatform.entity.OAuthLoginTicket;
import com.learnplatform.entity.User;
import com.learnplatform.entity.UserIdentity;
import com.learnplatform.mapper.OAuthLoginTicketMapper;
import com.learnplatform.mapper.UserIdentityMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class GoogleOAuthLoginService {
    private static final String PROVIDER = "google";
    private static final int TICKET_VALID_MINUTES = 2;

    private final UserIdentityMapper identityMapper;
    private final OAuthLoginTicketMapper ticketMapper;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenHasher tokenHasher;
    private final AuthService authService;

    public GoogleOAuthLoginService(UserIdentityMapper identityMapper,
                                   OAuthLoginTicketMapper ticketMapper,
                                   UserMapper userMapper,
                                   PasswordEncoder passwordEncoder,
                                   AuthTokenHasher tokenHasher,
                                   AuthService authService) {
        this.identityMapper = identityMapper;
        this.ticketMapper = ticketMapper;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.tokenHasher = tokenHasher;
        this.authService = authService;
    }

    @Transactional
    public String completeLogin(OidcUser oidcUser) {
        String subject = requiredClaim(oidcUser.getSubject(), "invalid_identity", "Google 身份信息不完整");
        String email = requiredClaim(oidcUser.getEmail(), "email_required", "Google 账号未提供邮箱")
                .trim().toLowerCase(Locale.ROOT);
        int atIndex = email.indexOf('@');
        if (email.length() > 254 || atIndex <= 0 || atIndex == email.length() - 1 || subject.length() > 255) {
            throw new OAuthLoginException("invalid_identity", "Google 身份信息格式无效");
        }
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuthLoginException("email_unverified", "Google 邮箱尚未验证");
        }

        LocalDateTime now = LocalDateTime.now();
        UserIdentity identity = identityMapper.selectOne(Wrappers.<UserIdentity>lambdaQuery()
                .eq(UserIdentity::getProvider, PROVIDER)
                .eq(UserIdentity::getProviderSubject, subject));

        User user;
        if (identity != null) {
            user = requireActiveUser(identity.getUserId());
            updateIdentity(identity, email, oidcUser.getFullName(), oidcUser.getPicture(), now);
        } else {
            User existingUser = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getEmail, email));
            if (existingUser != null) {
                throw new OAuthLoginException("account_exists", "该邮箱已有账号，请使用原登录方式");
            }
            user = createUser(subject, email, oidcUser.getFullName(), oidcUser.getPicture(), now);
            identity = new UserIdentity();
            identity.setUserId(user.getId());
            identity.setProvider(PROVIDER);
            identity.setProviderSubject(subject);
            identity.setCreateTime(now);
            updateIdentity(identity, email, oidcUser.getFullName(), oidcUser.getPicture(), now);
        }

        return issueTicket(user.getId(), now);
    }

    @Transactional
    public LoginResponse exchangeTicket(String rawTicket) {
        if (rawTicket == null || rawTicket.isBlank()) {
            throw invalidTicket();
        }
        LocalDateTime now = LocalDateTime.now();
        OAuthLoginTicket ticket = ticketMapper.selectOne(Wrappers.<OAuthLoginTicket>lambdaQuery()
                .eq(OAuthLoginTicket::getTokenHash, tokenHasher.hash(rawTicket))
                .isNull(OAuthLoginTicket::getUsedAt)
                .gt(OAuthLoginTicket::getExpiresAt, now));
        if (ticket == null) {
            throw invalidTicket();
        }
        int consumed = ticketMapper.consume(ticket.getId(), now);
        if (consumed != 1) {
            throw invalidTicket();
        }
        return authService.createLoginResponse(requireActiveUser(ticket.getUserId()));
    }

    private User createUser(String subject, String email, String name, String picture, LocalDateTime now) {
        User user = new User();
        user.setUsername("google_" + sha256(subject).substring(0, 20));
        user.setEmail(email);
        user.setEmailVerifiedAt(now);
        user.setPassword(passwordEncoder.encode(tokenHasher.randomToken()));
        String fallbackName = email.substring(0, email.indexOf('@'));
        user.setNickname(truncate(name == null || name.isBlank() ? fallbackName : name, 50));
        user.setAvatar(picture != null && picture.length() <= 255 ? picture : null);
        user.setRole("USER");
        user.setStatus(1);
        user.setAuthVersion(0);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    private void updateIdentity(UserIdentity identity, String email, String name, String picture, LocalDateTime now) {
        identity.setEmailSnapshot(truncate(email, 254));
        identity.setDisplayName(truncate(name, 255));
        identity.setAvatarUrl(truncate(picture, 1024));
        identity.setLastLoginAt(now);
        identity.setUpdateTime(now);
        if (identity.getId() == null) {
            identityMapper.insert(identity);
        } else {
            identityMapper.updateById(identity);
        }
    }

    private User requireActiveUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new OAuthLoginException("account_missing", "关联账号不存在");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new OAuthLoginException("account_disabled", "账号已被禁用");
        }
        return user;
    }

    private String issueTicket(Long userId, LocalDateTime now) {
        String rawTicket = tokenHasher.randomToken();
        OAuthLoginTicket ticket = new OAuthLoginTicket();
        ticket.setUserId(userId);
        ticket.setTokenHash(tokenHasher.hash(rawTicket));
        ticket.setExpiresAt(now.plusMinutes(TICKET_VALID_MINUTES));
        ticket.setCreateTime(now);
        ticketMapper.insert(ticket);
        return rawTicket;
    }

    private String requiredClaim(String value, String errorCode, String message) {
        if (value == null || value.isBlank()) {
            throw new OAuthLoginException(errorCode, message);
        }
        return value;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256", e);
        }
    }

    private BusinessException invalidTicket() {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "登录票据无效或已过期");
    }
}
