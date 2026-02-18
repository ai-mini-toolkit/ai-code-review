package com.aicodereview.service.auth;

import com.aicodereview.common.dto.auth.RegisterRequest;
import com.aicodereview.common.dto.auth.UserInfoDTO;
import com.aicodereview.common.exception.DuplicateResourceException;
import com.aicodereview.repository.UserRepository;
import com.aicodereview.repository.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 认证业务逻辑服务（不包含 JWT Token 生成，由 Controller 层处理以避免循环依赖）
 * - 登录验证（密码校验通过 Spring Security AuthenticationManager）
 * - 用户注册
 * - 获取用户信息
 * - Refresh Token 黑名单检查（登出）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenBlacklistService blacklistService;

    /**
     * 执行用户名/密码认证，返回已认证的 UserDetails
     *
     * @param username 用户名
     * @param password 明文密码
     * @return 认证成功的 UserDetails
     * @throws AuthenticationException 认证失败时抛出
     */
    public UserDetails authenticate(String username, String password) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );
        } catch (AuthenticationException e) {
            log.warn("Authentication failed for user {}: {}", username, e.getMessage());
            throw e;
        }

        log.info("User authenticated successfully: {}", username);
        return (UserDetails) authentication.getPrincipal();
    }

    /**
     * 注册新用户（仅 ADMIN 角色可调用）
     */
    @Transactional
    public UserInfoDTO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .realName(request.getRealName())
                .role(request.getRole() != null ? request.getRole() : "USER")
                .enabled(true)
                .build();

        User saved = userRepository.save(user);
        log.info("New user registered: {} (role: {})", saved.getUsername(), saved.getRole());
        return toUserInfoDTO(saved);
    }

    /**
     * 将 Refresh Token 加入黑名单（登出时调用）
     *
     * @param refreshToken Refresh Token
     * @param expiresIn    剩余有效期（秒）
     */
    public void logout(String refreshToken, long expiresIn) {
        blacklistService.addToBlacklist(refreshToken, expiresIn);
        log.info("User logged out, refresh token blacklisted");
    }

    /**
     * 检查 Refresh Token 是否已被吊销
     */
    public boolean isRefreshTokenBlacklisted(String refreshToken) {
        return blacklistService.isBlacklisted(refreshToken);
    }

    /**
     * 获取用户信息
     */
    @Transactional(readOnly = true)
    public UserInfoDTO getUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
        return toUserInfoDTO(user);
    }

    private UserInfoDTO toUserInfoDTO(User user) {
        String homePath = "ADMIN".equals(user.getRole()) ? "/dashboard" : "/review/history";
        return UserInfoDTO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .realName(user.getRealName())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .roles(List.of(user.getRole()))
                .enabled(user.getEnabled())
                .homePath(homePath)
                .build();
    }
}
