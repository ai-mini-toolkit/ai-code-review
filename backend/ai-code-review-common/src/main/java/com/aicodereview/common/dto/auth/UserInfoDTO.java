package com.aicodereview.common.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDTO {

    private Long id;
    private String username;
    private String email;
    private String realName;
    private String avatar;
    private String role;        // ADMIN or USER
    private List<String> roles; // 角色数组（Vben Admin 格式）
    private boolean enabled;
    private String homePath;    // 登录后跳转首页
}
