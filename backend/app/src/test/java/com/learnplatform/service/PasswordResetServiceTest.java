package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.learnplatform.entity.PasswordResetToken;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.PasswordResetTokenMapper;
import com.learnplatform.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceTest {
    private PasswordResetTokenMapper resetMapper;
    private UserMapper userMapper;
    private AuthMailService mailService;
    private PasswordEncoder passwordEncoder;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), PasswordResetToken.class);
        resetMapper = mock(PasswordResetTokenMapper.class);
        userMapper = mock(UserMapper.class);
        mailService = mock(AuthMailService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        AuthTokenHasher tokenHasher = new AuthTokenHasher("test-auth-secret", "fallback-secret");
        service = new PasswordResetService(resetMapper, userMapper, tokenHasher,
                mailService, passwordEncoder, "http://localhost:5173/");
    }

    @Test
    void unknownEmailReturnsWithoutCreatingTokenOrSendingMail() {
        when(userMapper.selectOne(any())).thenReturn(null);

        service.requestReset("unknown@example.com", "127.0.0.1");

        verify(resetMapper, never()).insert(any());
        verify(mailService, never()).sendPasswordResetLink(any(), any());
    }

    @Test
    void resetConsumesTokenChangesPasswordAndInvalidatesOldJwt() {
        PasswordResetToken reset = new PasswordResetToken();
        reset.setId(10L);
        reset.setUserId(7L);
        reset.setExpiresAt(LocalDateTime.now().plusMinutes(20));
        User user = new User();
        user.setId(7L);
        user.setAuthVersion(2);
        when(resetMapper.selectOne(any())).thenReturn(reset);
        when(resetMapper.update(any(), any())).thenReturn(1);
        when(userMapper.selectById(7L)).thenReturn(user);
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-password");

        service.resetPassword("valid-token", "new-password");

        assertEquals("encoded-password", user.getPassword());
        assertEquals(3, user.getAuthVersion());
        verify(userMapper).updateById(user);
    }
}
