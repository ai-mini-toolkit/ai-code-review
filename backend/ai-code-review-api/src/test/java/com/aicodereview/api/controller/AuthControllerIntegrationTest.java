package com.aicodereview.api.controller;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.dto.auth.LoginRequest;
import com.aicodereview.common.dto.auth.LoginResult;
import com.aicodereview.common.dto.auth.RefreshTokenResult;
import com.aicodereview.repository.UserRepository;
import com.aicodereview.repository.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthController 集成测试（AC #14）
 * 测试完整认证流程：登录 → 访问受保护 API → 登出
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private static boolean dbInitialized = false;
    private static boolean httpClientConfigured = false;

    @BeforeEach
    void setup() {
        // Configure Apache HttpClient to handle 401/403 responses without throwing HttpRetryException
        // (Java's default HttpURLConnection throws when it sees WWW-Authenticate header + streaming mode)
        if (!httpClientConfigured) {
            restTemplate.getRestTemplate().setRequestFactory(new HttpComponentsClientHttpRequestFactory());
            httpClientConfigured = true;
        }

        if (!dbInitialized) {
            // 确保测试用户存在（使用 upsert 避免重复）
            if (!userRepository.existsByUsername("testadmin")) {
                User admin = User.builder()
                        .username("testadmin")
                        .passwordHash(passwordEncoder.encode("test123"))
                        .email("testadmin@test.com")
                        .realName("Test Admin")
                        .role("ADMIN")
                        .enabled(true)
                        .build();
                userRepository.save(admin);
            }

            if (!userRepository.existsByUsername("testuser")) {
                User user = User.builder()
                        .username("testuser")
                        .passwordHash(passwordEncoder.encode("test123"))
                        .email("testuser@test.com")
                        .realName("Test User")
                        .role("USER")
                        .enabled(true)
                        .build();
                userRepository.save(user);
            }
            dbInitialized = true;
        }
    }

    // ===== Task 12.2: 登录成功场景 =====

    @Test
    @DisplayName("AC#5 - 登录成功返回 accessToken 和 refreshToken")
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("testadmin", "test123");

        ResponseEntity<ApiResponse<LoginResult>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();

        LoginResult result = response.getBody().getData();
        assertThat(result.getAccessToken()).isNotBlank();
        assertThat(result.getRefreshToken()).isNotBlank();
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isGreaterThan(0);
        assertThat(result.getUser()).isNotNull();
        assertThat(result.getUser().getUsername()).isEqualTo("testadmin");
        assertThat(result.getUser().getRole()).isEqualTo("ADMIN");
    }

    // ===== Task 12.3: 登录失败场景 =====

    @Test
    @DisplayName("AC#5 - 错误密码返回 401")
    void testLogin_WrongPassword_Returns401() {
        LoginRequest request = new LoginRequest("testadmin", "wrongpassword");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AC#5 - 不存在的用户返回 401")
    void testLogin_NonExistentUser_Returns401() {
        LoginRequest request = new LoginRequest("nonexistent", "test123");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== Task 12.4: 访问受保护 API =====

    @Test
    @DisplayName("AC#4 - 带有效 Token 访问受保护 API 返回 200")
    void testAccessProtectedApi_WithValidToken_Returns200() {
        String token = loginAndGetToken("testadmin", "test123");

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AC#4 - 不带 Token 访问受保护 API 返回 401")
    void testAccessProtectedApi_WithoutToken_Returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects",
                HttpMethod.GET,
                new HttpEntity<>(jsonHeaders()),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ===== Task 12.6: 登出 =====

    @Test
    @DisplayName("AC#8 - 登出成功")
    void testLogout_Success() {
        String token = loginAndGetToken("testadmin", "test123");

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/auth/logout",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ===== Task 12.7: 角色权限 =====

    @Test
    @DisplayName("AC#12 - USER 角色访问 GET 端点返回 200")
    void testUserRole_CanAccessReadEndpoints() {
        String token = loginAndGetToken("testuser", "test123");

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("AC#12 - USER 角色访问 POST 端点被拒绝（403）")
    void testUserRole_CannotAccessWriteEndpoints() {
        String token = loginAndGetToken("testuser", "test123");

        HttpHeaders headers = bearerHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/projects",
                HttpMethod.POST,
                new HttpEntity<>("{\"name\":\"test\"}", headers),
                String.class
        );

        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AC#9 - GET /api/v1/auth/me 返回当前用户信息")
    void testGetCurrentUser_ReturnsUserInfo() {
        String token = loginAndGetToken("testadmin", "test123");

        HttpHeaders headers = bearerHeaders(token);
        ResponseEntity<ApiResponse<com.aicodereview.common.dto.auth.UserInfoDTO>> response = restTemplate.exchange(
                "/api/v1/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getUsername()).isEqualTo("testadmin");
        assertThat(response.getBody().getData().getRole()).isEqualTo("ADMIN");
    }

    // ===== Helper Methods =====

    private String loginAndGetToken(String username, String password) {
        LoginRequest request = new LoginRequest(username, password);
        ResponseEntity<ApiResponse<LoginResult>> response = restTemplate.exchange(
                "/api/v1/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(request, jsonHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody().getData().getAccessToken();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }
}
