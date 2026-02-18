import client from './client';
import {
  ApiResponse,
  AgentDefinition,
  AgentThread,
  AgentMessage,
  CreateThreadRequest,
  CreateAgentWithThreadRequest,
  CreateAgentWithThreadResponse,
} from '../types/api';

export const listAgents = () =>
  client.get<ApiResponse<AgentDefinition[]>>('/agents');

export const createAgentWithThread = (data: CreateAgentWithThreadRequest) =>
  client.post<ApiResponse<CreateAgentWithThreadResponse>>('/agents/with-thread', data);

export const createThread = (data: CreateThreadRequest) =>
  client.post<ApiResponse<AgentThread>>('/agent-threads', data);

export const listThreads = (agentId?: string) =>
  client.get<ApiResponse<AgentThread[]>>('/agent-threads', {
    params: agentId ? { agentId } : undefined,
  });

export const deleteAgent = (id: string) =>
  client.delete<ApiResponse<void>>(`/agents/${id}`);

export const deleteThread = (id: string) =>
  client.delete<ApiResponse<void>>(`/agent-threads/${id}`);

export const getMessages = (threadId: string, limit = 50, offset = 0, signal?: AbortSignal) =>
  client.get<ApiResponse<AgentMessage[]>>(`/agent-threads/${threadId}/messages`, {
    params: { limit, offset },
    signal,
  });

export const chatThread = (threadId: string, message: string) =>
  client.post<ApiResponse<{ threadId: string; content: string }>>(
    `/agent-threads/${threadId}/chat`,
    { message }
  );
