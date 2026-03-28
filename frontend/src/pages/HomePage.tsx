import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from 'antd';
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

const HomePage = () => {
  const navigate = useNavigate();
  const [tasks, setTasks] = useState<ReviewTaskHistoryItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadTasks = useCallback(async () => {
    setLoading(true);
    setError(null);
    const localTasks = loadRecentReviewTasks();

    try {
      const response = await listMyReviewTasks(6);
      setTasks(mergeReviewTaskHistory(response.data.data, localTasks).slice(0, 6));
    } catch (requestError) {
      setTasks(mergeReviewTaskHistory([], localTasks).slice(0, 6));
      setError((requestError as RequestErrorShape).response?.data?.message ?? '账号记录暂时读取失败。');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadTasks();
  }, [loadTasks]);

  return (
    <>
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />

      <div className="sf-home-shell">
        <div className="sf-hero sf-home-hero">
          <div className="sf-hero-subtitle">面向游戏团队的智能协作平台</div>
          <h1 className="sf-glow-title">PLAYFORGE</h1>
          <div className="sf-hero-desc">
            在一个工作台里完成创意探索、AI 协作，以及多角色文档评审与公开分享。
          </div>
          <div className="sf-hero-actions">
            <Button className="sf-hex-btn" size="large" onClick={() => navigate('/chat')}>
              AI 对话
            </Button>
            <Button className="sf-hex-btn secondary" size="large" onClick={() => navigate('/reviews/new')}>
              新建评审
            </Button>
          </div>
        </div>

        <ReviewTaskHistoryList
          compact
          emptyMessage="还没有最近评审任务，上传一份文档后就会在这里留下入口。"
          error={error}
          loading={loading}
          subtitle="返回首页后也能继续找到它们"
          tasks={tasks}
          title="最近评审任务"
        />
      </div>
    </>
  );
};

export default HomePage;
