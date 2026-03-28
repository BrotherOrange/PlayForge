import { Link } from 'react-router-dom';
import { FileSearchOutlined, HistoryOutlined, ReloadOutlined } from '@ant-design/icons';
import { AVAILABLE_MODELS, PROVIDER_LABELS } from '../../constants/models';
import { getReviewStatusLabel } from '../../constants/reviewUi';
import { ReviewTaskHistoryItem } from '../../utils/reviewTaskHistory';

interface ReviewTaskHistoryListProps {
  tasks: ReviewTaskHistoryItem[];
  loading: boolean;
  error: string | null;
  title: string;
  subtitle: string;
  emptyMessage: string;
  compact?: boolean;
  onRefresh?: () => void;
}

const getModelDisplayName = (provider: string, modelName: string) =>
  AVAILABLE_MODELS.find((item) => item.provider === provider && item.modelName === modelName)?.displayName ?? modelName;

const ReviewTaskHistoryList = ({
  tasks,
  loading,
  error,
  title,
  subtitle,
  emptyMessage,
  compact = false,
  onRefresh,
}: ReviewTaskHistoryListProps) => {
  return (
    <section className={`pf-review-panel pf-review-history-panel ${compact ? 'compact' : ''}`}>
      <div className="pf-review-panel-header">
        <div>
          <h2>{title}</h2>
          <span>{subtitle}</span>
        </div>
        {onRefresh ? (
          <button className="pf-review-button ghost pf-review-history-refresh" onClick={onRefresh} type="button">
            <ReloadOutlined /> 刷新
          </button>
        ) : null}
      </div>

      {loading ? (
        <div className="pf-review-empty-state">
          <HistoryOutlined /> 正在加载评审记录...
        </div>
      ) : error ? (
        <div className="pf-review-empty-state">
          <FileSearchOutlined /> {error}
        </div>
      ) : tasks.length === 0 ? (
        <div className="pf-review-empty-state">
          <HistoryOutlined /> {emptyMessage}
        </div>
      ) : (
        <div className={`pf-review-history-list ${compact ? 'compact' : ''}`}>
          {tasks.map((task) => (
            <article className="pf-review-history-card" key={task.publicId}>
              <div className="pf-review-history-main">
                <div className="pf-review-history-topline">
                  <span className={`pf-review-status-badge ${task.status}`}>{getReviewStatusLabel(task.status)}</span>
                  <span className={`pf-review-source-badge ${task.source}`}>{task.source === 'server' ? '账号记录' : '本机最近'}</span>
                </div>
                <h3>{task.title}</h3>
                <div className="pf-review-history-meta">
                  <span>{PROVIDER_LABELS[task.provider] ?? task.provider}</span>
                  <span>{getModelDisplayName(task.provider, task.modelName)}</span>
                  <span>{task.documentCount} 份文档</span>
                  <span>{task.createdAt ?? '刚刚创建'}</span>
                  {task.hasOverallReport ? <span>已有总体报告</span> : null}
                </div>
                {task.errorMessage ? <p className="pf-review-history-error">{task.errorMessage}</p> : null}
              </div>
              <div className="pf-review-history-actions">
                <Link className="pf-review-link" to={`/reviews/${task.publicId}`}>
                  查看评审
                </Link>
                <span className="pf-review-history-id">{task.publicId}</span>
              </div>
            </article>
          ))}
        </div>
      )}
    </section>
  );
};

export default ReviewTaskHistoryList;
