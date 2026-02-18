import { useEffect, useRef, useState } from 'react';
import { Upload, message } from 'antd';
import { PlusOutlined, LoadingOutlined } from '@ant-design/icons';
import type { RcFile } from 'antd/es/upload';
import type { UploadRequestOption } from '@rc-component/upload/lib/interface';
import axios from 'axios';
import { getUploadPolicy } from '../api/oss';

interface AvatarUploadProps {
  value: string | null;
  onChange: (key: string) => void;
}

const AvatarUpload = ({ value, onChange }: AvatarUploadProps) => {
  const [loading, setLoading] = useState(false);
  const [localPreview, setLocalPreview] = useState<string | null>(null);
  const localPreviewRef = useRef<string | null>(null);

  const displayUrl = localPreview || value;

  useEffect(() => () => {
    if (localPreviewRef.current) {
      URL.revokeObjectURL(localPreviewRef.current);
      localPreviewRef.current = null;
    }
  }, []);

  const beforeUpload = (file: RcFile): boolean => {
    const isImage = file.type.startsWith('image/');
    if (!isImage) {
      message.error('只能上传图片文件');
      return false;
    }
    const isLt10M = file.size / 1024 / 1024 < 10;
    if (!isLt10M) {
      message.error('图片大小不能超过10MB');
      return false;
    }
    return true;
  };

  const customRequest = async (options: UploadRequestOption) => {
    const { file, onSuccess, onError } = options;
    setLoading(true);
    try {
      const policyRes = await getUploadPolicy();
      const policy = policyRes.data.data;

      const rcFile = file as RcFile;
      const ext = rcFile.name.substring(rcFile.name.lastIndexOf('.'));
      const key = policy.key + ext;

      const formData = new FormData();
      formData.append('key', key);
      formData.append('policy', policy.policy);
      formData.append('OSSAccessKeyId', policy.accessKeyId);
      formData.append('signature', policy.signature);
      formData.append('success_action_status', '200');
      formData.append('file', rcFile);

      await axios.post(policy.host, formData);

      const previewUrl = URL.createObjectURL(rcFile);
      if (localPreviewRef.current) {
        URL.revokeObjectURL(localPreviewRef.current);
      }
      localPreviewRef.current = previewUrl;
      setLocalPreview(previewUrl);
      onChange?.(key);
      onSuccess?.(key);
      message.success('头像上传成功');
    } catch (err) {
      onError?.(err as Error);
      message.error('头像上传失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Upload
      name="file"
      listType="picture-card"
      showUploadList={false}
      beforeUpload={beforeUpload}
      customRequest={customRequest}
    >
      {displayUrl ? (
        <img src={displayUrl} alt="avatar" style={{ width: '100%', borderRadius: 8 }} />
      ) : (
        <div>
          {loading ? <LoadingOutlined /> : <PlusOutlined />}
          <div style={{ marginTop: 8, color: '#94a3b8' }}>上传头像</div>
        </div>
      )}
    </Upload>
  );
};

export default AvatarUpload;
