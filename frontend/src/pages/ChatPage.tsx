import { useState, useEffect, useRef, useCallback, KeyboardEvent } from 'react';
import { useOutletContext } from 'react-router-dom';
import { message, Modal } from 'antd';
import {
  PlusOutlined,
  SendOutlined,
  StopOutlined,
  DeleteOutlined,
  RobotOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  LockOutlined,
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { listAgents, createAgentWithThread, deleteAgent, getMessages } from '../api/chat';
import { useAgentWebSocket } from '../hooks/useAgentWebSocket';
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
  { provider: 'gemini', modelName: 'gemini-3-pro-preview', displayName: 'Gemini 3 Pro' },
];

const ChatPage = () => {
  const { user } = useOutletContext<{ user: UserProfile | null }>();
  const isAdmin = user?.isAdmin === true;

  // agents = user's conversations (each agent = one conversation with a model)
  const [agents, setAgents] = useState<AgentDefinition[]>([]);
  const [selectedAgent, setSelectedAgent] = useState<AgentDefinition | null>(null);
  const [messages, setMessages] = useState<AgentMessage[]>([]);
  const [streamingContent, setStreamingContent] = useState('');
  const [isStreaming, setIsStreaming] = useState(false);
  const [inputValue, setInputValue] = useState('');
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const [modelPickerOpen, setModelPickerOpen] = useState(false);
  const [creatingAgent, setCreatingAgent] = useState(false);

  const streamingRef = useRef('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  const activeThreadId = selectedAgent?.threadId ?? null;

  // Auto-scroll to bottom
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, []);

  useEffect(() => {
    scrollToBottom();
  }, [messages, streamingContent, scrollToBottom]);

  // Load agents (conversations) on mount
  const loadAgents = useCallback(() => {
    listAgents()
      .then((res) => setAgents(res.data.data))
      .catch(() => message.error('Failed to load conversations'));
  }, []);

  // WebSocket callbacks
  const onToken = useCallback((content: string) => {
    streamingRef.current += content;
    setStreamingContent(streamingRef.current);
  }, []);

  const onDone = useCallback(() => {
    const finalContent = streamingRef.current;
    if (finalContent) {
      setMessages((prev) => [
        ...prev,
        {
          id: String(Date.now()),
          role: 'assistant',
          content: finalContent,
          toolName: null,
          tokenCount: 0,
          createdAt: new Date().toISOString(),
        },
      ]);
    }
    streamingRef.current = '';
    setStreamingContent('');
    setIsStreaming(false);
    // Refresh agent list to update state
    loadAgents();
  }, [loadAgents]);

  const onError = useCallback((msg: string) => {
    message.error(msg);
    streamingRef.current = '';
    setStreamingContent('');
    setIsStreaming(false);
  }, []);

  const { sendMessage: wsSend, cancelStream } = useAgentWebSocket({
    threadId: activeThreadId,
    onToken,
    onDone,
    onError,
  });

  useEffect(() => {
    loadAgents();
  }, [loadAgents]);

  // Load messages when selected agent (conversation) changes
  useEffect(() => {
    if (!activeThreadId) {
      setMessages([]);
      return;
    }

    const controller = new AbortController();
    getMessages(activeThreadId, 50, 0, controller.signal)
      .then((res) => setMessages(res.data.data))
      .catch((error) => {
        if ((error as { code?: string }).code === 'ERR_CANCELED') {
          return;
        }
        setMessages([]);
      });

    return () => controller.abort();
  }, [activeThreadId]);

  const handleSelectAgent = (agent: AgentDefinition) => {
    if (isStreaming) return;
    setSelectedAgent(agent);
    setStreamingContent('');
    streamingRef.current = '';
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
      // agent already has threadId set from the response
      setAgents((prev) => [agent, ...prev]);
      setSelectedAgent(agent);
      setMessages([]);
      setStreamingContent('');
      streamingRef.current = '';
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
      setAgents((prev) => prev.filter((a) => a.id !== agent.id));
      if (selectedAgent?.id === agent.id) {
        setSelectedAgent(null);
        setMessages([]);
      }
    } catch {
      message.error('Failed to delete conversation');
    }
  };

  const handleSend = () => {
    const content = inputValue.trim();
    if (!content || !activeThreadId || isStreaming) return;

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
    setIsStreaming(true);
    streamingRef.current = '';
    setStreamingContent('');
    wsSend(content);

    if (inputRef.current) {
      inputRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
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
            {agents.length === 0 && (
              <div className="sf-chat-empty-hint">No conversations yet</div>
            )}
            {agents.map((agent) => (
              <div
                key={agent.id}
                className={`sf-chat-thread-item ${selectedAgent?.id === agent.id ? 'active' : ''}`}
                onClick={() => handleSelectAgent(agent)}
              >
                <span
                  className="sf-provider-dot"
                  style={{ background: PROVIDER_COLORS[agent.provider] || '#666' }}
                />
                <div className="sf-chat-thread-info">
                  <span className="sf-chat-thread-title">
                    {agent.displayName || agent.name}
                  </span>
                  <span className="sf-chat-thread-meta">
                    {PROVIDER_LABELS[agent.provider] || agent.provider}
                  </span>
                </div>
                <button
                  className="sf-chat-thread-delete"
                  onClick={(e) => handleDeleteAgent(agent, e)}
                >
                  <DeleteOutlined />
                </button>
              </div>
            ))}
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
            <RobotOutlined style={{ fontSize: 18, marginRight: 8, color: 'var(--sf-primary)' }} />
            <span className="sf-chat-header-name">
              {selectedAgent.displayName || selectedAgent.name}
            </span>
            <span
              className="sf-provider-tag"
              style={{ background: PROVIDER_COLORS[selectedAgent.provider] || '#666' }}
            >
              {PROVIDER_LABELS[selectedAgent.provider] || selectedAgent.provider}
            </span>
            <span className="sf-chat-header-model">{selectedAgent.modelName}</span>
          </div>
        )}

        {/* Message Area */}
        <div className="sf-chat-messages">
          {!selectedAgent && (
            <div className="sf-chat-welcome">
              <RobotOutlined style={{ fontSize: 48, color: 'var(--sf-primary)', marginBottom: 16 }} />
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

          {selectedAgent && messages.length === 0 && !isStreaming && (
            <div className="sf-chat-welcome">
              <RobotOutlined style={{ fontSize: 48, color: 'var(--sf-primary)', marginBottom: 16 }} />
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

          {isStreaming && (
            <div className="sf-chat-bubble assistant">
              <div className="sf-chat-bubble-role">
                {selectedAgent?.displayName || 'AI'}
              </div>
              <div className="sf-chat-bubble-content sf-markdown">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{streamingContent}</ReactMarkdown>
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
                  placeholder="Type a message... (Enter to send, Shift+Enter for new line)"
                  value={inputValue}
                  onChange={handleInputChange}
                  onKeyDown={handleKeyDown}
                  rows={1}
                  disabled={isStreaming}
                />
                {isStreaming ? (
                  <button className="sf-chat-send-btn stop" onClick={cancelStream}>
                    <StopOutlined />
                  </button>
                ) : (
                  <button
                    className="sf-chat-send-btn"
                    onClick={handleSend}
                    disabled={!inputValue.trim()}
                  >
                    <SendOutlined />
                  </button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

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
