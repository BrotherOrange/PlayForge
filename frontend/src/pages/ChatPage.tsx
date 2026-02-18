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
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { listAgents, createAgentWithThread, deleteAgent, getMessages, chatThreadSSE } from '../api/chat';
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
  { provider: 'anthropic', modelName: 'claude-opus-4-6', displayName: 'Claude Opus 4.6' },
  { provider: 'gemini', modelName: 'gemini-3-flash-preview', displayName: 'Gemini 3 Flash' },
];

const SELECTED_AGENT_STORAGE_KEY = 'playforge:selectedAgentId';

const ChatPage = () => {
  const { user } = useOutletContext<{ user: UserProfile | null }>();
  const isAdmin = user?.isAdmin === true;

  // All agents (lead + sub)
  const [allAgents, setAllAgents] = useState<AgentDefinition[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentDefinition | null>(null);
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [streamingThreadId, setStreamingThreadId] = useState<string | null>(null);
  const [streamProgress, setStreamProgress] = useState<string[]>([]);
  const [streamingThinking, setStreamingThinking] = useState('');
  const [streamingAssistantContent, setStreamingAssistantContent] = useState('');
  const [inputValue, setInputValue] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [modelPickerOpen, setModelPickerOpen] = useState(false);
  const [creatingAgent, setCreatingAgent] = useState(false);
  const [teamPanelOpen, setTeamPanelOpen] = useState(false);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const activeThreadIdRef = useRef<string | null>(null);
  const streamingAssistantContentRef = useRef('');

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

  // Auto-scroll to bottom
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    activeThreadIdRef.current = activeThreadId;
  }, [activeThreadId]);

  useEffect(() => {
    scrollToBottom();
  }, [
    messages,
    streamProgress,
    streamingThinking,
    streamingAssistantContent,
    isCurrentThreadStreaming,
    scrollToBottom,
  ]);

  // Load agents
  const loadAgents = useCallback(() => {
    listAgents()
      .then((res) => setAllAgents(res.data.data))
      .catch(() => message.error('Failed to load conversations'));
  }, []);

  // Initial load
  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  // Persist selected agent to keep conversation visible after page refresh.
  useEffect(() => {
    if (selectedAgent?.id) {
      localStorage.setItem(SELECTED_AGENT_STORAGE_KEY, selectedAgent.id);
      return;
    }
    localStorage.removeItem(SELECTED_AGENT_STORAGE_KEY);
  }, [selectedAgent?.id]);

  // Restore selected agent (or default to first lead agent) after agents are loaded.
  useEffect(() => {
    if (allAgents.length === 0) {
      if (selectedAgent) {
        setSelectedAgent(null);
      }
      return;
    }

    const agentsWithThread = allAgents.filter((agent) => !!agent.threadId);
    if (agentsWithThread.length === 0) {
      setSelectedAgent(null);
      return;
    }

    if (selectedAgent) {
      const updatedSelected = agentsWithThread.find((agent) => agent.id === selectedAgent.id);
      if (updatedSelected) {
        if (updatedSelected.threadId !== selectedAgent.threadId) {
          setSelectedAgent(updatedSelected);
        }
        return;
      }
    }

    const savedAgentId = localStorage.getItem(SELECTED_AGENT_STORAGE_KEY);
    const savedAgent = savedAgentId
      ? agentsWithThread.find((agent) => agent.id === savedAgentId)
      : undefined;
    const fallbackAgent =
      agentsWithThread.find((agent) => !agent.parentThreadId) || agentsWithThread[0];
    setSelectedAgent(savedAgent || fallbackAgent);
  }, [allAgents, selectedAgent]);

  // Keep team panel in near-realtime while lead agent is active.
  useEffect(() => {
    if (isSubAgent || !teamPanelOpen || !currentLeadAgent?.threadId) {
      return;
    }
    const timer = setInterval(loadAgents, 2000);
    return () => clearInterval(timer);
  }, [isSubAgent, teamPanelOpen, currentLeadAgent?.threadId, loadAgents]);

  // While lead/sub-agent is streaming, refresh agent list so new sub-agents appear immediately.
  useEffect(() => {
    if (!streamingThreadId) {
      return;
    }
    loadAgents();
    const timer = window.setInterval(loadAgents, 1200);
    return () => window.clearInterval(timer);
  }, [streamingThreadId, loadAgents]);

  // Auto-open team panel when sub-agents appear
  useEffect(() => {
    if (currentSubAgents.length > 0 && !isSubAgent) {
      setTeamPanelOpen(true);
    }
  }, [currentSubAgents.length, isSubAgent]);

  // Load messages when selected agent changes.
  // Only depends on activeThreadId — streaming state changes should NOT trigger re-fetch.
  useEffect(() => {
    if (!activeThreadId) {
      setMessages([]);
      return;
    }

    const controller = new AbortController();
    getMessages(activeThreadId, 50, 0, controller.signal)
      .then((res) => setMessages(res.data.data))
      .catch((error) => {
        if ((error as { code?: string }).code === 'ERR_CANCELED') return;
        setMessages([]);
      });

    return () => controller.abort();
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
      const res = await getMessages(threadId, 50, 0);
      latestMessages = res.data.data;
      const hasAssistantReply =
        latestMessages.length > 0 &&
        latestMessages[latestMessages.length - 1].role === 'assistant';
      if (hasAssistantReply || i === retries) {
        break;
      }
      await new Promise<void>((resolve) => {
        window.setTimeout(resolve, 320 * (i + 1));
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
    setStreamProgress([]);
    setStreamingThinking('');
    setStreamingAssistantContent('');
    streamingAssistantContentRef.current = '';

    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
    }

    const sendingThreadId = activeThreadId;
    try {
      await chatThreadSSE(sendingThreadId, content, (event) => {
        const payload = event.content ?? '';
        switch (event.type) {
          case 'progress':
            if (payload) {
              setStreamProgress((prev) => [...prev, payload]);
            }
            break;
          case 'thinking':
            if (payload) {
              setStreamingThinking((prev) => `${prev}${payload}`);
            }
            break;
          case 'token':
            if (payload) {
              setStreamingAssistantContent((prev) => {
                const next = `${prev}${payload}`;
                streamingAssistantContentRef.current = next;
                return next;
              });
            }
            break;
          case 'response':
            if (payload) {
              setStreamingAssistantContent(payload);
              streamingAssistantContentRef.current = payload;
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
      const hasAssistantReply =
        latestMessages.length > 0 &&
        latestMessages[latestMessages.length - 1].role === 'assistant';
      const fallbackContent = streamingAssistantContentRef.current.trim();
      const mergedMessages =
        !hasAssistantReply && fallbackContent
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
      loadAgents();
    } catch {
      message.error('Failed to get response, please try again');
    } finally {
      setStreamingThreadId(null);
      setStreamProgress([]);
      setStreamingThinking('');
      setStreamingAssistantContent('');
      streamingAssistantContentRef.current = '';
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
                            className={`sf-sidebar-subagent-item ${selectedAgent?.id === sub.id ? 'active' : ''}`}
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

          {messages.map((msg) => (
            <div key={msg.id} className={`sf-chat-bubble ${msg.role}`}>
              <div className="sf-chat-bubble-role">
                {msg.role === 'user' ? 'You' : selectedAgent?.displayName || 'AI'}
              </div>
              <div className="sf-chat-bubble-content sf-markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{msg.content}</ReactMarkdown>
              </div>
            </div>
          ))}

          {isCurrentThreadStreaming && (
            <div className="sf-chat-bubble assistant">
              <div className="sf-chat-bubble-role">
                {selectedAgent?.displayName || 'AI'}
              </div>
              <div className="sf-chat-bubble-content sf-chat-progress-bubble">
                {streamProgress.length === 0 ? (
                  <div className="sf-chat-typing">
                    <LoadingOutlined style={{ marginRight: 8 }} />
                    Running...
                  </div>
                ) : (
                  <div className="sf-chat-progress-list">
                    {streamProgress.map((step, i) => (
                      <div
                        key={i}
                        className={`sf-chat-progress-step ${i === streamProgress.length - 1 ? 'active' : 'done'}`}
                      >
                        <span className="sf-chat-progress-icon">
                          {i === streamProgress.length - 1 ? (
                            <LoadingOutlined />
                          ) : (
                            <span className="sf-chat-progress-check">&#10003;</span>
                          )}
                        </span>
                        <span className="sf-chat-progress-text">{step}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {isCurrentThreadStreaming && streamingThinking.trim().length > 0 && (
            <div className="sf-chat-bubble assistant thinking">
              <div className="sf-chat-bubble-role">
                {selectedAgent?.displayName || 'AI'} Thinking
              </div>
              <div className="sf-chat-bubble-content sf-chat-thinking-content sf-markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {streamingThinking}
                </ReactMarkdown>
              </div>
            </div>
          )}

          {isCurrentThreadStreaming && streamingAssistantContent.length > 0 && (
            <div className="sf-chat-bubble assistant">
              <div className="sf-chat-bubble-role">
                {selectedAgent?.displayName || 'AI'}
              </div>
              <div className="sf-chat-bubble-content sf-markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {streamingAssistantContent}
                </ReactMarkdown>
                <span className="sf-chat-cursor" />
              </div>
            </div>
          )}

          <div ref={messagesEndRef} />
        </div>

        {/* Input Area */}
        {selectedAgent && activeThreadId && (
          <div className="sf-chat-input-area">
            {!isAdmin ? (
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
