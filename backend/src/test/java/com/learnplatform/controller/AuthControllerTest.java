package com.learnplatform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.exception.GlobalExceptionHandler;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.config.CaptchaService;
import com.learnplatform.config.LoginRateLimitService;
import com.learnplatform.dto.*;
import com.learnplatform.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController MockMvc 集成测试（standalone 模式，兼容 JDK 26）
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private LoginRateLimitService rateLimitService;

    @Mock
    private CaptchaService captchaService;

    @InjectMocks
    private AuthController authController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // ======================== 注册 ========================

    @Test
    void register_success() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("testuser");
        userVO.setNickname("测试用户");

        when(authService.register(any(RegisterRequest.class))).thenReturn(userVO);

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("testuser"));
    }

    @Test
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }

    @Test
    void register_duplicateUsername_returnsBusinessError() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在"));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("123456");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.BUSINESS_ERROR.getCode()));
    }

    // ======================== 登录 ========================

    @Test
    void login_success() throws Exception {
        UserVO userVO = new UserVO();
        userVO.setId(1L);
        userVO.setUsername("testuser");

        LoginResponse response = new LoginResponse();
        response.setToken("jwt-token-123");
        response.setUser(userVO);

        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(rateLimitService.isBlocked(anyString())).thenReturn(false);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setCaptchaId("test-captcha-id");
        request.setCaptchaCode("42");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("jwt-token-123"));

        verify(rateLimitService).clearRecord(anyString());
    }

    @Test
    void login_wrongPassword_returnsUnauthorized() throws Exception {
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(rateLimitService.isBlocked(anyString())).thenReturn(false);
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("wrong");
        request.setCaptchaId("test-captcha-id");
        request.setCaptchaCode("42");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()));

        verify(rateLimitService).recordFailure(anyString());
        verify(rateLimitService, never()).clearRecord(anyString());
    }

    @Test
    void login_rateLimited_returnsRateLimited() throws Exception {
        when(captchaService.verifyCaptcha(anyString(), anyString())).thenReturn(true);
        when(rateLimitService.isBlocked(anyString())).thenReturn(true);
        when(rateLimitService.getRemainingBlockSeconds(anyString())).thenReturn(300L);

        LoginRequest request = new LoginRequest();
        request.setUsername("testuser");
        request.setPassword("123456");
        request.setCaptchaId("test-captcha-id");
        request.setCaptchaCode("42");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.RATE_LIMITED.getCode()));
    }

    @Test
    void login_blankFields_returns400() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("");
        request.setPassword("");
        request.setCaptchaId("");
        request.setCaptchaCode("");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}