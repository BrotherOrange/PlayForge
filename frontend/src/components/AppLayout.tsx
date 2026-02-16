import { useState, useEffect } from 'react';
import { useNavigate, Outlet } from 'react-router-dom';
import { Layout, Dropdown, Avatar, Space, message } from 'antd';
import { UserOutlined, LogoutOutlined } from '@ant-design/icons';
import { getProfile } from '../api/user';
import { logout } from '../api/auth';
import { getRefreshToken, clearTokens } from '../utils/token';
import { UserProfile } from '../types/api';

const { Header, Content } = Layout;

const AppLayout = () => {
  const navigate = useNavigate();
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
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      onClick: handleLogout,
    },
  ];

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          background: '#fff',
          padding: '0 24px',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <div
          style={{ fontSize: 20, fontWeight: 'bold', cursor: 'pointer' }}
          onClick={() => navigate('/')}
        >
          PlayForge
        </div>
        <Dropdown menu={{ items: menuItems }} placement="bottomRight">
          <Space style={{ cursor: 'pointer' }}>
            <Avatar
              src={user?.avatarUrl}
              icon={!user?.avatarUrl && <UserOutlined />}
            />
            <span>{user?.nickname || user?.phone || '用户'}</span>
          </Space>
        </Dropdown>
      </Header>
      <Content style={{ padding: '24px', background: '#f5f5f5' }}>
        <Outlet context={{ user, setUser }} />
      </Content>
    </Layout>
  );
};

export default AppLayout;
