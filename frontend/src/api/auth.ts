import { AxiosResponse } from 'axios';
import client from './client';
import { ApiResponse, AuthTokens, RegisterRequest } from '../types/api';

export const login = (phone: string, password: string): Promise<AxiosResponse<ApiResponse<AuthTokens>>> =>
  client.post('/auth/login', { phone, password });

export const register = (data: RegisterRequest): Promise<AxiosResponse<ApiResponse<AuthTokens>>> =>
  client.post('/auth/register', data);

export const logout = (refreshToken: string): Promise<AxiosResponse<ApiResponse<null>>> =>
  client.post('/auth/logout', { refreshToken });
