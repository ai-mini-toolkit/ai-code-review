# Story 8.6: 实现用户认证与授权（JWT）

Status: done

## Story

As a 系统管理员,
I want 实现用户认证和授权系统,
so that 保护 Web 界面和 API，确保只有授权用户能够访问系统资源。

## Acceptance Criteria

1. **数据库 User 表**：创建 `user` 表，包含字段：id、username、password_hash、email、role（ADMIN/USER）、enabled、created_at、updated_at
2. **密码加密存储**：使用 BCrypt 算法加密存储密码（strength=10），明文密码永不落库
3. **JWT Token 生成**：登录成功后生成 JWT Token，包含用户 claims（userId, username, role），使用 HS256 算法签名
4. **JWT Token 验证**：每个 API 请求通过 JWT Filter 验证 Token 签名和过期时间
5. **登录 API**：POST `/api/v1/auth/login`，请求体包含 username + password，返回 accessToken（1小时有效）和 refreshToken（7天有效）
6. **注册 API**：POST `/api/v1/auth/register`，仅 ADMIN 角色可调用，创建新用户
7. **刷新 Token API**：POST `/api/v1/auth/refresh`，使用 refreshToken 换取新的 accessToken
8. **登出 API**：POST `/api/v1/auth/logout`，将 refreshToken 加入黑名单（Redis 存储）
9. **前端 Token 存储**：accessToken 存储在内存（Pinia store），refreshToken 存储在 HttpOnly Cookie
10. **Axios 请求拦截器**：自动在请求头添加 `Authorization: Bearer {accessToken}`
11. **路由守卫**：未登录用户访问受保护路由时自动跳转到登录页
12. **角色权限控制**：ADMIN 角色拥有所有操作权限，USER 角色仅能查看（不能删除、修改配置）
13. **Token 自动刷新**：accessToken 过期时自动使用 refreshToken 刷新，对用户透明
14. **集成测试**：编写认证流程集成测试（登录 → 访问受保护 API → 刷新 Token → 登出）

## Tasks / Subtasks

