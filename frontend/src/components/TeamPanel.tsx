import { useState, useEffect, useCallback, useRef, KeyboardEvent } from 'react';
import {
  TeamOutlined,
  RightOutlined,
  DownOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { chatThread, getMessages } from '../api/chat';
import { getAgentShortLabel, getAgentColor } from '../constants/agentTypes';
import { getAccessToken } from '../utils/token';
import { AgentDefinition, AgentMessage, WsServerMessage } from '../types/api';

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

interface TeamPanelProps {
  subAgents: AgentDefinition[];
  onClose: () => void;
}

interface SubAgentCardState {
  expanded: boolean;
  messages: AgentMessage[];
  loading: boolean;
  loaded: boolean;
  inputValue: string;
  sending: boolean;
}

const MESSAGE_FETCH_LIMIT = 200;

const TeamPanel = ({ subAgents, onClose }: TeamPanelProps) => {
  const [cardStates, setCardStates] = useState<Record<string, SubAgentCardState>>({});
  const cardStatesRef = useRef(cardStates);
  const panelBodyRef = useRef<HTMLDivElement | null>(null);
  const shouldStickPanelBottomRef = useRef(true);
  const prevSubAgentCountRef = useRef(0);
  const messageContainerRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const streamSocketsRef = useRef<Record<string, WebSocket | null>>({});

  cardStatesRef.current = cardStates;

  useEffect(() => {
    return () => {
      Object.values(streamSocketsRef.current).forEach((socket) => {
        if (socket && socket.readyState === WebSocket.OPEN) {
          socket.close(1000, 'panel-unmount');
        }
      });
      streamSocketsRef.current = {};
    };
  }, []);

  // Reset card states when sub-agents change
  useEffect(() => {
    setCardStates((prev) => {
      const next: Record<string, SubAgentCardState> = {};
      for (const agent of subAgents) {
        next[agent.id] = prev[agent.id] || {
          expanded: false,
          messages: [],
          loading: false,
          loaded: false,
          inputValue: '',
          sending: false,
        };
      }
      return next;
    });
  }, [subAgents]);

  const fetchMessages = useCallback((agentId: string, threadId: string, showLoading = false) => {
    setCardStates((p) => {
      const current = p[agentId];
      if (!current) return p;
      if (!showLoading && current.sending) return p;
      return {
        ...p,
        [agentId]: { ...current, loading: showLoading || current.loading },
      };
    });

    getMessages(threadId, MESSAGE_FETCH_LIMIT, 0)
      .then((res) => {
        setCardStates((p) => {
          const current = p[agentId];
          if (!current) return p;
          if (!showLoading && current.sending) return p;
          return {
            ...p,
            [agentId]: {
              ...current,
              messages: res.data.data,
              loading: false,
              loaded: true,
            },
          };
        });
      })
      .catch(() => {
        setCardStates((p) => {
          const current = p[agentId];
          if (!current) return p;
          return {
            ...p,
            [agentId]: { ...current, loading: false, loaded: true },
          };
        });
      });
  }, []);

  const refreshMessagesWithRetry = useCallback(
    (agentId: string, threadId: string, retries = 1) => {
      fetchMessages(agentId, threadId, false);
      for (let i = 1; i <= retries; i += 1) {
        window.setTimeout(() => fetchMessages(agentId, threadId, false), 450 * i);
      }
    },
    [fetchMessages]
  );

  const toggleCard = useCallback(
    (agent: AgentDefinition) => {
      setCardStates((prev) => {
        const current = prev[agent.id];
        if (!current) return prev;
        const willExpand = !current.expanded;

        if (willExpand && !current.loaded && agent.threadId) {
          setTimeout(() => fetchMessages(agent.id, agent.threadId!, true), 0);
        }

        return {
          ...prev,
          [agent.id]: { ...current, expanded: willExpand },
        };
      });
    },
    [fetchMessages]
  );

  // Poll all sub-agent messages so card status/preview refresh right after task dispatch.
  useEffect(() => {
    if (subAgents.length === 0) return;

    const refresh = () => {
      for (const agent of subAgents) {
        if (agent.threadId) {
          fetchMessages(agent.id, agent.threadId, false);
        }
      }
    };

    refresh();
    const timer = setInterval(refresh, 2000);
    return () => clearInterval(timer);
  }, [subAgents, fetchMessages]);

  // Keep expanded message list pinned to bottom.
  useEffect(() => {
    for (const agent of subAgents) {
      const state = cardStates[agent.id];
      if (!state?.expanded) continue;
      const container = messageContainerRefs.current[agent.id];
      if (container) {
        container.scrollTop = container.scrollHeight;
      }
    }
  }, [subAgents, cardStates]);

  // Keep right-side sub-agent panel pinned near bottom unless user scrolls upward manually.
  useEffect(() => {
    const panel = panelBodyRef.current;
    if (!panel) return;

    const hasNewSubAgent = subAgents.length > prevSubAgentCountRef.current;
    prevSubAgentCountRef.current = subAgents.length;

    if (hasNewSubAgent || shouldStickPanelBottomRef.current) {
      panel.scrollTop = panel.scrollHeight;
    }
  }, [subAgents, cardStates]);

  const handlePanelBodyScroll = useCallback(() => {
    const panel = panelBodyRef.current;
    if (!panel) return;
    const distanceToBottom = panel.scrollHeight - panel.scrollTop - panel.clientHeight;
    shouldStickPanelBottomRef.current = distanceToBottom <= 24;
  }, []);

  const updateDraft = useCallback((agentId: string, value: string) => {
    setCardStates((prev) => {
      const current = prev[agentId];
      if (!current) return prev;
      return {
        ...prev,
        [agentId]: { ...current, inputValue: value },
      };
    });
  }, []);

  const sendMessageToSubAgent = useCallback(async (agent: AgentDefinition) => {
    if (!agent.threadId) return;

    const state = cardStatesRef.current[agent.id];
    const draft = state?.inputValue?.trim() || '';
    if (!draft || state?.sending) {
      return;
    }

    const optimisticUserMsg: AgentMessage = {
      id: `tmp-user-${Date.now()}`,
      role: 'user',
      content: draft,
      toolName: null,
      tokenCount: 0,
      createdAt: new Date().toISOString(),
    };
    const streamAssistantMsgId = `tmp-assistant-stream-${Date.now()}`;
    const streamingAssistantMsg: AgentMessage = {
      id: streamAssistantMsgId,
      role: 'assistant',
      content: '',
      toolName: null,
      tokenCount: 0,
      createdAt: new Date().toISOString(),
    };

    setCardStates((prev) => {
      const current = prev[agent.id];
      if (!current) return prev;
      return {
        ...prev,
        [agent.id]: {
          ...current,
          expanded: true,
          loaded: true,
          sending: true,
          inputValue: '',
          messages: [...current.messages, optimisticUserMsg, streamingAssistantMsg],
        },
      };
    });

    const setSending = (sending: boolean) => {
      setCardStates((prev) => {
        const current = prev[agent.id];
        if (!current) return prev;
        return {
          ...prev,
          [agent.id]: {
            ...current,
            sending,
          },
        };
      });
    };

    const replaceStreamMessage = (content: string) => {
      setCardStates((prev) => {
        const current = prev[agent.id];
        if (!current) return prev;
        const nextMessages = current.messages.map((msg) =>
          msg.id === streamAssistantMsgId ? { ...msg, content } : msg
        );
        return {
          ...prev,
          [agent.id]: {
            ...current,
            messages: nextMessages,
          },
        };
      });
    };

    const appendStreamToken = (token: string) => {
      setCardStates((prev) => {
        const current = prev[agent.id];
        if (!current) return prev;
        const nextMessages = current.messages.map((msg) =>
          msg.id === streamAssistantMsgId ? { ...msg, content: `${msg.content}${token}` } : msg
        );
        return {
          ...prev,
          [agent.id]: {
            ...current,
            messages: nextMessages,
          },
        };
      });
    };

    const fallbackToSyncChat = async () => {
      try {
        const res = await chatThread(agent.threadId!, draft);
        replaceStreamMessage(res.data.data.content);
        setSending(false);
        refreshMessagesWithRetry(agent.id, agent.threadId!, 1);
      } catch {
        message.error('Sub-agent message failed, please retry');
        setCardStates((prev) => {
          const current = prev[agent.id];
          if (!current) return prev;
          return {
            ...prev,
            [agent.id]: {
              ...current,
              sending: false,
              inputValue: draft,
            },
          };
        });
      }
    };

    const token = getAccessToken();
    if (!token) {
      await fallbackToSyncChat();
      return;
    }

    const existingSocket = streamSocketsRef.current[agent.id];
    if (existingSocket && existingSocket.readyState === WebSocket.OPEN) {
      existingSocket.close(1000, 'new-message');
    }

    const wsBaseUrl = resolveWebSocketBaseUrl();
    const wsUrl = `${wsBaseUrl}/ws/agent-chat?threadId=${encodeURIComponent(agent.threadId)}`;

    let completed = false;
    const ws = new WebSocket(wsUrl, ['bearer', token]);
    streamSocketsRef.current[agent.id] = ws;

    ws.onopen = () => {
      ws.send(JSON.stringify({ type: 'message', content: draft }));
    };

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as WsServerMessage;
        if (payload.type === 'token') {
          appendStreamToken(payload.content || '');
          return;
        }
        if (payload.type === 'done') {
          completed = true;
          setSending(false);
          ws.close(1000, 'done');
          refreshMessagesWithRetry(agent.id, agent.threadId!, 1);
          return;
        }
        if (payload.type === 'error') {
          completed = true;
          message.error(payload.content || 'Sub-agent stream error');
          ws.close(1002, 'stream-error');
          fallbackToSyncChat();
        }
      } catch {
        // ignore non-json payloads
      }
    };

    ws.onerror = () => {
      if (completed) return;
      completed = true;
      ws.close(1002, 'socket-error');
      fallbackToSyncChat();
    };

    ws.onclose = () => {
      if (streamSocketsRef.current[agent.id] === ws) {
        streamSocketsRef.current[agent.id] = null;
      }
      if (!completed) {
        setSending(false);
      }
    };
  }, [refreshMessagesWithRetry]);

  const handleComposerKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>, agent: AgentDefinition) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      sendMessageToSubAgent(agent);
    }
  };

  if (subAgents.length === 0) return null;

  return (
    <div className="sf-team-panel">
      <div className="sf-team-panel-header">
        <TeamOutlined style={{ marginRight: 8 }} />
        <span>Sub-Agents ({subAgents.length})</span>
        <button className="sf-team-panel-close" onClick={onClose}>
          <CloseOutlined />
        </button>
      </div>

      <div
        className="sf-team-panel-body"
        ref={panelBodyRef}
        onScroll={handlePanelBodyScroll}
      >
        {subAgents.map((agent) => {
          const state = cardStates[agent.id];
          const isExpanded = state?.expanded ?? false;
          const messages = state?.messages ?? [];
          const isLoading = state?.loading ?? false;
          const isSending = state?.sending ?? false;
          const inputValue = state?.inputValue ?? '';
          const hasMessages = messages.length > 0;
          const lastMessage = hasMessages ? messages[messages.length - 1] : null;
          const isDestroyed = agent.isActive === false;
          const isWorking = !isDestroyed && (isSending || lastMessage?.role === 'user');
          const color = getAgentColor(agent.name);

          return (
            <div
              key={agent.id}
              className={`sf-subagent-card ${isExpanded ? 'expanded' : ''} ${isDestroyed ? 'inactive' : ''}`}
            >
              <div
                className="sf-subagent-card-header"
                onClick={() => toggleCard(agent)}
              >
                <span className="sf-subagent-type-dot" style={{ background: color }} />
                <span className="sf-subagent-type-label" style={{ color }}>
                  {getAgentShortLabel(agent.name)}
                </span>
                <span className="sf-subagent-name">
                  {agent.name.split('-').pop()}
                </span>
                <span className="sf-subagent-status">
                  {isDestroyed ? (
                    <StopOutlined style={{ color: '#ef4444', fontSize: 12 }} />
                  ) : isWorking ? (
                    <ClockCircleOutlined style={{ color: '#f59e0b', fontSize: 12 }} />
                  ) : (
                    <CheckCircleOutlined style={{ color: '#34d399', fontSize: 12 }} />
                  )}
                </span>
                <span className="sf-subagent-expand-icon">
                  {isExpanded ? <DownOutlined /> : <RightOutlined />}
                </span>
              </div>

              {!isExpanded && lastMessage && (
                <div className="sf-subagent-preview">
                  {lastMessage.content.slice(0, 80)}
                  {lastMessage.content.length > 80 ? '...' : ''}
                </div>
              )}

              {isExpanded && (
                <div className="sf-subagent-card-body">
                  {isLoading && (
                    <div className="sf-subagent-loading">
                      <LoadingOutlined /> Loading...
                    </div>
                  )}

                  {!isLoading && messages.length === 0 && (
                    <div className="sf-subagent-empty">No messages yet</div>
                  )}

                  {!isLoading && messages.length > 0 && (
                    <div
                      className="sf-subagent-messages"
                      ref={(el) => {
                        messageContainerRefs.current[agent.id] = el;
                      }}
                    >
                      {messages.map((msg) => (
                        <div key={msg.id} className={`sf-subagent-msg ${msg.role}`}>
                          <div className="sf-subagent-msg-role">
                            {msg.role === 'user' ? 'Task' : 'Response'}
                          </div>
                          <div className="sf-subagent-msg-content sf-markdown">
                            <ReactMarkdown remarkPlugins={[remarkGfm]}>
                              {msg.content}
                            </ReactMarkdown>
                          </div>
                        </div>
                      ))}
                    </div>
                  )}

                  {isDestroyed ? (
                    <div className="sf-subagent-inactive-hint">Agent destroyed</div>
                  ) : (
                    <div className="sf-subagent-composer" onClick={(e) => e.stopPropagation()}>
                      <textarea
                        className="sf-subagent-input"
                        placeholder="Task"
                        rows={1}
                        value={inputValue}
                        disabled={isSending}
                        onChange={(e) => updateDraft(agent.id, e.target.value)}
                        onKeyDown={(e) => handleComposerKeyDown(e, agent)}
                      />
                      <button
                        className="sf-subagent-send-btn"
                        onClick={() => sendMessageToSubAgent(agent)}
                        disabled={!inputValue.trim() || isSending}
                      >
                        {isSending ? <LoadingOutlined /> : <SendOutlined />}
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default TeamPanel;
