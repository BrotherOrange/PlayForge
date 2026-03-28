import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { LockOutlined, MobileOutlined } from '@ant-design/icons';
import { login } from '../api/auth';
import { ApiResponse } from '../types/api';
import { setTokens } from '../utils/token';

const { Title, Paragraph } = Typography;

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
      const response = await login(values.phone, values.password);
      const { accessToken, refreshToken } = response.data.data;
      setTokens(accessToken, refreshToken);
      message.success('登录成功。');
      navigate('/', { replace: true });
    } catch (error) {
      const axiosError = error as AxiosError<ApiResponse<unknown>>;
      message.error(axiosError.response?.data?.message ?? '登录失败，请稍后重试。');
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
        <Card className="sf-card sf-fade-in" style={{ width: 420 }}>
          <Title level={3} className="sf-glow-title" style={{ textAlign: 'center', marginBottom: 12, fontSize: 22 }}>
            PLAYFORGE
          </Title>
          <Paragraph style={{ textAlign: 'center', color: '#94a3b8', marginBottom: 28 }}>
            登录后可以创建文档评审任务，任务完成后的评审结果页依然支持公开访问。
          </Paragraph>
          <Form onFinish={onFinish} size="large">
            <Form.Item
              name="phone"
              rules={[
                { required: true, message: '请输入手机号。' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的中国大陆手机号。' },
              ]}
            >
              <Input prefix={<MobileOutlined />} placeholder="手机号" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码。' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 16 }}>
              <Button type="primary" htmlType="submit" loading={loading} block>
                登录
              </Button>
            </Form.Item>
            <div style={{ textAlign: 'center', color: '#94a3b8' }}>
              还没有账号？{' '}
              <Link to="/register" style={{ color: '#00d4ff' }}>
                立即注册
              </Link>
            </div>
          </Form>
        </Card>
      </div>
    </>
  );
};

export default LoginPage;
