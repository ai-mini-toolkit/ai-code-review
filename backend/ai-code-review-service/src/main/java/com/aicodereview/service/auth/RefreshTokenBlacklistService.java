package com.aicodereview.service.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token 黑名单服务（基于 Redis）
 * 登出后将 Refresh Token 加入黑名单，防止 Token 重放攻击
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenBlacklistService {

    private static final String BLACKLIST_KEY_PREFIX = "auth:refresh_token:blacklist:";

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 将 Refresh Token 加入黑名单
     *
     * @param token     Refresh Token
     * @param expiresIn 过期时间（秒）
     */
    public void addToBlacklist(String token, long expiresIn) {
        if (expiresIn <= 0) {
            log.debug("Refresh token already expired, no need to blacklist");
            return;
        }
        String key = BLACKLIST_KEY_PREFIX + token;
        stringRedisTemplate.opsForValue().set(key, "revoked", Duration.ofSeconds(expiresIn));
        log.info("Added refresh token to blacklist (TTL: {}s)", expiresIn);
    }

    /**
     * 检查 Refresh Token 是否在黑名单中
     *
     * @param token Refresh Token
     * @return true 表示已被吊销
     */
    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_KEY_PREFIX + token;
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
    }
}
