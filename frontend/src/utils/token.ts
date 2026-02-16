const ACCESS_TOKEN_KEY = 'playforge_access_token';
const REFRESH_TOKEN_KEY = 'playforge_refresh_token';

export const getAccessToken = (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY);
export const setAccessToken = (token: string): void => localStorage.setItem(ACCESS_TOKEN_KEY, token);

export const getRefreshToken = (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY);
export const setRefreshToken = (token: string): void => localStorage.setItem(REFRESH_TOKEN_KEY, token);

export const setTokens = (accessToken: string, refreshToken: string): void => {
  setAccessToken(accessToken);
  setRefreshToken(refreshToken);
};

export const clearTokens = (): void => {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};
