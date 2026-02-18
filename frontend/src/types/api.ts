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
  type: 'progress' | 'response' | 'done' | 'error';
  content: string;
}
