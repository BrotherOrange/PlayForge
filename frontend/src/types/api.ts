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

// 用户资料（id 是 Java Long，Jackson 序列化为 string）
export interface UserProfile {
  id: string;
  phone: string;
  nickname: string | null;
  avatarUrl: string | null;
  avatarKey: string | null;
  bio: string | null;
  isAdmin: boolean;
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

// AI Agent（id/threadId 是 Java Long → string）
export interface AgentDefinition {
  id: string;
  name: string;
  displayName: string;
  description: string;
  provider: string;
  modelName: string;
  threadId?: string;
  parentThreadId?: string;
  isActive?: boolean;
  createdAt: string;
}

// Agent 会话
export interface AgentThread {
  id: string;
  agentId: string;
  title: string;
  status: string;
  messageCount: number;
  lastMessageAt: string | null;
  createdAt: string;
}

export interface CreateThreadRequest {
  agentId: string;
  title?: string;
}

// 创建 Agent + Thread 请求
export interface CreateAgentWithThreadRequest {
  provider: string;
  modelName: string;
  displayName?: string;
}

// 创建 Agent + Thread 响应
export interface CreateAgentWithThreadResponse {
  agent: AgentDefinition;
  thread: AgentThread;
}

// Agent 消息
export interface AgentMessage {
  id: string;
  role: 'user' | 'assistant' | 'system' | 'tool';
  content: string;
  toolName: string | null;
  tokenCount: number;
  createdAt: string;
}

// WebSocket 消息
export interface WsClientMessage {
  type: 'message' | 'cancel';
  content?: string;
}

export interface WsServerMessage {
  type: 'token' | 'thinking' | 'done' | 'error';
  content?: string;
}

// SSE 进度事件
export interface ChatProgressEvent {
  type: 'token' | 'thinking' | 'progress' | 'response' | 'done' | 'error';
  content?: string;
}

export interface ReviewBlockData {
  blockId: string;
  blockType: string;
  markdown: string;
  text: string;
}

export interface PublicReviewCreateResult {
  publicId: string;
  status: string;
  title: string;
}

export interface PublicReviewTaskSummary {
  publicId: string;
  title: string;
  provider: string;
  modelName: string;
  status: string;
  documentCount: number;
  hasOverallReport: boolean;
  errorMessage: string | null;
  createdAt: string | null;
  updatedAt: string | null;
}

export interface PublicReviewFileItem {
  title?: string;
  originalFilename: string;
  contentType: string;
  objectKey: string;
}

export interface PublicReviewCreateRequest {
  taskTitle?: string;
  provider: string;
  modelName: string;
  fileItems: PublicReviewFileItem[];
  textItems: PublicReviewTextEntry[];
}

export interface PublicReviewRoleRunDetail {
  id: string;
  roleKey: string;
  roleName: string;
  roleColor: string;
  status: string;
  annotationCount: number;
  errorMessage: string | null;
  completedAt: string | null;
}

export interface PublicReviewAnnotationDetail {
  id: string;
  roleRunId: string;
  roleKey: string;
  roleName: string;
  roleColor: string;
  annotationType: 'question' | 'note' | 'opinion' | string;
  priority: 'high' | 'medium' | 'low' | string;
  blockId: string;
  startOffset: number;
  endOffset: number;
  quoteText: string;
  title: string;
  content: string;
  createdAt: string | null;
}

export interface PublicReviewDocumentDetail {
  id: string;
  sourceType: 'file' | 'text' | string;
  sortOrder: number;
  title: string;
  originalFilename: string | null;
  status: string;
  warnings: string[];
  summaryMarkdown: string | null;
  errorMessage: string | null;
  blocks: ReviewBlockData[];
  roleRuns: PublicReviewRoleRunDetail[];
  annotations: PublicReviewAnnotationDetail[];
}

export interface PublicReviewReportHistoryDetail {
  id: string;
  reportScope: string;
  title: string;
  provider: string;
  modelName: string;
  reportMarkdown: string;
  createdAt: string | null;
}

export interface PublicReviewTaskDetail {
  publicId: string;
  title: string;
  provider: string;
  modelName: string;
  status: string;
  documentCount: number;
  overallReportMarkdown: string | null;
  errorMessage: string | null;
  createdAt: string | null;
  documents: PublicReviewDocumentDetail[];
  reportHistory: PublicReviewReportHistoryDetail[];
}

export interface PublicReviewSnapshotEvent {
  type?: 'snapshot' | string;
  task?: PublicReviewTaskDetail;
}

export interface PublicReviewTextEntry {
  title: string;
  content: string;
}
