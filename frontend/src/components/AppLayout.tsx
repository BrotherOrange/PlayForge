import { useState, useEffect } from 'react';
import { useNavigate, Outlet, useLocation } from 'react-router-dom';
import { Layout, Dropdown, Avatar, Space, message } from 'antd';
import { UserOutlined, LogoutOutlined, IdcardOutlined } from '@ant-design/icons';
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

  return (
    <Layout style={{ minHeight: '100vh', background: 'transparent' }}>
      {/* Background layers - always present */}
      {!isHomePage && (
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

      <Content style={{ paddingTop: isHomePage ? 0 : 64, background: 'transparent' }}>
        <Outlet context={{ user, setUser }} />
      </Content>
    </Layout>
  );
};

export default AppLayout;
