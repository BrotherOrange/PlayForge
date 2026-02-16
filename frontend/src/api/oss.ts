import { AxiosResponse } from 'axios';
import client from './client';
import { ApiResponse, OssPolicy } from '../types/api';

export const getUploadPolicy = (directory: string = 'avatars'): Promise<AxiosResponse<ApiResponse<OssPolicy>>> =>
  client.get('/oss/policy', { params: { directory } });
