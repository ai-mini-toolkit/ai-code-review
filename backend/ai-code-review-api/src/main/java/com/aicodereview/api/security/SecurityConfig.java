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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 6 配置
 * - JWT 无状态认证
 * - 角色权限控制（ADMIN/USER）
 * - CORS 配置（允许前端访问）
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（无状态 JWT 认证不需要 CSRF 保护）
            .csrf(AbstractHttpConfigurer::disable)

            // 禁用 HTTP Basic 和 Form Login（防止 WWW-Authenticate 响应头干扰 JWT 认证）
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)

            // 配置 CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 无状态 Session（JWT 不使用 Session）
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 配置访问规则
            .authorizeHttpRequests(auth -> auth
                // 白名单：认证接口无需 Token（仅 login/refresh/logout/register）
                // /auth/me 和 /auth/codes 需要认证，不在白名单中
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                        "/api/v1/auth/logout", "/api/v1/auth/register").permitAll()

                // 白名单：Webhook 接口（使用签名验证）
                .requestMatchers("/api/v1/webhook/**", "/api/webhook/**").permitAll()

                // 白名单：Actuator 监控端点（Prometheus, 健康检查等）
                .requestMatchers("/actuator/**").permitAll()

                // 查询接口：所有已认证用户可访问
                .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()

                // 写操作接口：仅 ADMIN 角色可访问（在 @Valid 之前执行，确保 403 优先于 422）
                .requestMatchers(HttpMethod.POST, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/**").hasRole("ADMIN")

                // 其他所有请求需要认证
                .anyRequest().authenticated()
            )

            // 配置认证失败处理
            .exceptionHandling(exception ->
                exception.authenticationEntryPoint(jwtAuthenticationEntryPoint)
            )

            // 添加 JWT 过滤器（在 UsernamePasswordAuthenticationFilter 之前）
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
