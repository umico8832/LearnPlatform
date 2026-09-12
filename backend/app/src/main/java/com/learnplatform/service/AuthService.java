package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.LoginRequest;
import com.learnplatform.dto.LoginResponse;
import com.learnplatform.dto.RegisterRequest;
import com.learnplatform.dto.UpdatePasswordRequest;
import com.learnplatform.dto.UpdateProfileRequest;
import com.learnplatform.dto.UserVO;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.UserMapper;
import com.learnplatform.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * 认证服务
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailVerificationService emailVerificationService;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider, EmailVerificationService emailVerificationService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * 用户注册
     */
    @Transactional
    public UserVO register(RegisterRequest request) {
        log.info("用户注册: username={}", request.getUsername());
        // 检查用户名是否已存在
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, request.getUsername());
        Long count = userMapper.selectCount(wrapper);
        if (count > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "用户名已存在");
        }

        String email = emailVerificationService.normalizeEmail(request.getEmail());
        Long emailCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (emailCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "邮箱已被注册");
        }
        emailVerificationService.consumeRegistrationTicket(email, request.getVerificationTicket());

        // 创建用户
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(email);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        user.setStatus(1);
        user.setAuthVersion(0);
        user.setDeleted(0);
        userMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());

        return UserVO.fromUser(user);
    }

    /**
     * 用户登录
     */
    public LoginResponse login(LoginRequest request) {
        String account = request.getAccount().trim();
        log.info("用户登录请求");
        // 查找用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (account.contains("@")) {
            wrapper.eq(User::getEmail, account.toLowerCase(Locale.ROOT));
        } else {
            wrapper.eq(User::getUsername, account);
        }
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 检查用户状态
        if (user.getStatus() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 生成 Token
        String token = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole(),
                user.getAuthVersion() == null ? 0 : user.getAuthVersion());
        log.info("用户登录成功: userId={}, username={}, role={}", user.getId(), user.getUsername(), user.getRole());

        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setExpiresIn(jwtTokenProvider.getExpirationSeconds());
        response.setUser(UserVO.fromUser(user));

        return response;
    }

    /**
     * 获取当前用户信息
     */
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return UserVO.fromUser(user);
    }

    /**
     * 修改个人信息（昵称）
     */
    public UserVO updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        log.info("用户修改昵称: userId={}, newNickname={}", userId, request.getNickname());
        user.setNickname(request.getNickname());
        userMapper.updateById(user);
        return UserVO.fromUser(user);
    }

    /**
     * 修改密码
     */
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        // 验证原密码
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "原密码错误");
        }
        // 新密码不能与原密码相同
        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "新密码不能与原密码相同");
        }
        log.info("用户修改密码: userId={}", userId);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setAuthVersion((user.getAuthVersion() == null ? 0 : user.getAuthVersion()) + 1);
        userMapper.updateById(user);
    }
}
