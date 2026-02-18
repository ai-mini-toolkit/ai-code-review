import { baseRequestClient, requestClient } from '#/api/request';

export namespace AuthApi {
  /** 登录接口参数 */
  export interface LoginParams {
    password?: string;
    username?: string;
  }

  /** 用户信息（后端 UserInfoDTO） */
  export interface UserInfo {
    id: number;
    username: string;
    email: string;
    realName?: string;
    avatar?: string;
    role: 'ADMIN' | 'USER';
    roles: string[];
    enabled: boolean;
    homePath: string;
  }

  /** 登录接口返回值（后端 LoginResult） */
  export interface LoginResult {
    accessToken: string;
    refreshToken?: string;
    tokenType: string;
    expiresIn: number;
    user: UserInfo;
  }

  /** 刷新 Token 返回值（Vben Admin 兼容格式 — data 字段存放新 token） */
  export interface RefreshTokenResult {
    data: string;
    status: number;
  }
}

/**
 * 登录
 */
export async function loginApi(data: AuthApi.LoginParams) {
  return requestClient.post<AuthApi.LoginResult>('/api/v1/auth/login', data);
}

/**
 * 刷新 accessToken（withCredentials 确保携带 HttpOnly Cookie 中的 refreshToken）
 */
export async function refreshTokenApi() {
  return baseRequestClient.post<AuthApi.RefreshTokenResult>(
    '/api/v1/auth/refresh',
    {},
    { withCredentials: true },
  );
}

/**
 * 退出登录
 */
export async function logoutApi() {
  return baseRequestClient.post('/api/v1/auth/logout', {}, { withCredentials: true });
}

/**
 * 获取用户权限码（角色列表）
 */
export async function getAccessCodesApi() {
  return requestClient.get<string[]>('/api/v1/auth/codes');
}

/**
 * 获取当前用户信息（/api/v1/auth/me）
 */
export async function getUserInfoApi() {
  return requestClient.get<AuthApi.UserInfo>('/api/v1/auth/me');
}
