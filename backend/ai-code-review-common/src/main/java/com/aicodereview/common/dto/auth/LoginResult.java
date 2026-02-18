package com.aicodereview.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO（包含 JWT Token 信息和用户信息）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult {

    /** JWT Access Token（短期有效，1小时） */
    private String accessToken;

    /** JWT Refresh Token（长期有效，7天） */
    private String refreshToken;

    /** Token 类型（固定为 "Bearer"） */
    @Builder.Default
    private String tokenType = "Bearer";

    /** accessToken 过期时间（秒） */
    private long expiresIn;

    /** 用户信息 */
    private UserInfoDTO user;
}
