package com.aicodereview.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新 Token 响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenResult {

    /** 新的 Access Token */
    private String accessToken;

    /** 新 Token 过期时间（秒） */
    private long expiresIn;
}
