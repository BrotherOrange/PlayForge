import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Form, Input, Button, Card, message, Typography } from 'antd';
import { MobileOutlined, LockOutlined } from '@ant-design/icons';
import { login } from '../api/auth';
import { setTokens } from '../utils/token';
import { AxiosError } from 'axios';
import { ApiResponse } from '../types/api';

const { Title } = Typography;

interface LoginFormValues {
  phone: string;
  password: string;
}

const LoginPage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: LoginFormValues) => {
    setLoading(true);
    try {
      const res = await login(values.phone, values.password);
      const { accessToken, refreshToken } = res.data.data;
      setTokens(accessToken, refreshToken);
      message.success('登录成功');
      navigate('/', { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiResponse<unknown>>;
      const msg = axiosErr.response?.data?.message || '登录失败';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />
      <div className="sf-auth-page">
        <Card className="sf-card sf-fade-in" style={{ width: 400 }}>
          <Title
            level={3}
            className="sf-glow-title"
            style={{ textAlign: 'center', marginBottom: 32, fontSize: 22 }}
          >
            PLAYFORGE
          </Title>
          <Form onFinish={onFinish} size="large">
            <Form.Item
              name="phone"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
              ]}
            >
              <Input prefix={<MobileOutlined />} placeholder="手机号" />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[{ required: true, message: '请输入密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                登录
              </Button>
            </Form.Item>
            <div style={{ textAlign: 'center', color: '#94a3b8' }}>
              还没有账号？<Link to="/register" style={{ color: '#00d4ff' }}>立即注册</Link>
            </div>
          </Form>
        </Card>
      </div>
    </>
  );
};

export default LoginPage;
