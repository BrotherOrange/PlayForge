import { useState, useEffect } from 'react';
import { useOutletContext } from 'react-router-dom';
import { Card, Form, Input, Button, message, Spin } from 'antd';
import AvatarUpload from '../components/AvatarUpload';
import { getProfile, updateProfile } from '../api/user';
import { AxiosError } from 'axios';
import { ApiResponse, UserProfile } from '../types/api';

interface OutletContext {
  user: UserProfile | null;
  setUser: React.Dispatch<React.SetStateAction<UserProfile | null>>;
}

interface ProfileFormValues {
  nickname?: string;
  bio?: string;
}

const HomePage = () => {
  const { setUser } = useOutletContext<OutletContext>();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [avatarKey, setAvatarKey] = useState<string | null>(null);
  const [avatarDisplayUrl, setAvatarDisplayUrl] = useState<string | null>(null);

  useEffect(() => {
    getProfile()
      .then((res) => {
        const data = res.data.data;
        form.setFieldsValue({
          nickname: data.nickname,
          bio: data.bio,
        });
        setAvatarKey(data.avatarKey);
        setAvatarDisplayUrl(data.avatarUrl);
      })
      .catch(() => message.error('获取资料失败'))
      .finally(() => setLoading(false));
  }, [form]);

  const onFinish = async (values: ProfileFormValues) => {
    setSaving(true);
    try {
      const res = await updateProfile({
        nickname: values.nickname,
        bio: values.bio,
        avatarUrl: avatarKey,
      });
      const data = res.data.data;
      setUser(data);
      message.success('保存成功');
    } catch (err) {
      const axiosErr = err as AxiosError<ApiResponse<unknown>>;
      const msg = axiosErr.response?.data?.message || '保存失败';
      message.error(msg);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <Card title="个人资料">
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <AvatarUpload value={avatarDisplayUrl} onChange={setAvatarKey} />
        </div>
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item label="昵称" name="nickname">
            <Input placeholder="请输入昵称" maxLength={20} />
          </Form.Item>
          <Form.Item label="个人简介" name="bio">
            <Input.TextArea placeholder="介绍一下自己吧" maxLength={200} rows={4} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={saving} block>
              保存
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default HomePage;
