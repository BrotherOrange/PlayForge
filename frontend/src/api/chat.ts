import client from './client';
import { getAccessToken } from '../utils/token';
import {
  ApiResponse,
  AgentDefinition,
  AgentThread,
  AgentMessage,
  CreateThreadRequest,
  CreateAgentWithThreadRequest,
  CreateAgentWithThreadResponse,
  ChatProgressEvent,
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
    { message },
    { timeout: 300_000 }
  );

/**
 * SSE-based chat with progress events.
 * Streams token/thinking/progress/response/done/error events from the server.
 */
export const chatThreadSSE = async (
  threadId: string,
  message: string,
  onEvent: (event: ChatProgressEvent) => void,
  signal?: AbortSignal
): Promise<void> => {
  const token = getAccessToken();
  const response = await fetch(`/api/agent-threads/${threadId}/chat-progress`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message }),
    signal,
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`);
  }

  const reader = response.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    buffer += decoder.decode(value, { stream: true });

    // SSE events are separated by double newlines
    const parts = buffer.split('\n\n');
    buffer = parts.pop() || '';

    for (const part of parts) {
      const data = part
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim())
        .join('\n')
        .trim();

      if (!data) continue;

      try {
        const event = JSON.parse(data) as ChatProgressEvent;
        onEvent(event);
      } catch {
        // Ignore parse errors
      }
    }
  }
};
