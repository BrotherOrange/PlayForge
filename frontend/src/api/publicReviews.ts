import axios from 'axios';
import { ApiResponse, PublicReviewTaskDetail } from '../types/api';

const publicReviewClient = axios.create({
  baseURL: '/api/public',
  timeout: 600000,
});

export const getPublicReview = (publicId: string) =>
  publicReviewClient.get<ApiResponse<PublicReviewTaskDetail>>(`/reviews/${publicId}`);

export const generatePublicReviewReport = (publicId: string) =>
  publicReviewClient.post<ApiResponse<PublicReviewTaskDetail>>(`/reviews/${publicId}/overall-report`);

export const createPublicReviewEventSource = (publicId: string) =>
  new EventSource(`/api/public/reviews/${encodeURIComponent(publicId)}/stream`);
