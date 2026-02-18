import { useEffect, useRef, useCallback } from 'react';
import { getAccessToken } from '../utils/token';
import { WsServerMessage } from '../types/api';

interface UseAgentWebSocketOptions {
  threadId: string | null;
  onToken: (content: string) => void;
  onThinking: (content: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

const normalizeBaseUrl = (url: string): string => url.replace(/\/+$/, '');

const toWebSocketBase = (url: string): string => {
  if (url.startsWith('http://')) {
    return `ws://${url.slice('http://'.length)}`;
  }
  if (url.startsWith('https://')) {
    return `wss://${url.slice('https://'.length)}`;
  }
  return url;
};

const resolveWebSocketBaseUrl = (): string => {
  const wsBase = process.env.REACT_APP_WS_BASE_URL?.trim();
  if (wsBase) {
    return normalizeBaseUrl(toWebSocketBase(wsBase));
  }

  const apiBase = process.env.REACT_APP_API_BASE_URL?.trim();
  if (apiBase) {
    return normalizeBaseUrl(toWebSocketBase(apiBase));
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
  if (process.env.NODE_ENV === 'development') {
    const backendHost = process.env.REACT_APP_DEV_BACKEND_HOST?.trim() || window.location.hostname;
    const backendPort = process.env.REACT_APP_DEV_BACKEND_PORT?.trim() || '8080';
    return `${protocol}://${backendHost}:${backendPort}`;
  }

  return `${protocol}://${window.location.host}`;
};

export function useAgentWebSocket({
  threadId,
  onToken,
  onThinking,
  onDone,
  onError,
}: UseAgentWebSocketOptions) {
  const wsRef = useRef<WebSocket | null>(null);
  const callbacksRef = useRef({ onToken, onThinking, onDone, onError });
  const reconnectAttemptsRef = useRef(0);
  const reconnectTimerRef = useRef<number | null>(null);
  const shouldReconnectRef = useRef(false);
  callbacksRef.current = { onToken, onThinking, onDone, onError };

  useEffect(() => {
    if (!threadId) return;

    const token = getAccessToken();
    if (!token) return;

    shouldReconnectRef.current = true;

    const wsBaseUrl = resolveWebSocketBaseUrl();
    const url = `${wsBaseUrl}/ws/agent-chat?threadId=${encodeURIComponent(threadId)}`;

    const connect = () => {
      const currentToken = getAccessToken();
      if (!currentToken) {
        callbacksRef.current.onError('Authentication expired, please log in again');
        return;
      }
      const ws = new WebSocket(url, ['bearer', currentToken]);
      wsRef.current = ws;

      ws.onopen = () => {
        reconnectAttemptsRef.current = 0;
      };

      ws.onmessage = (event) => {
        try {
          const msg: WsServerMessage = JSON.parse(event.data);
          switch (msg.type) {
            case 'token':
              callbacksRef.current.onToken(msg.content || '');
              break;
            case 'thinking':
              callbacksRef.current.onThinking(msg.content || '');
              break;
            case 'done':
              callbacksRef.current.onDone();
              break;
            case 'error':
              callbacksRef.current.onError(msg.content || 'Unknown error');
              break;
          }
        } catch {
          // ignore non-JSON messages
        }
      };

      ws.onerror = () => {
        // onclose will handle reconnect and user-facing error.
      };

      ws.onclose = (event) => {
        if (!shouldReconnectRef.current || event.code === 1000) {
          return;
        }
        if (reconnectAttemptsRef.current >= 5) {
          callbacksRef.current.onError('WebSocket disconnected, please refresh and try again');
          return;
        }
        reconnectAttemptsRef.current += 1;
        const delay = Math.min(1000 * 2 ** (reconnectAttemptsRef.current - 1), 5000);
        reconnectTimerRef.current = window.setTimeout(connect, delay);
      };
    };

    connect();

    return () => {
      shouldReconnectRef.current = false;
      if (reconnectTimerRef.current !== null) {
        window.clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      const ws = wsRef.current;
      if (ws) {
        ws.close(1000, 'cleanup');
      }
      wsRef.current = null;
    };
  }, [threadId]);

  const sendMessage = useCallback((content: string) => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'message', content }));
    } else {
      callbacksRef.current.onError('WebSocket not connected, please try again');
    }
  }, []);

  const cancelStream = useCallback(() => {
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'cancel' }));
    }
  }, []);

  return { sendMessage, cancelStream };
}
