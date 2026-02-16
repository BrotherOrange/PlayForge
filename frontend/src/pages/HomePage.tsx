import { useNavigate } from 'react-router-dom';
import { Button } from 'antd';

const HomePage = () => {
  const navigate = useNavigate();

  return (
    <>
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />
      <div className="sf-hero">
        <div className="sf-hero-subtitle">NEXT-GEN GAME PLATFORM</div>
        <h1 className="sf-glow-title">PLAYFORGE</h1>
        <div className="sf-hero-desc">
          探索无限可能的游戏世界，在这里创造、连接、征服。
        </div>
        <div className="sf-hero-btn-wrapper">
          <Button
            className="sf-hex-btn"
            size="large"
            onClick={() => navigate('/profile')}
          >
            进入控制台
          </Button>
        </div>
      </div>
    </>
  );
};

export default HomePage;
