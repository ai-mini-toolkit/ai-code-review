package com.aicodereview.api.controller;

import com.aicodereview.api.security.JwtTokenProvider;
import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.ErrorCode;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.common.dto.auth.RefreshTokenResult;
import com.aicodereview.common.dto.auth.RegisterRequest;
import com.aicodereview.common.dto.auth.UserInfoDTO;
import com.aicodereview.service.auth.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 认证 Controller
 * POST /api/v1/auth/login    — 登录（返回 accessToken + refreshToken HttpOnly Cookie）
 * POST /api/v1/auth/refresh  — 刷新 accessToken（从 Cookie 读取 refreshToken）
 * POST /api/v1/auth/logout   — 登出（将 refreshToken 加入黑名单）
 * POST /api/v1/auth/register — 注册新用户（仅 ADMIN）
 * GET  /api/v1/auth/me       — 获取当前用户信息
 * GET  /api/v1/auth/codes    — 获取用户权限码（Vben Admin 兼容）
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";
    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7 days in seconds

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;

    /**
     * 登录 API
     * AC #5: POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResult>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        log.info("POST /api/v1/auth/login - Login attempt: {}", request.getUsername());

        UserDetails userDetails;
        try {
            userDetails = authService.authenticate(request.getUsername(), request.getPassword());
        } catch (AuthenticationException e) {
            log.warn("Login failed for user {}: {}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(ErrorCode.UNAUTHORIZED, "Invalid username or password"));
        }

        String accessToken = jwtTokenProvider.generateAccessToken(userDetails);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userDetails);
        long expiresIn = jwtTokenProvider.getTokenExpiresIn(accessToken);

        // Get user info
        UserInfoDTO userInfo = authService.getUserInfo(request.getUsername());

        // Set refreshToken as HttpOnly Cookie (防 XSS)
        setRefreshTokenCookie(response, refreshToken);

        LoginResult result = LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(userInfo)
                .build();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 刷新 Token API
     * AC #7: POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResult>> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        // 从 HttpOnly Cookie 中读取 refreshToken
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(
                            ErrorCode.UNAUTHORIZED,
                            "No refresh token provided"
                    ));
        }

        // 检查是否已被吊销
        if (authService.isRefreshTokenBlacklisted(refreshToken)) {
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(
                            ErrorCode.UNAUTHORIZED,
                            "Refresh token has been revoked"
                    ));
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            clearRefreshTokenCookie(response);
            return ResponseEntity.status(401)
                    .body(ApiResponse.error(
                            ErrorCode.UNAUTHORIZED,
                            "Invalid or expired refresh token"
                    ));
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userDetails);
        long expiresIn = jwtTokenProvider.getTokenExpiresIn(newAccessToken);

        // Return as { data: "token" } for Vben Admin compatibility (RefreshTokenResult.data)
        RefreshTokenResult result = RefreshTokenResult.builder()
                .accessToken(newAccessToken)
                .expiresIn(expiresIn)
                .build();

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 登出 API
     * AC #8: POST /api/v1/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshTokenFromCookie(request);

        if (refreshToken != null && !refreshToken.isBlank()) {
            long expiresIn = jwtTokenProvider.getTokenExpiresIn(refreshToken);
            authService.logout(refreshToken, expiresIn);
        }

        clearRefreshTokenCookie(response);
        log.info("POST /api/v1/auth/logout - User logged out");
        return ResponseEntity.ok(ApiResponse.success());
    }

    /**
     * 注册 API（仅 ADMIN 角色）
     * AC #6: POST /api/v1/auth/register
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserInfoDTO>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        log.info("POST /api/v1/auth/register - Registering user: {}", request.getUsername());
        UserInfoDTO userInfo = authService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.success(userInfo));
    }

    /**
     * 获取当前用户信息
     * AC #9: GET /api/v1/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoDTO>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserInfoDTO info = authService.getUserInfo(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(info));
    }

    /**
     * 获取用户权限码（Vben Admin 兼容接口）
     * GET /api/v1/auth/codes → GET /user/info 的补充
     */
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<String>>> getAccessCodes(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserInfoDTO info = authService.getUserInfo(userDetails.getUsername());
        // 返回角色作为权限码
        return ResponseEntity.ok(ApiResponse.success(info.getRoles()));
    }

    // ===== Helper methods =====

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, refreshToken);
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(REFRESH_TOKEN_MAX_AGE);
        // cookie.setSecure(true); // Enable in production with HTTPS
        response.addCookie(cookie);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(REFRESH_TOKEN_COOKIE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
