import { useState, useEffect } from 'react';
import { useOutletContext } from 'react-router-dom';
import { Card, Form, Input, Button, message, Spin, Avatar } from 'antd';
import { UserOutlined, EditOutlined } from '@ant-design/icons';
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

const ProfilePage = () => {
  const { setUser } = useOutletContext<OutletContext>();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState(false);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [avatarKey, setAvatarKey] = useState<string | null>(null);
  const [avatarDisplayUrl, setAvatarDisplayUrl] = useState<string | null>(null);

  useEffect(() => {
    getProfile()
      .then((res) => {
        const data = res.data.data;
        setProfile(data);
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
      setProfile(data);
      setAvatarDisplayUrl(data.avatarUrl);
      setEditing(false);
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

  if (!editing) {
    return (
      <div className="sf-profile-container sf-fade-in">
        <Card className="sf-card">
          <div className="sf-profile-header">
            <Avatar
              size={96}
              src={avatarDisplayUrl}
              icon={!avatarDisplayUrl && <UserOutlined />}
              style={{
                border: '2px solid var(--sf-border)',
                boxShadow: '0 0 20px rgba(0,212,255,0.15)',
              }}
            />
            <div className="sf-profile-name">
              {profile?.nickname || '未设置昵称'}
            </div>
            <div className="sf-profile-bio">
              {profile?.bio || '这个人很懒，什么都没写'}
            </div>
          </div>

          <div className="sf-profile-info">
            <div className="sf-profile-field">
              <span className="sf-profile-label">手机号</span>
              <span className="sf-profile-value">{profile?.phone}</span>
            </div>
            <div className="sf-profile-field">
              <span className="sf-profile-label">昵称</span>
              <span className="sf-profile-value">
                {profile?.nickname || '未设置'}
              </span>
            </div>
            <div className="sf-profile-field">
              <span className="sf-profile-label">简介</span>
              <span className="sf-profile-value">
                {profile?.bio || '未填写'}
              </span>
            </div>
          </div>

          <Button
            type="primary"
            icon={<EditOutlined />}
            block
            onClick={() => setEditing(true)}
          >
            编辑资料
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="sf-profile-container sf-fade-in">
      <Card className="sf-card" title="编辑资料">
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <AvatarUpload value={avatarDisplayUrl} onChange={setAvatarKey} />
        </div>
        <Form form={form} layout="vertical" onFinish={onFinish}>
          <Form.Item label="昵称" name="nickname">
            <Input placeholder="请输入昵称" maxLength={20} />
          </Form.Item>
          <Form.Item label="个人简介" name="bio">
            <Input.TextArea
              placeholder="介绍一下自己吧"
              maxLength={200}
              rows={4}
            />
          </Form.Item>
          <Form.Item>
            <div style={{ display: 'flex', gap: 12 }}>
              <Button
                block
                onClick={() => setEditing(false)}
              >
                取消
              </Button>
              <Button type="primary" htmlType="submit" loading={saving} block>
                保存
              </Button>
            </div>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
};

export default ProfilePage;
