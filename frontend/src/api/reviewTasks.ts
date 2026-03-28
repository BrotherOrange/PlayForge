import { AxiosResponse } from 'axios';
import client from './client';
import {
  ApiResponse,
  OssPolicy,
  PublicReviewCreateRequest,
  PublicReviewCreateResult,
  PublicReviewTaskSummary,
} from '../types/api';

export const getReviewUploadPolicy = (): Promise<AxiosResponse<ApiResponse<OssPolicy>>> =>
  client.get('/reviews/upload-policy');

export const createReviewTask = (payload: PublicReviewCreateRequest) =>
  client.post<ApiResponse<PublicReviewCreateResult>>('/reviews', payload, {
    timeout: 120000,
  });

export const listMyReviewTasks = (limit = 20) =>
  client.get<ApiResponse<PublicReviewTaskSummary[]>>('/reviews/history', {
    params: { limit },
  });
