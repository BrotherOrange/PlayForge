import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { message } from 'antd';
import { ArrowLeftOutlined, FileTextOutlined, LoadingOutlined, RadarChartOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import {
  createPublicReviewEventSource,
  generatePublicReviewReport,
  getPublicReview,
} from '../api/publicReviews';
import ReviewDocumentViewer from '../components/review/ReviewDocumentViewer';
import {
  getReviewAnnotationTypeLabel,
  getReviewPriorityLabel,
  getReviewRoleLabel,
  getReviewStatusLabel,
} from '../constants/reviewUi';
import { PublicReviewAnnotationDetail, PublicReviewTaskDetail } from '../types/api';
import { saveRecentReviewTask, summarizeReviewTask } from '../utils/reviewTaskHistory';

type RequestErrorShape = {
  response?: {
    data?: {
      message?: string;
    };
  };
};

type FilteredDocument = PublicReviewTaskDetail['documents'][number] & {
  filteredAnnotations: PublicReviewAnnotationDetail[];
};

const parseSnapshot = (payload: string): PublicReviewTaskDetail | null => {
  if (!payload) {
    return null;
  }

  try {
    return JSON.parse(payload) as PublicReviewTaskDetail;
  } catch {
    return null;
  }
};

const PublicReviewPreviewPage = () => {
  const { publicId } = useParams<{ publicId: string }>();
  const [task, setTask] = useState<PublicReviewTaskDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeAnnotationId, setActiveAnnotationId] = useState<string | null>(null);
  const [roleFilter, setRoleFilter] = useState('all');
  const [typeFilter, setTypeFilter] = useState('all');
  const [priorityFilter, setPriorityFilter] = useState('all');
  const [overallReportExpanded, setOverallReportExpanded] = useState(false);
  const [generatingReport, setGeneratingReport] = useState(false);
  const [streamState, setStreamState] = useState<'connecting' | 'live' | 'closed'>('connecting');
  const [selectedReportId, setSelectedReportId] = useState<string | null>(null);
  const [collapsedDocumentIds, setCollapsedDocumentIds] = useState<string[]>([]);

  useEffect(() => {
    if (!publicId) {
      setError('缺少任务编号。');
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    setStreamState('connecting');

    getPublicReview(publicId)
      .then((response) => {
        if (cancelled) {
          return;
        }
        setTask(response.data.data);
      })
      .catch((requestError) => {
        if (cancelled) {
          return;
        }
        const fallbackMessage = '加载评审任务失败，请稍后重试。';
        setError((requestError as RequestErrorShape).response?.data?.message ?? fallbackMessage);
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    const eventSource = createPublicReviewEventSource(publicId);
    const handleSnapshot = (event: MessageEvent<string>) => {
      const nextTask = parseSnapshot(event.data);
      if (!nextTask) {
        return;
      }
      setTask(nextTask);
      setStreamState('live');
    };

    eventSource.addEventListener('snapshot', handleSnapshot as EventListener);
    eventSource.onopen = () => setStreamState('live');
    eventSource.onerror = () => {
      setStreamState(eventSource.readyState === EventSource.CLOSED ? 'closed' : 'connecting');
    };

    return () => {
      cancelled = true;
      eventSource.removeEventListener('snapshot', handleSnapshot as EventListener);
      eventSource.close();
    };
  }, [publicId]);

  useEffect(() => {
    if (!task?.overallReportMarkdown) {
      return;
    }
    setOverallReportExpanded((current) => current || Boolean(task.overallReportMarkdown));
  }, [task?.overallReportMarkdown]);

  useEffect(() => {
    if (!task) {
      return;
    }
    saveRecentReviewTask(summarizeReviewTask(task));
  }, [task]);

  useEffect(() => {
    if (!task) {
      return;
    }

    const validDocumentIds = new Set(task.documents.map((document) => document.id));
    setCollapsedDocumentIds((current) => current.filter((documentId) => validDocumentIds.has(documentId)));
  }, [task]);

  useEffect(() => {
    if (!task) {
      setSelectedReportId(null);
      return;
    }

    if (task.reportHistory.length === 0) {
      setSelectedReportId(null);
      return;
    }

    const stillExists = task.reportHistory.some((report) => report.id === selectedReportId);
    if (!stillExists) {
      setSelectedReportId(task.reportHistory[0].id);
    }
  }, [selectedReportId, task]);

  const documents = useMemo(() => task?.documents ?? [], [task?.documents]);

  const roleOptions = useMemo(() => {
    const map = new Map<string, string>();
    documents.forEach((document) => {
      document.roleRuns.forEach((roleRun) => {
        map.set(roleRun.roleKey, getReviewRoleLabel(roleRun.roleKey, roleRun.roleName));
      });
    });
    return Array.from(map.entries());
  }, [documents]);

  const filteredDocuments = useMemo<FilteredDocument[]>(
    () =>
      documents.map((document) => ({
        ...document,
        filteredAnnotations: document.annotations.filter((annotation) => {
          if (roleFilter !== 'all' && annotation.roleKey !== roleFilter) {
            return false;
          }
          if (typeFilter !== 'all' && annotation.annotationType !== typeFilter) {
            return false;
          }
          if (priorityFilter !== 'all' && annotation.priority !== priorityFilter) {
            return false;
          }
          return true;
        }),
      })),
    [documents, priorityFilter, roleFilter, typeFilter]
  );

  const allVisibleAnnotations = useMemo(
    () => filteredDocuments.flatMap((document) => document.filteredAnnotations),
    [filteredDocuments]
  );

  useEffect(() => {
    if (!activeAnnotationId) {
      return;
    }

    const stillVisible = allVisibleAnnotations.some((annotation) => annotation.id === activeAnnotationId);
    if (!stillVisible) {
      setActiveAnnotationId(null);
    }
  }, [activeAnnotationId, allVisibleAnnotations]);

  const taskCounts = useMemo(() => {
    const annotations = documents.flatMap((document) => document.annotations);
    return {
      total: annotations.length,
      high: annotations.filter((annotation) => annotation.priority === 'high').length,
      medium: annotations.filter((annotation) => annotation.priority === 'medium').length,
      low: annotations.filter((annotation) => annotation.priority === 'low').length,
    };
  }, [documents]);

  const reportHistory = task?.reportHistory ?? [];
  const selectedReport =
    reportHistory.find((report) => report.id === selectedReportId)
    ?? reportHistory[0]
    ?? (task?.overallReportMarkdown
      ? {
          id: 'latest',
          reportScope: 'overall',
          title: task.title,
          provider: task.provider,
          modelName: task.modelName,
          reportMarkdown: task.overallReportMarkdown,
          createdAt: task.createdAt,
        }
      : null);

  const handleFocusAnnotation = (annotation: PublicReviewAnnotationDetail) => {
    setActiveAnnotationId(annotation.id);
  };

  const toggleDocumentCollapse = (documentId: string) => {
    setCollapsedDocumentIds((current) =>
      current.includes(documentId)
        ? current.filter((value) => value !== documentId)
        : [...current, documentId]
    );
  };

  const handleGenerateReport = async () => {
    if (!publicId || !task) {
      return;
    }

    setGeneratingReport(true);
    try {
      const response = await generatePublicReviewReport(publicId);
      const nextTask = response.data.data;
      setTask(nextTask);
      setOverallReportExpanded(true);
      setSelectedReportId(nextTask.reportHistory[0]?.id ?? null);
      message.success(nextTask.reportHistory.length > 1 ? '总体报告已重新生成。' : '总体报告已生成。');
    } catch (requestError) {
      const fallbackMessage = '生成总体报告失败，请稍后重试。';
      const errorMessage = (requestError as RequestErrorShape).response?.data?.message ?? fallbackMessage;
      message.error(errorMessage);
    } finally {
      setGeneratingReport(false);
    }
  };

  if (loading) {
    return (
      <div className="pf-review-loading">
        <LoadingOutlined style={{ marginRight: 12 }} />
        正在加载公开评审页...
      </div>
    );
  }

  if (error || !task) {
    return (
      <div className="pf-review-page">
        <div className="sf-starfield" />
        <div className="sf-grid" />
        <div className="sf-particles" />
        <div className="pf-review-shell">
          <section className="pf-review-panel">
            <div className="pf-review-panel-header">
              <h2>任务加载失败</h2>
            </div>
            <div className="pf-review-error-banner">{error ?? '没有找到这个评审任务。'}</div>
            <Link className="pf-review-link" to="/">
              <ArrowLeftOutlined />
              返回首页
            </Link>
          </section>
        </div>
      </div>
    );
  }

  const canGenerateOverallReport = task.status !== 'created' && task.status !== 'processing';

  return (
    <div className="pf-review-page">
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />

      <div className="pf-review-shell">
        <header className="pf-review-hero compact">
          <div>
            <div className="pf-review-eyebrow">公开评审结果页</div>
            <h1>{task.title}</h1>
            <p>
              所有文档会同步启动评审，角色结果通过实时快照更新。左侧是连续正文与高亮，右侧是与当前行对齐的边注，点击历史报告可切换查看旧版本。
            </p>
          </div>
          <div className="pf-review-header-actions">
            <div className={`pf-review-stream-chip ${streamState}`}>
              <span className="pf-review-stream-dot" />
              {streamState === 'live' ? '实时连接中' : streamState === 'connecting' ? '正在重连' : '实时连接已断开'}
            </div>
            <Link className="pf-review-link" to="/">
              <ArrowLeftOutlined />
              返回首页
            </Link>
          </div>
        </header>

        {task.errorMessage && <div className="pf-review-error-banner">{task.errorMessage}</div>}

        <section className="pf-review-toolbar">
          <div className="pf-review-toolbar-stats">
            <div className="pf-review-stat-chip">
              <strong>{getReviewStatusLabel(task.status)}</strong>
              <span>任务状态</span>
            </div>
            <div className="pf-review-stat-chip">
              <strong>{task.documentCount}</strong>
              <span>文档数量</span>
            </div>
            <div className="pf-review-stat-chip">
              <strong>{taskCounts.total}</strong>
              <span>总批注数</span>
            </div>
            <div className="pf-review-stat-chip">
              <strong>{taskCounts.high}</strong>
              <span>高优先级</span>
            </div>
            <div className="pf-review-stat-chip">
              <strong>{taskCounts.medium}</strong>
              <span>中优先级</span>
            </div>
            <div className="pf-review-stat-chip">
              <strong>{taskCounts.low}</strong>
              <span>低优先级</span>
            </div>
          </div>

          <div className="pf-review-toolbar-filters">
            <label>
              <span>角色筛选</span>
              <select value={roleFilter} onChange={(event) => setRoleFilter(event.target.value)}>
                <option value="all">全部角色</option>
                {roleOptions.map(([roleKey, label]) => (
                  <option key={roleKey} value={roleKey}>
                    {label}
                  </option>
                ))}
              </select>
            </label>

            <label>
              <span>类型筛选</span>
              <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
                <option value="all">全部类型</option>
                <option value="question">{getReviewAnnotationTypeLabel('question')}</option>
                <option value="note">{getReviewAnnotationTypeLabel('note')}</option>
                <option value="opinion">{getReviewAnnotationTypeLabel('opinion')}</option>
              </select>
            </label>

            <label>
              <span>优先级筛选</span>
              <select value={priorityFilter} onChange={(event) => setPriorityFilter(event.target.value)}>
                <option value="all">全部优先级</option>
                <option value="high">{getReviewPriorityLabel('high')}</option>
                <option value="medium">{getReviewPriorityLabel('medium')}</option>
                <option value="low">{getReviewPriorityLabel('low')}</option>
              </select>
            </label>
          </div>
        </section>

        <section className="pf-review-panel pf-review-overall-panel">
          <div className="pf-review-panel-header">
            <div>
              <h2>总体报告</h2>
              <span>点击按钮后会把原文与各角色批注一起交给当前模型生成正式报告。</span>
            </div>
            <div className="pf-review-header-actions">
              <button
                className="pf-review-button secondary"
                disabled={!canGenerateOverallReport || generatingReport}
                onClick={handleGenerateReport}
                type="button"
              >
                {generatingReport ? '生成中...' : task.overallReportMarkdown ? '再次生成' : '生成总体报告'}
              </button>
              {selectedReport && (
                <button
                  className="pf-review-button ghost"
                  onClick={() => setOverallReportExpanded((current) => !current)}
                  type="button"
                >
                  {overallReportExpanded ? '收起报告' : '展开报告'}
                </button>
              )}
            </div>
          </div>

          {!selectedReport ? (
            <div className="pf-review-empty-state">
              {canGenerateOverallReport
                ? '当前还没有总体报告。待文档评审完成后，点击右上角按钮即可生成。'
                : '文档仍在评审中。等全部角色跑完后，你就可以手动生成总体报告。'}
            </div>
          ) : (
            overallReportExpanded && (
              <div className="pf-review-report-layout">
                <div className="pf-review-report-viewer">
                  <div className="pf-review-report-meta">
                    <strong>{selectedReport.id === reportHistory[0]?.id ? '当前版本' : '历史版本'}</strong>
                    <span>
                      {selectedReport.createdAt ?? '刚刚'} · {selectedReport.provider} / {selectedReport.modelName}
                    </span>
                  </div>
                  <div className="pf-review-summary-markdown sf-markdown">
                    <ReactMarkdown remarkPlugins={[remarkGfm]}>{selectedReport.reportMarkdown}</ReactMarkdown>
                  </div>
                </div>

                <aside className="pf-review-report-history">
                  <div className="pf-review-report-history-head">
                    <strong>报告历史</strong>
                    <span>{reportHistory.length} 条记录</span>
                  </div>
                  {reportHistory.length === 0 ? (
                    <div className="pf-review-empty-state compact">当前只有最新报告，还没有可切换的历史记录。</div>
                  ) : (
                    <div className="pf-review-report-history-list">
                      {reportHistory.map((report, index) => {
                        const selected = report.id === selectedReport.id;
                        return (
                          <button
                            className={`pf-review-report-history-item ${selected ? 'active' : ''}`}
                            key={report.id}
                            onClick={() => setSelectedReportId(report.id)}
                            type="button"
                          >
                            <strong>{index === 0 ? '当前版本' : `历史版本 ${reportHistory.length - index}`}</strong>
                            <span>{report.createdAt ?? '刚刚'}</span>
                            <em>{report.provider} / {report.modelName}</em>
                          </button>
                        );
                      })}
                    </div>
                  )}
                </aside>
              </div>
            )
          )}
        </section>

        <section className="pf-review-document-stack">
          {filteredDocuments.map((document) => (
            <ReviewDocumentViewer
              activeAnnotationId={activeAnnotationId}
              collapsed={collapsedDocumentIds.includes(document.id)}
              document={document}
              key={document.id}
              onFocusAnnotation={handleFocusAnnotation}
              onToggleCollapsed={() => toggleDocumentCollapse(document.id)}
              visibleAnnotations={document.filteredAnnotations}
            />
          ))}
        </section>

        <section className="pf-review-panel pf-review-guidance-panel">
          <div className="pf-review-panel-header">
            <div>
              <h2>查看方式</h2>
              <span>这一页就是预览页，不需要登录。</span>
            </div>
          </div>
          <div className="pf-review-guidance-grid">
            <div className="pf-review-guidance-item">
              <RadarChartOutlined />
              <div>
                <strong>高亮重叠可切换角色</strong>
                <p>同一段文字如果有多个角色重叠批注，点击荧光高亮后会弹出角色选择浮层。</p>
              </div>
            </div>
            <div className="pf-review-guidance-item">
              <FileTextOutlined />
              <div>
                <strong>每份文档可独立折叠</strong>
                <p>文档默认在固定高度区域内滚动，折叠后只保留标题、状态和角色执行概览，方便快速切换。</p>
              </div>
            </div>
          </div>
        </section>
      </div>
    </div>
  );
};

export default PublicReviewPreviewPage;
