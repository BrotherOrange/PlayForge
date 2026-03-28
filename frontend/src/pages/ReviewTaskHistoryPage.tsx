import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { PlusOutlined } from '@ant-design/icons';
import ReviewTaskHistoryList from '../components/review/ReviewTaskHistoryList';
import { listMyReviewTasks } from '../api/reviewTasks';
import { loadRecentReviewTasks, mergeReviewTaskHistory, ReviewTaskHistoryItem } from '../utils/reviewTaskHistory';

type RequestErrorShape = {
  response?: {
    data?: {
      message?: string;
    };
  };
};

const ReviewTaskHistoryPage = () => {
  const [tasks, setTasks] = useState<ReviewTaskHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    setError(null);

    const localTasks = loadRecentReviewTasks();

    try {
      const response = await listMyReviewTasks(30);
      const serverTasks = response.data.data;
      setTasks(mergeReviewTaskHistory(serverTasks, localTasks));
    } catch (requestError) {
      const fallbackMessage = '账号历史暂时读取失败，已先展示这台设备上的最近记录。';
      setTasks(mergeReviewTaskHistory([], localTasks));
      setError((requestError as RequestErrorShape).response?.data?.message ?? fallbackMessage);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  return (
    <div className="pf-review-page">
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />

      <div className="pf-review-shell">
        <header className="pf-review-hero">
          <div className="pf-review-hero-copy">
            <div className="pf-review-eyebrow">登录后查看</div>
            <h1>评审任务记录</h1>
            <p>
              这里会优先展示当前账号创建过的评审任务，同时保留这台设备最近打开过的评审记录，
              这样你从首页返回后也能快速回到之前的任务。
            </p>
          </div>
          <div className="pf-review-hero-actions">
            <Link className="pf-review-link pf-review-link-compact" to="/">
              返回首页
            </Link>
            <Link className="pf-review-button secondary pf-review-history-create" to="/reviews/new">
              <PlusOutlined /> 新建评审
            </Link>
          </div>
        </header>

        <ReviewTaskHistoryList
          emptyMessage="你还没有可查看的评审记录，先去创建一份文档评审吧。"
          error={error}
          loading={loading}
          onRefresh={() => void loadTasks()}
          subtitle="账号历史 + 本机最近任务"
          tasks={tasks}
          title="我的评审任务"
        />
      </div>
    </div>
  );
};

export default ReviewTaskHistoryPage;
