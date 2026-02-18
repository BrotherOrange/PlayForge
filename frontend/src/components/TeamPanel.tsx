import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import {
  TeamOutlined,
  RightOutlined,
  DownOutlined,
  MessageOutlined,
  LoadingOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { getMessages } from '../api/chat';
import { getAgentShortLabel, getAgentColor } from '../constants/agentTypes';
import { AgentDefinition, AgentMessage } from '../types/api';

interface TeamPanelProps {
  subAgents: AgentDefinition[];
  onSelectAgent: (agent: AgentDefinition) => void;
  onClose: () => void;
}

interface SubAgentCardState {
  expanded: boolean;
  messages: AgentMessage[];
  loading: boolean;
  loaded: boolean;
}

const TeamPanel = ({ subAgents, onSelectAgent, onClose }: TeamPanelProps) => {
  const [cardStates, setCardStates] = useState<Record<string, SubAgentCardState>>({});
  const cardStatesRef = useRef(cardStates);
  cardStatesRef.current = cardStates;

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
        };
      }
      return next;
    });
  }, [subAgents]);

  // Fetch messages for a specific agent
  const fetchMessages = useCallback((agentId: string, threadId: string) => {
    setCardStates((p) => ({
      ...p,
      [agentId]: { ...p[agentId], loading: true },
    }));
    getMessages(threadId, 20, 0)
      .then((res) => {
        setCardStates((p) => ({
          ...p,
          [agentId]: {
            ...p[agentId],
            messages: res.data.data,
            loading: false,
            loaded: true,
          },
        }));
      })
      .catch(() => {
        setCardStates((p) => ({
          ...p,
          [agentId]: { ...p[agentId], loading: false, loaded: true },
        }));
      });
  }, []);

  const toggleCard = useCallback(
    (agent: AgentDefinition) => {
      setCardStates((prev) => {
        const current = prev[agent.id];
        if (!current) return prev;
        const willExpand = !current.expanded;

        // Trigger async fetch outside the updater on first expand
        if (willExpand && !current.loaded && agent.threadId) {
          // Schedule fetch after state update
          setTimeout(() => fetchMessages(agent.id, agent.threadId!), 0);
        }

        return {
          ...prev,
          [agent.id]: { ...current, expanded: willExpand },
        };
      });
    },
    [fetchMessages]
  );

  // Stable key for expanded agent IDs (avoids timer rebuild on every message fetch)
  const expandedAgentKey = useMemo(() => {
    return subAgents
      .filter((a) => cardStates[a.id]?.expanded && a.threadId)
      .map((a) => a.id)
      .join(',');
  }, [subAgents, cardStates]);

  // Refresh messages for expanded cards periodically
  useEffect(() => {
    if (!expandedAgentKey) return;

    const timer = setInterval(() => {
      const currentStates = cardStatesRef.current;
      for (const agent of subAgents) {
        if (!agent.threadId || !currentStates[agent.id]?.expanded) continue;
        getMessages(agent.threadId, 20, 0)
          .then((res) => {
            setCardStates((prev) => ({
              ...prev,
              [agent.id]: {
                ...prev[agent.id],
                messages: res.data.data,
                loaded: true,
              },
            }));
          })
          .catch(() => {});
      }
    }, 5000);

    return () => clearInterval(timer);
  }, [expandedAgentKey, subAgents]);

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

      <div className="sf-team-panel-body">
        {subAgents.map((agent) => {
          const state = cardStates[agent.id];
          const isExpanded = state?.expanded ?? false;
          const messages = state?.messages ?? [];
          const isLoading = state?.loading ?? false;
          const hasMessages = messages.length > 0;
          const lastMessage = hasMessages ? messages[messages.length - 1] : null;
          const color = getAgentColor(agent.name);

          return (
            <div
              key={agent.id}
              className={`sf-subagent-card ${isExpanded ? 'expanded' : ''}`}
            >
              {/* Card header */}
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
                  {hasMessages ? (
                    <CheckCircleOutlined style={{ color: '#34d399', fontSize: 12 }} />
                  ) : (
                    <ClockCircleOutlined style={{ color: '#f59e0b', fontSize: 12 }} />
                  )}
                </span>
                <span className="sf-subagent-expand-icon">
                  {isExpanded ? <DownOutlined /> : <RightOutlined />}
                </span>
              </div>

              {/* Last message preview (when collapsed) */}
              {!isExpanded && lastMessage && (
                <div className="sf-subagent-preview">
                  {lastMessage.content.slice(0, 80)}
                  {lastMessage.content.length > 80 ? '...' : ''}
                </div>
              )}

              {/* Expanded conversation */}
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
                    <div className="sf-subagent-messages">
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

                  <button
                    className="sf-subagent-chat-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      onSelectAgent(agent);
                    }}
                  >
                    <MessageOutlined /> Open Full Chat
                  </button>
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
