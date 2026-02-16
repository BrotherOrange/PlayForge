import { AxiosResponse } from 'axios';
import client from './client';
import { ApiResponse, UserProfile, UpdateProfileRequest } from '../types/api';

export const getProfile = (): Promise<AxiosResponse<ApiResponse<UserProfile>>> =>
  client.get('/user/profile');

export const updateProfile = (data: UpdateProfileRequest): Promise<AxiosResponse<ApiResponse<UserProfile>>> =>
  client.put('/user/profile', data);