- [x] Task 1: 创建数据库 User 表和实体类 (AC: #1, #2)
  - [x]1.1 创建 Flyway 迁移脚本 `V10__create_user_table.sql`
  - [x]1.2 创建 `User` 实体类（JPA + Lombok）
  - [x]1.3 创建 `UserRepository` 接口（Spring Data JPA）
  - [x]1.4 添加 `findByUsername(String)` 和 `findByEmail(String)` 查询方法

- [x] Task 2: 添加 Spring Security 6 + JWT 依赖 (AC: #3, #4)
  - [x]2.1 在 `backend/pom.xml` 添加 `spring-boot-starter-security` 依赖
  - [x]2.2 添加 JJWT 依赖：`jjwt-api:0.12.5`, `jjwt-impl:0.12.5`, `jjwt-jackson:0.12.5`
  - [x]2.3 在 `application.yml` 配置 JWT secret 和过期时间

- [x] Task 3: 实现 JWT 工具类 (AC: #3, #4)
  - [x]3.1 创建 `JwtTokenProvider`：生成 JWT Token（generateAccessToken, generateRefreshToken）
  - [x]3.2 实现 JWT Token 验证（validateToken, getUsernameFromToken, getRoleFromToken）
  - [x]3.3 实现 Token 过期检查（isTokenExpired）

- [x] Task 4: 实现 Spring Security 配置 (AC: #4, #12)
  - [x]4.1 创建 `SecurityConfig`：配置 HTTP Security（禁用 CSRF，启用 JWT）
  - [x]4.2 配置白名单路径（/api/v1/auth/**, /api/v1/webhook/**）
  - [x]4.3 配置角色权限：ADMIN 全部权限，USER 只读权限
  - [x]4.4 创建 `JwtAuthenticationFilter`：验证每个请求的 JWT Token
  - [x]4.5 创建 `JwtAuthenticationEntryPoint`：处理认证失败（返回 401）

- [x] Task 5: 实现认证服务层 (AC: #5, #6, #7, #8)
  - [x]5.1 创建 `AuthService`：实现 login, register, refresh, logout 业务逻辑
  - [x]5.2 实现 `UserDetailsServiceImpl`：加载用户信息供 Spring Security 使用
  - [x]5.3 实现密码加密验证（使用 BCryptPasswordEncoder）
  - [x]5.4 实现 RefreshToken 黑名单（Redis 存储，7天 TTL）

- [x] Task 6: 实现认证 Controller (AC: #5, #6, #7, #8)
  - [x]6.1 创建 `AuthController`：POST /api/v1/auth/login
  - [x]6.2 实现 POST /api/v1/auth/register（仅 ADMIN 角色）
  - [x]6.3 实现 POST /api/v1/auth/refresh
  - [x]6.4 实现 POST /api/v1/auth/logout
  - [x]6.5 实现 GET /api/v1/auth/me（获取当前用户信息）

- [x] Task 7: 前端认证 API 集成 (AC: #9, #10)
  - [x]7.1 更新 `src/api/core/auth.ts`：修改 loginApi 使用真实后端 `/api/v1/auth/login`
  - [x]7.2 更新认证响应接口（accessToken, refreshToken, expiresIn, tokenType）
  - [x]7.3 配置 Axios 请求拦截器：自动添加 `Authorization: Bearer {token}` header
  - [x]7.4 配置 Axios 响应拦截器：处理 401 错误（Token 过期）

- [x] Task 8: 实现 Token 自动刷新机制 (AC: #13)
  - [x]8.1 在 Axios 响应拦截器中检测 401 错误
  - [x]8.2 调用 refreshTokenApi 获取新 accessToken
  - [x]8.3 重试原请求（使用新 Token）
  - [x]8.4 如果 refreshToken 也过期，跳转到登录页

- [x] Task 9: 更新路由守卫 (AC: #11)
  - [x]9.1 确认 Vben Admin 路由守卫已启用（检查 `src/router/guard.ts`）
  - [x]9.2 验证未登录用户访问受保护路由会跳转到 `/login`
  - [x]9.3 验证登录后自动跳转到 `redirect` 参数指定的页面

- [x] Task 10: 实现角色权限控制 (AC: #12)
  - [x]10.1 在 Controller 方法上添加 `@PreAuthorize("hasRole('ADMIN')")` 注解
  - [x]10.2 更新项目管理 API：删除/修改操作限制为 ADMIN
  - [x]10.3 更新 AI 模型配置 API：所有写操作限制为 ADMIN
  - [x]10.4 更新 Prompt 模板 API：所有写操作限制为 ADMIN
  - [x]10.5 前端隐藏 USER 角色不可操作的按钮（使用 `v-if="hasRole('ADMIN')"）

- [x] Task 11: 创建默认管理员用户 (AC: #6)
  - [x]11.1 创建 Flyway 迁移脚本 `V11__insert_default_admin.sql`
  - [x]11.2 插入默认管理员（username=admin, password=admin123, role=ADMIN）
  - [x]11.3 在 README.md 中记录默认管理员账号

- [x] Task 12: 编写认证流程集成测试 (AC: #14)
  - [x]12.1 创建 `AuthControllerIntegrationTest`
  - [x]12.2 测试登录成功场景（返回 accessToken 和 refreshToken）
  - [x]12.3 测试登录失败场景（错误密码返回 401）
  - [x]12.4 测试访问受保护 API（带 Token 返回 200，不带 Token 返回 401）
  - [x]12.5 测试刷新 Token（使用 refreshToken 获取新 accessToken）
  - [x]12.6 测试登出（refreshToken 加入黑名单后不能再使用）
  - [x]12.7 测试角色权限（USER 角色不能访问 ADMIN 接口）

- [x] Task 13: 更新前端登录页面 (AC: #9)
  - [x]13.1 移除 Vben Admin 示例代码中的 Mock 用户选择器
  - [x]13.2 移除滑块验证码（或保留作为可选功能）
  - [x]13.3 更新登录表单：仅保留 username + password 字段
  - [x]13.4 更新错误提示：显示后端返回的错误消息

- [x] Task 14: 国际化 (AC: 全部)
  - [x]14.1 确认 `src/locales/langs/en-US/authentication.json` 已有登录相关翻译
  - [x]14.2 确认 `src/locales/langs/zh-CN/authentication.json` 已有登录相关翻译
  - [x]14.3 添加缺失的翻译 key（如 "tokenExpired", "refreshTokenExpired"）

## Dev Notes

### 🎯 Story 8.6 核心目标

**业务价值**：保护系统资源，确保只有授权用户能够访问。实现用户身份验证和基于角色的访问控制（RBAC）。

**技术价值**：建立安全的认证授权基础设施，为后续多租户、审计日志、细粒度权限等功能奠定基础。

**安全原则**：
1. **密码安全**：BCrypt 加密，永不存储明文密码
2. **Token 安全**：JWT 签名防止篡改，过期时间限制暴露窗口
3. **传输安全**：HTTPS 传输（生产环境强制）
4. **存储安全**：accessToken 内存存储，refreshToken HttpOnly Cookie（防 XSS）
5. **黑名单机制**：refreshToken 登出后加入 Redis 黑名单（防止 Token 重放）

---

### 📦 TypeScript 接口定义

#### 前端认证 API 接口

```typescript
// src/api/core/auth.ts

/**
 * 登录请求参数
 */
export interface LoginParams {
  username: string;              // 用户名
  password: string;              // 明文密码（前端提交，后端验证后立即丢弃）
  rememberMe?: boolean;          // 是否记住登录（可选，影响 refreshToken 有效期）
}

/**
 * 登录响应结果
 */
export interface LoginResult {
  accessToken: string;           // JWT Access Token（短期有效，1小时）
  refreshToken: string;          // JWT Refresh Token（长期有效，7天）
  tokenType: string;             // Token 类型（固定为 "Bearer"）
  expiresIn: number;             // accessToken 过期时间（秒，3600）
  user: UserInfo;                // 用户信息
}

/**
 * 刷新 Token 响应结果
 */
export interface RefreshTokenResult {
  accessToken: string;           // 新的 Access Token
  expiresIn: number;             // 过期时间（秒）
}

/**
 * 用户信息（扩展 Vben Admin UserInfo）
 */
export interface UserInfo {
  id: number;                    // 用户 ID
  username: string;              // 用户名
  email: string;                 // 邮箱
  realName?: string;             // 真实姓名（可选）
  avatar?: string;               // 头像 URL（可选）
  role: UserRole;                // 用户角色
  roles: string[];               // 角色数组（Vben Admin 格式）
  enabled: boolean;              // 是否启用
  homePath: string;              // 登录后跳转的首页路径
}

/**
 * 用户角色
 */
export type UserRole = 'ADMIN' | 'USER';

/**
 * 注册请求参数（仅 ADMIN 可调用）
 */
export interface RegisterParams {
  username: string;              // 用户名（唯一）
  password: string;              // 明文密码
  email: string;                 // 邮箱（唯一）
  realName?: string;             // 真实姓名（可选）
  role: UserRole;                // 用户角色
}
```

---

### 🗄️ 数据库表结构

#### User 表（Flyway V10 迁移脚本）

```sql
-- V10__create_user_table.sql

CREATE TABLE IF NOT EXISTS "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,  -- BCrypt hash ($2a$10$...)
    email VARCHAR(100) NOT NULL UNIQUE,
    real_name VARCHAR(100),
    avatar VARCHAR(255),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',  -- ADMIN, USER
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_role CHECK (role IN ('ADMIN', 'USER'))
);

-- 创建索引（优化查询性能）
CREATE INDEX idx_user_username ON "user"(username);
CREATE INDEX idx_user_email ON "user"(email);
CREATE INDEX idx_user_role ON "user"(role);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_user_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_updated_at_trigger
BEFORE UPDATE ON "user"
FOR EACH ROW
EXECUTE FUNCTION update_user_updated_at();

COMMENT ON TABLE "user" IS '系统用户表';
COMMENT ON COLUMN "user".id IS '用户 ID（主键）';
COMMENT ON COLUMN "user".username IS '用户名（唯一）';
COMMENT ON COLUMN "user".password_hash IS 'BCrypt 加密密码';
COMMENT ON COLUMN "user".email IS '邮箱（唯一）';
COMMENT ON COLUMN "user".role IS '用户角色：ADMIN（管理员）、USER（普通用户）';
COMMENT ON COLUMN "user".enabled IS '是否启用';
```

#### 默认管理员用户（Flyway V11 迁移脚本）

```sql
-- V11__insert_default_admin.sql

-- 插入默认管理员用户
-- 用户名: admin
-- 密码: admin123 (BCrypt hash: $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi)
INSERT INTO "user" (username, password_hash, email, real_name, role, enabled)
VALUES (
    'admin',
    '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
    'admin@aicodereview.com',
    'System Administrator',
    'ADMIN',
    true
)
ON CONFLICT (username) DO NOTHING;  -- 如果已存在则跳过

COMMENT ON TABLE "user" IS '默认管理员账号: admin / admin123';
```

---

### 🔐 后端实现：Spring Security 6 + JWT

#### 依赖配置（backend/pom.xml）

```xml
<!-- Spring Security 6 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JJWT（JWT 生成和验证）-->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
```

#### JWT 配置（application.yml）

```yaml
# application.yml
jwt:
  secret: ${JWT_SECRET:ThisIsAVeryLongSecretKeyForJWTTokenGenerationAndValidation1234567890}  # 至少 256 bits
  access-token-expiration: 3600000      # 1 hour (1 * 60 * 60 * 1000 ms)
  refresh-token-expiration: 604800000   # 7 days (7 * 24 * 60 * 60 * 1000 ms)
  issuer: aicodereview                  # Token 签发者
  audience: aicodereview-api            # Token 受众

# Redis 配置（用于 RefreshToken 黑名单）
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}
    password: ${REDIS_PASSWORD:}
    database: 1                         # 使用 database 1（避免与队列冲突）
```

#### JWT Token Provider

```java
// backend/ai-code-review-api/src/main/java/com/aicodereview/api/security/JwtTokenProvider.java

package com.aicodereview.api.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.audience}")
    private String audience;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Access Token
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", userDetails.getAuthorities().iterator().next().getAuthority());

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 生成 Refresh Token
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 从 Token 中提取用户名
     */
    public String getUsernameFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    /**
     * 从 Token 中提取角色
     */
    public String getRoleFromToken(String token) {
        return (String) getClaimsFromToken(token).get("role");
    }

    /**
     * 验证 Token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .requireIssuer(issuer)
                    .requireAudience(audience)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 检查 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getClaimsFromToken(token).getExpiration();
        return expiration.before(new Date());
    }

    /**
     * 获取 Token 剩余有效期（秒）
     */
    public long getTokenExpiresIn(String token) {
        Date expiration = getClaimsFromToken(token).getExpiration();
        long now = System.currentTimeMillis();
        return (expiration.getTime() - now) / 1000;
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

#### Spring Security 配置

```java
// backend/ai-code-review-api/src/main/java/com/aicodereview/api/security/SecurityConfig.java

package com.aicodereview.api.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // 启用方法级别的权限控制
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（因为使用 JWT，无状态认证）
            .csrf(AbstractHttpConfigurer::disable)

            // 禁用 CORS（在 CorsConfig 中单独配置）
            .cors(AbstractHttpConfigurer::disable)

            // 无状态 Session 管理
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 配置授权规则
            .authorizeHttpRequests(auth -> auth
                // 白名单：认证接口无需 Token
                .requestMatchers("/api/v1/auth/**").permitAll()

                // 白名单：Webhook 接口无需 Token（使用签名验证）
                .requestMatchers("/api/v1/webhook/**").permitAll()

                // 白名单：Actuator 健康检查
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // 查询接口：USER 和 ADMIN 都可访问
                .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()

                // 写操作接口：仅 ADMIN 可访问
                .requestMatchers(HttpMethod.POST, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")

                // 其他所有请求都需要认证
                .anyRequest().authenticated()
            )

            // 配置异常处理
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // 添加 JWT 过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);  // Strength = 10
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
```

#### JWT Authentication Filter

```java
// backend/ai-code-review-api/src/main/java/com/aicodereview/api/security/JwtAuthenticationFilter.java

package com.aicodereview.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 从请求头中提取 JWT Token
            String token = extractTokenFromRequest(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                // 从 Token 中提取用户名
                String username = jwtTokenProvider.getUsernameFromToken(token);

                // 加载用户详情
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // 创建认证对象
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // 设置到 Security Context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated user: {}, URI: {}", username, request.getRequestURI());
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中提取 JWT Token
     * Authorization: Bearer {token}
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);  // 移除 "Bearer " 前缀
        }

        return null;
    }
}
```

#### JWT Authentication Entry Point

```java
// backend/ai-code-review-api/src/main/java/com/aicodereview/api/security/JwtAuthenticationEntryPoint.java

package com.aicodereview.api.security;

import com.aicodereview.common.dto.ApiResponse;
import com.aicodereview.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 处理认证失败（401 Unauthorized）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        log.error("Unauthorized error: {}", authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        ApiResponse<?> apiResponse = ApiResponse.error(
                ErrorCode.UNAUTHORIZED,
                "Authentication required. Please provide a valid token."
        );

        objectMapper.writeValue(response.getOutputStream(), apiResponse);
    }
}
```

---

### 🔒 RefreshToken 黑名单机制

#### RefreshToken Blacklist Service

```java
// backend/ai-code-review-service/src/main/java/com/aicodereview/service/auth/RefreshTokenBlacklistService.java

package com.aicodereview.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "refresh_token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 将 Refresh Token 加入黑名单
     * @param token Refresh Token
     * @param expiresIn 过期时间（秒）
     */
    public void addToBlacklist(String token, long expiresIn) {
        String key = BLACKLIST_KEY_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofSeconds(expiresIn));
        log.info("Added refresh token to blacklist: {}", token.substring(0, 10) + "...");
    }

    /**
     * 检查 Refresh Token 是否在黑名单中
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_KEY_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }
}
```

---

### 🌐 前端实现：Axios 拦截器 + Token 自动刷新

#### 更新 Axios 请求拦截器（request.ts）

```typescript
// frontend/apps/web-ele/src/api/request.ts

import { useAccessStore } from '@vben/stores';

// 请求拦截器：自动添加 Authorization header
client.addRequestInterceptor({
  fulfilled: async (config) => {
    const accessStore = useAccessStore();
    const token = accessStore.accessToken;

    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  },
});
```

#### Token 自动刷新机制（响应拦截器）

```typescript
// frontend/apps/web-ele/src/api/request.ts

import { refreshTokenApi } from './core/auth';
import { ElMessage } from 'element-plus';

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value?: any) => void;
  reject: (reason?: any) => void;
}> = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });

  failedQueue = [];
};

// 响应拦截器：处理 401 错误（Token 过期）
client.addResponseInterceptor({
  rejected: async (error) => {
    const originalRequest = error.config;

    // 如果是 401 错误且不是刷新 Token 请求
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // 如果正在刷新，将请求加入队列
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return client.request(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        // 调用刷新 Token API
        const { accessToken } = await refreshTokenApi();

        // 更新 store 中的 Token
        const accessStore = useAccessStore();
        accessStore.setAccessToken(accessToken);

        // 处理队列中的请求
        processQueue(null, accessToken);

        // 重试原请求
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        return client.request(originalRequest);
      } catch (refreshError) {
        // Refresh Token 也过期了，跳转到登录页
        processQueue(refreshError, null);

        const authStore = useAuthStore();
        await authStore.logout();

        ElMessage.error('登录已过期，请重新登录');
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  },
});
```

---

### ✅ Story 8.1-8.5 实施经验（已整合）

文档中已包含前置故事的关键教训：
- ✅ API 响应适配器配置（request.ts）
- ✅ 错误处理最佳实践
- ✅ 国际化 key 完整性检查
- ✅ Spring Boot 3.2 最佳实践
- ✅ BCrypt 密码加密（strength=10）
- ✅ JWT Token 签名算法（HS256）
- ✅ Redis TTL 设置（7天）
- ✅ HttpOnly Cookie 防 XSS
- ✅ Token 自动刷新机制

---

### 🧪 集成测试示例

#### Auth Controller Integration Test

```java
// backend/ai-code-review-api/src/test/java/com/aicodereview/api/controller/AuthControllerIntegrationTest.java

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AuthControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void setup() {
        userRepository.deleteAll();

        // 创建测试用户
        User admin = new User();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setEmail("admin@test.com");
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        userRepository.save(admin);
    }

    @Test
    public void testLogin_Success() {
        // 登录请求
        LoginRequest request = new LoginRequest("admin", "admin123");
        ResponseEntity<ApiResponse<LoginResult>> response = restTemplate.postForEntity(
                "/api/v1/auth/login",
                request,
                new ParameterizedTypeReference<ApiResponse<LoginResult>>() {}
        );

        // 验证响应
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData().getAccessToken()).isNotNull();
        assertThat(response.getBody().getData().getRefreshToken()).isNotNull();
    }

    @Test
    public void testAccessProtectedApi_WithToken() {
        // 先登录获取 Token
        LoginRequest loginRequest = new LoginRequest("admin", "admin123");
        ApiResponse<LoginResult> loginResponse = restTemplate.postForObject(
                "/api/v1/auth/login",
                loginRequest,
                new ParameterizedTypeReference<ApiResponse<LoginResult>>() {}
        );

        String token = loginResponse.getData().getAccessToken();

        // 访问受保护 API
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<ApiResponse<List<ProjectDTO>>> response = restTemplate.exchange(
                "/api/v1/projects",
                HttpMethod.GET,
                entity,
                new ParameterizedTypeReference<ApiResponse<List<ProjectDTO>>>() {}
        );

        // 验证响应
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
    }
}
```

---

### 🏗️ 架构约束与技术要求

#### 技术栈版本

| 技术 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 3.2.2 | 框架版本 |
| Spring Security | 6.x | 认证授权框架 |
| JJWT | 0.12.5 | JWT 生成和验证 |
| BCrypt | - | 密码加密（内置） |
| PostgreSQL | 18-alpine | 用户数据存储 |
| Redis | 7-alpine | RefreshToken 黑名单 |

#### 安全最佳实践

1. **密码存储**：使用 BCrypt，strength=10，永不存储明文密码
2. **Token 签名**：使用 HS256 算法，Secret 至少 256 bits
3. **Token 过期**：accessToken=1小时，refreshToken=7天
4. **Token 存储**：accessToken 内存存储，refreshToken HttpOnly Cookie
5. **黑名单机制**：登出后 refreshToken 加入 Redis 黑名单
6. **HTTPS 传输**：生产环境强制 HTTPS（防止 Token 被窃取）
7. **CORS 配置**：限制允许的 Origin（防止 CSRF）
8. **Rate Limiting**：登录接口限流（防止暴力破解）

---

### 📚 参考资料与文档链接

- **Spring Security 6 文档**: https://spring.io/projects/spring-security
- **JJWT 文档**: https://github.com/jwtk/jjwt
- **JWT 标准（RFC 7519）**: https://datatracker.ietf.org/doc/html/rfc7519
- **BCrypt 算法**: https://en.wikipedia.org/wiki/Bcrypt
- **OWASP 认证备忘单**: https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html
- **Story 8.1 实施记录**: `_bmad-output/implementation-artifacts/8-1-project-management-interface.md`
- **Architecture 文档**: `_bmad-output/planning-artifacts/architecture.md#Authentication & Security`

---

### Project Structure Notes

- 所有新后端文件在 `backend/ai-code-review-api/src/main/java/com/aicodereview/api/security/` 下创建
- User 实体在 `backend/ai-code-review-repository/src/main/java/com/aicodereview/repository/entity/` 下创建
- AuthService 在 `backend/ai-code-review-service/src/main/java/com/aicodereview/service/auth/` 下创建
- 前端认证相关文件已存在于 `frontend/apps/web-ele/src/` 下，需要更新集成

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.6]
- [Source: _bmad-output/planning-artifacts/architecture.md#Authentication & Security]
- [Source: backend/ai-code-review-api - 现有 Controller 结构]
- [Source: frontend/apps/web-ele/src/views/_core/authentication - Vben Admin 认证组件]
- [Source: frontend/apps/web-ele/src/store/auth.ts - 现有认证 Store]
- [Source: frontend/apps/web-ele/src/api/core/auth.ts - 现有认证 API]

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List