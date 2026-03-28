import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AxiosError } from 'axios';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { LockOutlined, MobileOutlined } from '@ant-design/icons';
import { register } from '../api/auth';
import { ApiResponse } from '../types/api';
import { setTokens } from '../utils/token';

const { Title, Paragraph } = Typography;

interface RegisterFormValues {
  phone: string;
  nickname?: string;
  bio?: string;
  password: string;
  confirmPassword: string;
}

const RegisterPage = () => {
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);

  const onFinish = async (values: RegisterFormValues) => {
    setLoading(true);
    try {
      const response = await register({
        phone: values.phone,
        password: values.password,
        nickname: values.nickname || null,
        avatarUrl: null,
        bio: values.bio || null,
      });
      const { accessToken, refreshToken } = response.data.data;
      setTokens(accessToken, refreshToken);
      message.success('注册成功。');
      navigate('/', { replace: true });
    } catch (error) {
      const axiosError = error as AxiosError<ApiResponse<unknown>>;
      message.error(axiosError.response?.data?.message ?? '注册失败，请稍后重试。');
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
        <Card className="sf-card sf-fade-in" style={{ width: 440 }}>
          <Title level={3} className="sf-glow-title" style={{ textAlign: 'center', marginBottom: 12, fontSize: 22 }}>
            PLAYFORGE
          </Title>
          <Paragraph style={{ textAlign: 'center', color: '#94a3b8', marginBottom: 24 }}>
            注册后即可发起文档评审任务，并把最终的公开评审页分享给任何人查看。
          </Paragraph>
          <Form onFinish={onFinish} size="large" layout="vertical">
            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                { required: true, message: '请输入手机号。' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的中国大陆手机号。' },
              ]}
            >
              <Input prefix={<MobileOutlined />} placeholder="手机号" />
            </Form.Item>
            <Form.Item name="nickname" label="昵称">
              <Input placeholder="选填，用于页面展示" maxLength={20} />
            </Form.Item>
            <Form.Item name="bio" label="个人简介">
              <Input.TextArea placeholder="选填，简单介绍一下自己" maxLength={200} rows={3} />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码。' },
                { min: 6, max: 20, message: '密码长度请控制在 6 到 20 个字符之间。' },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请再次输入密码。' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致。'));
                  },
                }),
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="确认密码" />
            </Form.Item>
            <Form.Item style={{ marginBottom: 16 }}>
              <Button type="primary" htmlType="submit" loading={loading} block>
                注册
              </Button>
            </Form.Item>
            <div style={{ textAlign: 'center', color: '#94a3b8' }}>
              已有账号？{' '}
              <Link to="/login" style={{ color: '#00d4ff' }}>
                去登录
              </Link>
            </div>
          </Form>
        </Card>
      </div>
    </>
  );
};

export default RegisterPage;
