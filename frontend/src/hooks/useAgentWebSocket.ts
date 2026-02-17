import { useEffect, useRef, useCallback } from 'react';
import { getAccessToken } from '../utils/token';
import { WsServerMessage } from '../types/api';

interface UseAgentWebSocketOptions {
  threadId: string | null;
  onToken: (content: string) => void;
  onDone: () => void;
  onError: (message: string) => void;
}

export function useAgentWebSocket({ threadId, onToken, onDone, onError }: UseAgentWebSocketOptions) {
  const wsRef = useRef<WebSocket | null>(null);
  const callbacksRef = useRef({ onToken, onDone, onError });
  callbacksRef.current = { onToken, onDone, onError };

  useEffect(() => {
    if (!threadId) return;

    const token = getAccessToken();
    if (!token) return;

    // Dev: connect directly to backend (CRA proxy doesn't support WS)
    // Prod: derive protocol from page URL (http→ws, https→wss)
    const isDev = process.env.NODE_ENV === 'development';
    const protocol = isDev ? 'ws' : (window.location.protocol === 'https:' ? 'wss' : 'ws');
    const host = isDev ? 'localhost:8080' : window.location.host;
    const url = `${protocol}://${host}/ws/agent-chat?token=${encodeURIComponent(token)}&threadId=${threadId}`;

    const ws = new WebSocket(url);
    wsRef.current = ws;

    ws.onmessage = (event) => {
      try {
        const msg: WsServerMessage = JSON.parse(event.data);
        switch (msg.type) {
          case 'token':
            callbacksRef.current.onToken(msg.content || '');
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
      callbacksRef.current.onError('WebSocket connection error');
    };

    return () => {
      ws.close();
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
