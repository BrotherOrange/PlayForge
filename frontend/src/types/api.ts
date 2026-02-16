// 通用 API 响应包装
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
}

// 认证
export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
}

export interface RegisterRequest {
  phone: string;
  password: string;
  nickname?: string | null;
  avatarUrl?: string | null;
  bio?: string | null;
}

// 用户资料
export interface UserProfile {
  id: number;
  phone: string;
  nickname: string | null;
  avatarUrl: string | null;
  avatarKey: string | null;
  bio: string | null;
  createdAt: string;
}

export interface UpdateProfileRequest {
  nickname?: string;
  bio?: string;
  avatarUrl?: string | null;
}

// OSS 上传策略
export interface OssPolicy {
  host: string;
  policy: string;
  signature: string;
  accessKeyId: string;
  key: string;
  expire: number;
}
