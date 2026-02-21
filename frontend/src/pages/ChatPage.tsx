import { useState, useEffect, useRef, useCallback, useMemo, KeyboardEvent } from 'react';
import { useOutletContext } from 'react-router-dom';
import { message, Modal } from 'antd';
import {
  PlusOutlined,
  SendOutlined,
  DeleteOutlined,
  RobotOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LockOutlined,
  TeamOutlined,
  LeftOutlined,
  LoadingOutlined,
  CopyOutlined,
  CheckOutlined,
  RightOutlined,
  DownOutlined,
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { listAgents, createAgentWithThread, deleteAgent, getMessages, getThreadProcessingStatus, chatThreadSSE } from '../api/chat';
import TeamPanel from '../components/TeamPanel';
import { getAgentLabel, getAgentColor, getAgentTypeFromName } from '../constants/agentTypes';
import { AgentDefinition, AgentMessage, UserProfile } from '../types/api';

const PROVIDER_COLORS: Record<string, string> = {
  openai: '#00d4ff',
  anthropic: '#a78bfa',
  gemini: '#34d399',
};

const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  anthropic: 'Anthropic',
  gemini: 'Gemini',
};

const AVAILABLE_MODELS = [
  { provider: 'openai', modelName: 'gpt-5.2', displayName: 'GPT-5.2' },
  { provider: 'anthropic', modelName: 'claude-sonnet-4-6', displayName: 'Claude Sonnet 4.6' },
  { provider: 'gemini', modelName: 'gemini-3-flash-preview', displayName: 'Gemini 3 Flash' },
];

const MESSAGE_FETCH_LIMIT = 200;

const hasAssistantReplyAfterLastUser = (history: AgentMessage[]): boolean => {
  for (let i = history.length - 1; i >= 0; i -= 1) {
    const msg = history[i];
    if (msg.role === 'assistant') return true;
    if (msg.role === 'user') return false;
  }
  return false;
};

const buildMessageFingerprint = (history: AgentMessage[]): string =>
  history.map((msg) => `${msg.id}:${msg.content.length}`).join('|');

