import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { Form, Input, Button, Card, message, Typography } from 'antd';
import { MobileOutlined, LockOutlined } from '@ant-design/icons';
import { register } from '../api/auth';
import { setTokens } from '../utils/token';
import AvatarUpload from '../components/AvatarUpload';
import { AxiosError } from 'axios';
import { ApiResponse } from '../types/api';

const { Title } = Typography;

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
  const [avatarKey, setAvatarKey] = useState<string | null>(null);

  const onFinish = async (values: RegisterFormValues) => {
    setLoading(true);
    try {
      const res = await register({
        phone: values.phone,
        password: values.password,
        nickname: values.nickname || null,
        avatarUrl: avatarKey || null,
        bio: values.bio || null,
      });
      const { accessToken, refreshToken } = res.data.data;
      setTokens(accessToken, refreshToken);
      message.success('注册成功');
      navigate('/', { replace: true });
    } catch (err) {
      const axiosErr = err as AxiosError<ApiResponse<unknown>>;
      const msg = axiosErr.response?.data?.message || '注册失败';
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
        <Card className="sf-card sf-fade-in" style={{ width: 440 }}>
          <Title
            level={3}
            className="sf-glow-title"
            style={{ textAlign: 'center', marginBottom: 24, fontSize: 22 }}
          >
            PLAYFORGE
          </Title>
          <Form onFinish={onFinish} size="large" layout="vertical">
            <Form.Item style={{ textAlign: 'center', marginBottom: 16 }}>
              <AvatarUpload value={null} onChange={setAvatarKey} />
            </Form.Item>
            <Form.Item
              name="phone"
              label="手机号"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
              ]}
            >
              <Input prefix={<MobileOutlined />} placeholder="手机号" />
            </Form.Item>
            <Form.Item
              name="nickname"
              label="昵称"
            >
              <Input placeholder="给自己取个名字吧" maxLength={20} />
            </Form.Item>
            <Form.Item
              name="bio"
              label="个人简介"
            >
              <Input.TextArea placeholder="介绍一下自己吧" maxLength={200} rows={3} />
            </Form.Item>
            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, max: 20, message: '密码长度6-20位' },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="密码" />
            </Form.Item>
            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'));
                  },
                }),
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="确认密码" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>
                注册
              </Button>
            </Form.Item>
            <div style={{ textAlign: 'center', color: '#94a3b8' }}>
              已有账号？<Link to="/login" style={{ color: '#00d4ff' }}>去登录</Link>
            </div>
          </Form>
        </Card>
      </div>
    </>
  );
};

export default RegisterPage;
