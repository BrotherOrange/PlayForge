import axios, { InternalAxiosRequestConfig } from 'axios';
import { getAccessToken, getRefreshToken, setTokens, clearTokens } from '../utils/token';
import { ApiResponse, AuthTokens } from '../types/api';

interface RetryableAxiosRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const client = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// 请求拦截器：自动附加 Bearer Token
client.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Token刷新状态
let isRefreshing = false;
let failedQueue: Array<{
  resolve: (value: AuthTokens) => void;
  reject: (reason: unknown) => void;
}> = [];

const processQueue = (error: unknown, tokens: AuthTokens | null): void => {
  failedQueue.forEach(({ resolve, reject }) => {
    if (error) {
      reject(error);
    } else {
      resolve(tokens!);
    }
  });
  failedQueue = [];
};

// 响应拦截器：自动刷新Token
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest: RetryableAxiosRequestConfig = error.config;
    const data: ApiResponse<unknown> | undefined = error.response?.data;

    // TOKEN_EXPIRED (1003)：尝试刷新
    if (data?.code === 1003 && !originalRequest._retry) {
      if (isRefreshing) {
        return new Promise<AuthTokens>((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        }).then((tokens) => {
          originalRequest.headers.Authorization = `Bearer ${tokens.accessToken}`;
          return client(originalRequest);
        });
      }

      originalRequest._retry = true;
      isRefreshing = true;

      try {
        const refreshToken = getRefreshToken();
        if (!refreshToken) {
          throw new Error('No refresh token');
        }

        // 使用原生axios避免拦截器死循环
        const res = await axios.post<ApiResponse<AuthTokens>>('/api/auth/refresh', { refreshToken });
        const tokens = res.data.data;
        setTokens(tokens.accessToken, tokens.refreshToken);
        processQueue(null, tokens);
        originalRequest.headers.Authorization = `Bearer ${tokens.accessToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearTokens();
        window.location.href = '/login';
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    // 其他认证错误 (1004/1005/1006)：清除Token并跳转登录
    if ([1004, 1005, 1006].includes(data?.code as number)) {
      clearTokens();
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default client;