const ChatPage = () => {
  const { user } = useOutletContext<{ user: UserProfile | null }>();
  const isAdmin = user?.isAdmin === true;

  // All agents (lead + sub)
  const [allAgents, setAllAgents] = useState<AgentDefinition[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentDefinition | null>(null);
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [streamingThreadId, setStreamingThreadId] = useState<string | null>(null);
  const [streamingBubbles, setStreamingBubbles] = useState<AgentMessage[]>([]);
  const [inputValue, setInputValue] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [modelPickerOpen, setModelPickerOpen] = useState(false);
  const [creatingAgent, setCreatingAgent] = useState(false);
  const [teamPanelOpen, setTeamPanelOpen] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);
  const [statusExpanded, setStatusExpanded] = useState(false);
  const [thinkingExpanded, setThinkingExpanded] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const activeThreadIdRef = useRef<string | null>(null);
  const streamingThreadIdRef = useRef<string | null>(null);
  const streamingBubblesRef = useRef<AgentMessage[]>([]);

  const activeThreadId = selectedAgent?.threadId ?? null;
  const isAnyMainStreamRunning = streamingThreadId !== null;
  const isCurrentThreadStreaming =
    !!activeThreadId && streamingThreadId === activeThreadId;

  // Derived: lead agents (no parentThreadId)
  const leadAgents = useMemo(
    () => allAgents.filter((a) => !a.parentThreadId),
    [allAgents]
  );

  // Derived: sub-agents grouped by parentThreadId
  const subAgentsMap = useMemo(() => {
    const map = new Map<string, AgentDefinition[]>();
    for (const agent of allAgents) {
      if (agent.parentThreadId) {
        const list = map.get(agent.parentThreadId) || [];
        list.push(agent);
        map.set(agent.parentThreadId, list);
      }
    }
    return map;
  }, [allAgents]);

  // Current lead agent (either selected or parent of selected sub-agent)
  const currentLeadAgent = useMemo(() => {
    if (!selectedAgent) return null;
    if (!selectedAgent.parentThreadId) return selectedAgent;
    return leadAgents.find((a) => a.threadId === selectedAgent.parentThreadId) ?? null;
  }, [selectedAgent, leadAgents]);

  // Sub-agents for the current lead agent
  const currentSubAgents = useMemo(() => {
    const threadId = currentLeadAgent?.threadId;
    if (!threadId) return [];
    return subAgentsMap.get(threadId) || [];
  }, [currentLeadAgent, subAgentsMap]);

  // Whether selected agent is a sub-agent
  const isSubAgent = !!selectedAgent?.parentThreadId;

  const lastStreamingAssistantId = useMemo(() => {
    for (let i = streamingBubbles.length - 1; i >= 0; i -= 1) {
      if (streamingBubbles[i].role === 'assistant') {
        return streamingBubbles[i].id;
      }
    }
    return null;
  }, [streamingBubbles]);

  const streamingGroups = useMemo(() => {
    const progress: AgentMessage[] = [];
    const thinking: AgentMessage[] = [];
    const assistant: AgentMessage[] = [];
    for (const b of streamingBubbles) {
      if (b.role === 'tool' && b.toolName === 'progress') progress.push(b);
      else if (b.role === 'tool' && b.toolName === 'thinking') thinking.push(b);
      else if (b.role === 'assistant') assistant.push(b);
    }
    const thinkingText = thinking.length > 0 ? thinking[thinking.length - 1].content : '';
    return { progress, thinkingText, assistant };
  }, [streamingBubbles]);

  const handleCopy = useCallback(async (id: string, content: string) => {
    try {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(content);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = content;
        textarea.style.position = 'fixed';
        textarea.style.left = '-9999px';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      setCopiedId(id);
      setTimeout(() => setCopiedId(null), 2000);
    } catch {
      message.error('Failed to copy');
    }
  }, []);

  // Auto-scroll to bottom
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    activeThreadIdRef.current = activeThreadId;
  }, [activeThreadId]);

  useEffect(() => {
    streamingThreadIdRef.current = streamingThreadId;
  }, [streamingThreadId]);

  useEffect(() => {
    streamingBubblesRef.current = streamingBubbles;
  }, [streamingBubbles]);

  useEffect(() => {
    scrollToBottom();
  }, [
    messages,
    streamingBubbles,
    isCurrentThreadStreaming,
    scrollToBottom,
  ]);

  // Load agents
  const loadAgents = useCallback(() => {
    listAgents()
      .then((res) => setAllAgents(res.data.data))
      .catch(() => { /* silent — polling retries automatically */ });
  }, []);

  // Initial load
  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  // Keep selected agent object fresh. Do NOT auto-select any conversation on page load.
  useEffect(() => {
    if (allAgents.length === 0) {
      if (selectedAgent) {
        setSelectedAgent(null);
      }
      return;
    }

    if (!selectedAgent) return;

    const updatedSelected = allAgents.find((agent) => agent.id === selectedAgent.id);
    if (!updatedSelected) {
      setSelectedAgent(null);
      return;
    }

    if (
      updatedSelected.threadId !== selectedAgent.threadId ||
      updatedSelected.parentThreadId !== selectedAgent.parentThreadId ||
      updatedSelected.displayName !== selectedAgent.displayName ||
      updatedSelected.modelName !== selectedAgent.modelName
    ) {
      setSelectedAgent(updatedSelected);
    }
  }, [allAgents, selectedAgent]);

  // Unified agent list polling: only when streaming OR team panel is open.
  // Single timer avoids overlapping intervals.
  useEffect(() => {
    const needPolling = streamingThreadId || (teamPanelOpen && !isSubAgent && currentLeadAgent?.threadId);
    if (!needPolling) return;
    const interval = streamingThreadId ? 1500 : 3000;
    loadAgents();
    const timer = window.setInterval(loadAgents, interval);
    return () => window.clearInterval(timer);
  }, [streamingThreadId, teamPanelOpen, isSubAgent, currentLeadAgent?.threadId, loadAgents]);

  // Auto-open team panel when sub-agents appear
  useEffect(() => {
    if (currentSubAgents.length > 0 && !isSubAgent) {
      setTeamPanelOpen(true);
    }
  }, [currentSubAgents.length, isSubAgent]);

  // Load messages when selected agent changes.
  // Also poll for new messages to catch updates from ongoing backend processing
  // (e.g., after page refresh while chat is still running on the server).
  useEffect(() => {
    if (!activeThreadId) {
      setMessages([]);
      return;
    }

    const controller = new AbortController();
    let lastFingerprint = '';
    getMessages(activeThreadId, MESSAGE_FETCH_LIMIT, 0, controller.signal)
      .then((res) => {
        const data = res.data.data;
        setMessages(data);
        lastFingerprint = buildMessageFingerprint(data);
      })
      .catch((error) => {
        if ((error as { code?: string }).code === 'ERR_CANCELED') return;
        setMessages([]);
      });

    let stablePolls = 0;
    let backendConfirmedIdle = false;
    let pollStopped = false;
    const pollTimer = window.setInterval(async () => {
      if (pollStopped) return;
      if (streamingThreadIdRef.current === activeThreadId) return; // SSE already handling updates
      try {
        const res = await getMessages(activeThreadId, MESSAGE_FETCH_LIMIT, 0);
        const data = res.data.data;
        const fingerprint = buildMessageFingerprint(data);
        if (fingerprint !== lastFingerprint) {
          setMessages(data);
          stablePolls = 0;
          backendConfirmedIdle = false;
        } else {
          stablePolls++;
          // After 10s of no changes, check if backend is still processing
          if (stablePolls >= 5 && !backendConfirmedIdle) {
            try {
              const statusRes = await getThreadProcessingStatus(activeThreadId);
              if (statusRes.data.data.processing) {
                stablePolls = 0;
              } else {
                backendConfirmedIdle = true;
                stablePolls = 0; // restart counter for post-idle buffer
              }
            } catch { /* treat as idle on error */ backendConfirmedIdle = true; stablePolls = 0; }
          }
          // Stop when backend confirmed idle AND stable for 30s more (no new messages)
          if (backendConfirmedIdle && stablePolls >= 15) {
            pollStopped = true;
            window.clearInterval(pollTimer);
          }
        }
        lastFingerprint = fingerprint;
      } catch { /* ignore polling errors */ }
    }, 2000);

    return () => {
      controller.abort();
      window.clearInterval(pollTimer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeThreadId]);

  const handleSelectAgent = (agent: AgentDefinition) => {
    setSelectedAgent(agent);
  };

  const handleBackToLead = () => {
    if (currentLeadAgent) {
      handleSelectAgent(currentLeadAgent);
    }
  };

  const handleNewChat = () => {
    setModelPickerOpen(true);
  };

  const handlePickModel = async (model: typeof AVAILABLE_MODELS[0]) => {
    setModelPickerOpen(false);
    setCreatingAgent(true);
    try {
      const res = await createAgentWithThread({
        provider: model.provider,
        modelName: model.modelName,
        displayName: model.displayName,
      });
      const { agent } = res.data.data;
      setAllAgents((prev) => [agent, ...prev]);
      setSelectedAgent(agent);
      setMessages([]);
    } catch {
      message.error('Failed to create conversation');
    } finally {
      setCreatingAgent(false);
    }
  };

  const handleDeleteAgent = async (agent: AgentDefinition, e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      await deleteAgent(agent.id);
      // Remove lead agent and its sub-agents (backend cascade-deletes them)
      const deletedThreadId = agent.threadId;
      setAllAgents((prev) =>
        prev.filter((a) => a.id !== agent.id && a.parentThreadId !== deletedThreadId)
      );
      if (
        selectedAgent?.id === agent.id ||
        selectedAgent?.parentThreadId === deletedThreadId
      ) {
        setSelectedAgent(null);
        setMessages([]);
      }
      setTeamPanelOpen(false);
    } catch {
      message.error('Failed to delete conversation');
    }
  };

  const fetchMessagesWithRetry = useCallback(async (threadId: string, retries = 2) => {
    let latestMessages: AgentMessage[] = [];
    for (let i = 0; i <= retries; i += 1) {
      const res = await getMessages(threadId, MESSAGE_FETCH_LIMIT, 0);
      latestMessages = res.data.data;
      const hasAssistantReply = hasAssistantReplyAfterLastUser(latestMessages);
      if (hasAssistantReply || i === retries) {
        break;
      }
      await new Promise<void>((resolve) => {
        window.setTimeout(resolve, 150 * (i + 1));
      });
    }
    return latestMessages;
  }, []);

  const handleSend = async () => {
    const content = inputValue.trim();
    if (!content || !activeThreadId || isAnyMainStreamRunning) return;

    // Optimistically add user message
    setMessages((prev) => [
      ...prev,
      {
        id: String(Date.now()),
        role: 'user',
        content,
        toolName: null,
        tokenCount: 0,
        createdAt: new Date().toISOString(),
      },
    ]);
    setInputValue('');
    setStreamingThreadId(activeThreadId);
    setStreamingBubbles([]);
    streamingBubblesRef.current = [];
    setStatusExpanded(false);
    setThinkingExpanded(false);

    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
    }

    const sendingThreadId = activeThreadId;
    let refreshedFromDb = false;
    try {
      await chatThreadSSE(sendingThreadId, content, (event) => {
        const payload = event.content ?? '';
        switch (event.type) {
          case 'progress':
            if (payload) {
              setStreamingBubbles((prev) => [
                ...prev,
                {
                  id: `tmp-progress-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                  role: 'tool',
                  content: payload,
                  toolName: 'progress',
                  tokenCount: 0,
                  createdAt: new Date().toISOString(),
                },
              ]);
            }
            break;
          case 'thinking':
            if (payload) {
              setStreamingBubbles((prev) => {
                const last = prev[prev.length - 1];
                if (last && last.role === 'tool' && last.toolName === 'thinking') {
                  const next = [...prev];
                  next[next.length - 1] = {
                    ...last,
                    content: `${last.content}${payload}`,
                  };
                  return next;
                }
                return [
                  ...prev,
                  {
                    id: `tmp-thinking-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                    role: 'tool',
                    content: payload,
                    toolName: 'thinking',
                    tokenCount: 0,
                    createdAt: new Date().toISOString(),
                  },
                ];
              });
            }
            break;
          case 'token':
            if (payload) {
              setStreamingBubbles((prev) => {
                const last = prev[prev.length - 1];
                if (last && last.role === 'assistant') {
                  const next = [...prev];
                  next[next.length - 1] = {
                    ...last,
                    content: `${last.content}${payload}`,
                  };
                  return next;
                }
                return [
                  ...prev,
                  {
                    id: `tmp-assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                    role: 'assistant',
                    content: payload,
                    toolName: null,
                    tokenCount: 0,
                    createdAt: new Date().toISOString(),
                  },
                ];
              });
            }
            break;
          case 'response':
            if (payload) {
              setStreamingBubbles((prev) => [
                ...prev,
                {
                  id: `tmp-assistant-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
                  role: 'assistant',
                  content: payload,
                  toolName: null,
                  tokenCount: 0,
                  createdAt: new Date().toISOString(),
                },
              ]);
            }
            break;
          case 'error':
            message.error(event.content || 'Failed to get response');
            break;
          default:
            break;
        }
      });

      // Refresh messages from DB to get accurate IDs and timestamps
      const latestMessages = await fetchMessagesWithRetry(sendingThreadId, 2);
      const latestLiveAssistant = [...streamingBubblesRef.current]
        .reverse()
        .find((m) => m.role === 'assistant');
      const fallbackContent = latestLiveAssistant?.content.trim() || '';
      const hasAssistantReplyInDb = hasAssistantReplyAfterLastUser(latestMessages);
      const mergedMessages =
        !hasAssistantReplyInDb && fallbackContent
          ? [
              ...latestMessages,
              {
                id: `tmp-assistant-final-${Date.now()}`,
                role: 'assistant' as const,
                content: fallbackContent,
                toolName: null,
                tokenCount: 0,
                createdAt: new Date().toISOString(),
              },
            ]
          : latestMessages;

      if (activeThreadIdRef.current === sendingThreadId) {
        setMessages(mergedMessages);
      }
      refreshedFromDb = true;
      loadAgents();
    } catch {
      message.error('Failed to get response, please try again');
    } finally {
      if (!refreshedFromDb && activeThreadIdRef.current === sendingThreadId) {
        const fallbackBubbles = streamingBubblesRef.current;
        if (fallbackBubbles.length > 0) {
          setMessages((prev) => [...prev, ...fallbackBubbles]);
        }
      }
      setStreamingThreadId(null);
      setStreamingBubbles([]);
      streamingBubblesRef.current = [];
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setInputValue(e.target.value);
    const el = e.target;
    el.style.height = 'auto';
    el.style.height = Math.min(el.scrollHeight, 160) + 'px';
  };

  // Check if a lead agent has sub-agents
  const hasSubAgents = (agent: AgentDefinition) => {
    return agent.threadId ? (subAgentsMap.get(agent.threadId)?.length ?? 0) > 0 : false;
  };

  const getBubbleClassName = (msg: AgentMessage) => {
    const classes = ['sf-chat-bubble', msg.role === 'user' ? 'user' : 'assistant'];
    if (msg.role === 'tool' && msg.toolName === 'thinking') {
      classes.push('thinking');
    }
    if (msg.role === 'tool' && msg.toolName === 'progress') {
      classes.push('tool-progress');
    }
    return classes.join(' ');
  };

  const getBubbleRoleLabel = (msg: AgentMessage) => {
    if (msg.role === 'user') return 'You';
    if (msg.role === 'tool' && msg.toolName === 'progress') return 'Status';
    if (msg.role === 'tool' && msg.toolName === 'thinking') {
      return `${selectedAgent?.displayName || 'AI'} Thinking`;
    }
    return selectedAgent?.displayName || 'AI';
  };

  const getBubbleContentClass = (msg: AgentMessage) =>
    `sf-chat-bubble-content ${
      msg.role === 'tool' && msg.toolName === 'thinking'
        ? 'sf-chat-thinking-content'
        : ''
    } sf-markdown`;

  return (
    <div className="sf-chat-layout">
      {/* Sidebar */}
      <div className={`sf-chat-sidebar ${sidebarCollapsed ? 'collapsed' : ''}`}>
        <div className="sf-chat-sidebar-inner">
          {/* New Chat Button */}
          <button
            className="sf-chat-new-btn"
            onClick={handleNewChat}
            disabled={creatingAgent || !isAdmin}
            title={!isAdmin ? '需要管理员权限' : undefined}
          >
            <PlusOutlined /> New Chat
          </button>

          {/* Conversation List */}
          <div className="sf-chat-section-label">CONVERSATIONS</div>
          <div className="sf-chat-thread-list">
            {leadAgents.length === 0 && (
              <div className="sf-chat-empty-hint">No conversations yet</div>
            )}
            {leadAgents.map((agent) => {
              const agentSubAgents = agent.threadId
                ? subAgentsMap.get(agent.threadId) || []
                : [];
              const isSelected = selectedAgent?.id === agent.id;
              const isParentOfSelected =
                selectedAgent?.parentThreadId === agent.threadId;

              return (
                <div key={agent.id} className="sf-chat-thread-group">
                  {/* Lead Agent Item */}
                  <div
                    className={`sf-chat-thread-item ${isSelected || isParentOfSelected ? 'active' : ''}`}
                    onClick={() => handleSelectAgent(agent)}
                  >
                    <span
                      className="sf-provider-dot"
                      style={{
                        background: PROVIDER_COLORS[agent.provider] || '#666',
                      }}
                    />
                    <div className="sf-chat-thread-info">
                      <span className="sf-chat-thread-title">
                        {agent.displayName || agent.name}
                      </span>
                      <span className="sf-chat-thread-meta">
                        {PROVIDER_LABELS[agent.provider] || agent.provider}
                        {agentSubAgents.length > 0 && (
                          <span className="sf-chat-team-badge">
                            <TeamOutlined /> {agentSubAgents.length}
                          </span>
                        )}
                      </span>
                    </div>
                    <button
                      className="sf-chat-thread-delete"
                      onClick={(e) => handleDeleteAgent(agent, e)}
                    >
                      <DeleteOutlined />
                    </button>
                  </div>

                  {/* Sub-Agent Items (shown when this lead is active) */}
                  {(isSelected || isParentOfSelected) &&
                    agentSubAgents.length > 0 && (
                      <div className="sf-sidebar-subagents">
                        {agentSubAgents.map((sub) => (
                          <div
                            key={sub.id}
                            className={`sf-sidebar-subagent-item ${selectedAgent?.id === sub.id ? 'active' : ''} ${sub.isActive === false ? 'inactive' : ''}`}
                            onClick={() => handleSelectAgent(sub)}
                          >
                            <span
                              className="sf-subagent-type-dot"
                              style={{
                                background: getAgentColor(sub.name),
                              }}
                            />
                            <span className="sf-sidebar-subagent-name">
                              {getAgentLabel(sub.name)}
                            </span>
                          </div>
                        ))}
                      </div>
                    )}
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Sidebar Toggle */}
      <button
        className="sf-chat-sidebar-toggle"
        onClick={() => setSidebarCollapsed(!sidebarCollapsed)}
      >
        {sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      </button>

      {/* Main Chat Area */}
      <div className="sf-chat-main">
        {/* Chat Header */}
        {selectedAgent && (
          <div className="sf-chat-header">
            {/* Breadcrumb for sub-agents */}
            {isSubAgent && currentLeadAgent && (
              <button className="sf-chat-breadcrumb" onClick={handleBackToLead}>
                <LeftOutlined />
                <span>{currentLeadAgent.displayName || currentLeadAgent.name}</span>
              </button>
            )}
            <RobotOutlined
              style={{ fontSize: 18, marginRight: 8, color: 'var(--sf-primary)' }}
            />
            <span className="sf-chat-header-name">
              {isSubAgent
                ? getAgentLabel(selectedAgent.name)
                : selectedAgent.displayName || selectedAgent.name}
            </span>
            {!isSubAgent && (
              <span
                className="sf-provider-tag"
                style={{
                  background: PROVIDER_COLORS[selectedAgent.provider] || '#666',
                }}
              >
                {PROVIDER_LABELS[selectedAgent.provider] || selectedAgent.provider}
              </span>
            )}
            {isSubAgent && (
              <span
                className="sf-subagent-header-type"
                style={{ color: getAgentColor(selectedAgent.name) }}
              >
                {getAgentTypeFromName(selectedAgent.name)}
              </span>
            )}
            <span className="sf-chat-header-model">{selectedAgent.modelName}</span>

            {/* Team panel toggle */}
            {!isSubAgent && hasSubAgents(selectedAgent) && (
              <button
                className={`sf-team-toggle-btn ${teamPanelOpen ? 'active' : ''}`}
                onClick={() => setTeamPanelOpen(!teamPanelOpen)}
                title="Toggle team panel"
              >
                <TeamOutlined />
                <span className="sf-team-toggle-count">
                  {currentSubAgents.length}
                </span>
              </button>
            )}
          </div>
        )}

        {/* Message Area */}
        <div className="sf-chat-messages">
          {!selectedAgent && (
            <div className="sf-chat-welcome">
              <RobotOutlined
                style={{ fontSize: 48, color: 'var(--sf-primary)', marginBottom: 16 }}
              />
              <h2>Welcome to PlayForge AI Chat</h2>
              {isAdmin ? (
                <p>Click "New Chat" to select a model and start a conversation.</p>
              ) : (
                <p style={{ color: 'var(--sf-text-muted)' }}>
                  <LockOutlined style={{ marginRight: 6 }} />
                  需要管理员权限才能创建会话和发送消息，请联系管理员开通
                </p>
              )}
            </div>
          )}

          {selectedAgent && messages.length === 0 && !isCurrentThreadStreaming && (
            <div className="sf-chat-welcome">
              <RobotOutlined
                style={{ fontSize: 48, color: 'var(--sf-primary)', marginBottom: 16 }}
              />
              <h2>Start a conversation</h2>
              <p>Type a message below to begin.</p>
            </div>
          )}

          {messages
            .filter((msg) => !(msg.role === 'tool' && msg.toolName === 'progress'))
            .map((msg) => (
            <div key={msg.id} className={getBubbleClassName(msg)}>
              <div className="sf-chat-bubble-role">{getBubbleRoleLabel(msg)}</div>
              <div className={getBubbleContentClass(msg)}>
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
              </div>
              <button
                className={`sf-bubble-copy-btn ${copiedId === msg.id ? 'copied' : ''}`}
                onClick={() => handleCopy(msg.id, msg.content)}
                title="Copy"
              >
                {copiedId === msg.id ? <CheckOutlined /> : <CopyOutlined />}
              </button>
            </div>
          ))}

          {isCurrentThreadStreaming && (
            <>
              {/* Merged status/progress */}
              {streamingGroups.progress.length > 0 && (
                <div className="sf-chat-bubble assistant sf-status-group">
                  <div className="sf-collapsible-header" onClick={() => setStatusExpanded(!statusExpanded)}>
                    <span className="sf-collapsible-icon">
                      {statusExpanded ? <DownOutlined /> : <RightOutlined />}
                    </span>
                    <span className="sf-chat-bubble-role">Status</span>
                    <span className="sf-status-count">{streamingGroups.progress.length}</span>
                    {!statusExpanded && (
                      <span className="sf-status-latest">
                        {streamingGroups.progress[streamingGroups.progress.length - 1].content}
                      </span>
                    )}
                  </div>
                  {statusExpanded && (
                    <div className="sf-collapsible-body">
                      <div className="sf-chat-progress-list">
                        {streamingGroups.progress.map((msg) => (
                          <div key={msg.id} className="sf-chat-progress-step done">
                            <span className="sf-chat-progress-icon sf-chat-progress-check">&#10003;</span>
                            <span className="sf-chat-progress-text">{msg.content}</span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Thinking (collapsed by default) */}
              {streamingGroups.thinkingText && (
                <div className="sf-chat-bubble assistant thinking">
                  <div className="sf-collapsible-header" onClick={() => setThinkingExpanded(!thinkingExpanded)}>
                    <span className="sf-collapsible-icon">
                      {thinkingExpanded ? <DownOutlined /> : <RightOutlined />}
                    </span>
                    <span className="sf-chat-bubble-role">
                      {selectedAgent?.displayName || 'AI'} Thinking
                    </span>
                  </div>
                  {thinkingExpanded && (
                    <div className="sf-collapsible-body sf-chat-thinking-content sf-markdown">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {streamingGroups.thinkingText}
                      </ReactMarkdown>
                    </div>
                  )}
                </div>
              )}

              {/* Assistant response */}
              {streamingGroups.assistant.map((msg) => (
                <div key={msg.id} className="sf-chat-bubble assistant">
                  <div className="sf-chat-bubble-role">
                    {selectedAgent?.displayName || 'AI'}
                  </div>
                  <div className="sf-chat-bubble-content sf-markdown">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
                    {msg.id === lastStreamingAssistantId && (
                      <span className="sf-chat-cursor" />
                    )}
                  </div>
                  <button
                    className={`sf-bubble-copy-btn ${copiedId === msg.id ? 'copied' : ''}`}
                    onClick={() => handleCopy(msg.id, msg.content)}
                    title="Copy"
                  >
                    {copiedId === msg.id ? <CheckOutlined /> : <CopyOutlined />}
                  </button>
                </div>
              ))}

              {/* Loading indicator */}
              {streamingBubbles.length === 0 && (
                <div className="sf-chat-bubble assistant">
                  <div className="sf-chat-bubble-role">Status</div>
                  <div className="sf-chat-bubble-content">
                    <div className="sf-chat-typing">
                      <LoadingOutlined style={{ marginRight: 8 }} />
                      Running...
                    </div>
                  </div>
                </div>
              )}
            </>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        {selectedAgent && activeThreadId && (
          <div className="sf-chat-input-area">
            {selectedAgent.isActive === false ? (
              <div className="sf-chat-admin-hint">
                此Agent已被销毁，仅可查看历史对话
              </div>
            ) : !isAdmin ? (
              <div className="sf-chat-admin-hint">
                <LockOutlined style={{ marginRight: 6 }} />
                需要管理员权限才能发送消息
              </div>
            ) : (
              <div className="sf-chat-input-wrapper">
                <textarea
                  ref={inputRef}
                  className="sf-chat-input"
                  placeholder="Message"
                  value={inputValue}
                  onChange={handleInputChange}
                  onKeyDown={handleKeyDown}
                  rows={1}
                />
                <button
                  className="sf-chat-send-btn"
                  onClick={handleSend}
                  disabled={!inputValue.trim() || isAnyMainStreamRunning}
                >
                  {isAnyMainStreamRunning ? <LoadingOutlined /> : <SendOutlined />}
                </button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Team Panel (right sidebar) */}
      {teamPanelOpen && !isSubAgent && currentSubAgents.length > 0 && (
        <TeamPanel
          subAgents={currentSubAgents}
          onClose={() => setTeamPanelOpen(false)}
        />
      )}

      {/* Model Picker Modal */}
      <Modal
        title="Select a Model"
        open={modelPickerOpen}
        onCancel={() => setModelPickerOpen(false)}
        footer={null}
        className="sf-model-picker-modal"
        centered
      >
        <div className="sf-model-picker-list">
          {AVAILABLE_MODELS.map((model) => (
            <div
              key={`${model.provider}-${model.modelName}`}
              className="sf-model-picker-item"
              onClick={() => handlePickModel(model)}
            >
              <span
                className="sf-provider-dot"
                style={{ background: PROVIDER_COLORS[model.provider] || '#666' }}
              />
              <div className="sf-model-picker-info">
                <span className="sf-model-picker-name">{model.displayName}</span>
                <span className="sf-model-picker-provider">
                  {PROVIDER_LABELS[model.provider] || model.provider}
                </span>
              </div>
            </div>
          ))}
        </div>
      </Modal>
    </div>
  );
};

export default ChatPage;
