import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from 'antd';
import ReviewTaskHistoryList from '../components/review/ReviewTaskHistoryList';
import { listMyReviewTasks } from '../api/reviewTasks';
import { ReviewTaskHistoryItem } from '../utils/reviewTaskHistory';

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

    try {
      const response = await listMyReviewTasks(4);
      setTasks(response.data.data.map((task) => ({ ...task, source: 'server' as const })));
    } catch (requestError) {
      setTasks([]);
      setError((requestError as RequestErrorShape).response?.data?.message ?? '账号最近评审任务暂时读取失败。');
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
          emptyMessage="你最近还没有创建过评审任务，先去新建一个吧。"
          error={error}
          loading={loading}
          subtitle="仅展示当前账号创建的最近 4 个评审任务。"
          tasks={tasks}
          title="最近评审任务"
        />
      </div>
    </>
  );
};

export default HomePage;
