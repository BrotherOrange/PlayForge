import { useState, useEffect } from 'react';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';
import { Layout, Dropdown, Avatar, Space, message } from 'antd';
import { UserOutlined, LogoutOutlined, IdcardOutlined, RobotOutlined } from '@ant-design/icons';
import { getProfile } from '../api/user';
import { logout } from '../api/auth';
import { getRefreshToken, clearTokens } from '../utils/token';
import { UserProfile } from '../types/api';

const { Header, Content } = Layout;

const AppLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const [user, setUser] = useState<UserProfile | null>(null);

  useEffect(() => {
    getProfile()
      .then((res) => setUser(res.data.data))
      .catch(() => {});
  }, []);

  const handleLogout = async () => {
    try {
      const refreshToken = getRefreshToken();
      if (refreshToken) {
        await logout(refreshToken);
      }
    } catch {
      // 即使接口失败也继续清理
    }
    clearTokens();
    message.success('已退出登录');
    navigate('/login', { replace: true });
  };

  const menuItems = [
    {
      key: 'profile',
      icon: <IdcardOutlined />,
      label: '个人中心',
      onClick: () => navigate('/profile'),
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  const isHomePage = location.pathname === '/';
  const isChatPage = location.pathname === '/chat';

  return (
    <Layout style={{ minHeight: '100vh', background: 'transparent' }}>
      {/* Background layers - skip on chat page */}
      {!isHomePage && !isChatPage && (
        <>
          <div className="sf-starfield" />
          <div className="sf-grid" />
          <div className="sf-particles" />
        </>
      )}

      <Header className="sf-header" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0 24px' }}>
        <div
          style={{
            fontFamily: "'Orbitron', sans-serif",
            fontSize: 20,
            fontWeight: 700,
            cursor: 'pointer',
            color: '#00d4ff',
            textShadow: '0 0 10px rgba(0,212,255,0.3)',
          }}
          onClick={() => navigate('/')}
        >
          PLAYFORGE
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 24 }}>
          <span
            style={{
              fontSize: 13,
              color: isChatPage ? 'var(--sf-primary)' : 'var(--sf-text-muted)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: 6,
              transition: 'color 0.2s',
            }}
            onClick={() => navigate('/chat')}
          >
            <RobotOutlined /> AI Chat
          </span>
        </div>

        <Dropdown menu={{ items: menuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer' }}>
            <Avatar
              src={user?.avatarUrl}
              icon={!user?.avatarUrl && <UserOutlined />}
              style={{
                border: '1px solid var(--sf-border)',
                boxShadow: '0 0 8px rgba(0,212,255,0.2)',
              }}
            />
            <span style={{ color: '#e2e8f0' }}>
              {user?.nickname || user?.phone || '用户'}
            </span>
          </Space>
        </Dropdown>
      </Header>

      <Content style={{ paddingTop: isHomePage ? 0 : 64, background: 'transparent', ...(isChatPage ? { height: 'calc(100vh - 64px)', overflow: 'hidden' } : {}) }}>
        <Outlet context={{ user, setUser }} />
      </Content>
    </Layout>
  );
};

export default AppLayout;
