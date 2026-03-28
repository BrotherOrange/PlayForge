import axios from 'axios';
import { startTransition, useMemo, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { message } from 'antd';
import {
  ArrowRightOutlined,
  DeleteOutlined,
  FileTextOutlined,
  PlusOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { createReviewTask, getReviewUploadPolicy } from '../api/reviewTasks';
import { AVAILABLE_MODELS, PROVIDER_COLORS, PROVIDER_LABELS } from '../constants/models';
import { PublicReviewFileItem, PublicReviewTextEntry } from '../types/api';
import { saveRecentReviewTask } from '../utils/reviewTaskHistory';

interface DraftTextEntry extends PublicReviewTextEntry {
  id: string;
}

const MAX_ITEMS = 3;
const ALLOWED_EXTENSIONS = new Set(['pdf', 'doc', 'docx', 'xlsx', 'xls', 'txt', 'md', 'markdown']);

const newTextEntry = (): DraftTextEntry => ({
  id: Math.random().toString(36).slice(2, 10),
  title: '',
  content: '',
});

const getFileExtension = (filename: string) => {
  const lastDot = filename.lastIndexOf('.');
  return lastDot >= 0 ? filename.slice(lastDot + 1).toLowerCase() : '';
};

const getFileSuffix = (filename: string) => {
  const lastDot = filename.lastIndexOf('.');
  return lastDot >= 0 ? filename.slice(lastDot) : '';
};

const PublicReviewCreatePage = () => {
  const navigate = useNavigate();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [taskTitle, setTaskTitle] = useState('');
  const [selectedModelKey, setSelectedModelKey] = useState(
    `${AVAILABLE_MODELS[0].provider}:${AVAILABLE_MODELS[0].modelName}`
  );
  const [files, setFiles] = useState<File[]>([]);
  const [textEntries, setTextEntries] = useState<DraftTextEntry[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const selectedModel = useMemo(
    () =>
      AVAILABLE_MODELS.find((model) => `${model.provider}:${model.modelName}` === selectedModelKey) ??
      AVAILABLE_MODELS[0],
    [selectedModelKey]
  );

  const normalizedTextEntries = useMemo(
    () =>
      textEntries
        .map((entry) => ({
          ...entry,
          title: entry.title.trim(),
          content: entry.content.trim(),
        }))
        .filter((entry) => entry.title || entry.content),
    [textEntries]
  );

  const totalItems = files.length + normalizedTextEntries.length;
  const canAddMore = totalItems < MAX_ITEMS;

  const warnLimit = () => {
    message.warning(`单次评审任务最多支持 ${MAX_ITEMS} 份文档。`);
  };

  const openFilePicker = () => {
    if (!canAddMore) {
      warnLimit();
      return;
    }
    fileInputRef.current?.click();
  };

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    const pickedFiles = Array.from(event.target.files ?? []);
    if (pickedFiles.length === 0) {
      return;
    }

    const remainingSlots = Math.max(0, MAX_ITEMS - totalItems);
    if (remainingSlots <= 0) {
      warnLimit();
      return;
    }

    const acceptedFiles = pickedFiles.filter((file) => ALLOWED_EXTENSIONS.has(getFileExtension(file.name)));
    const rejectedCount = pickedFiles.length - acceptedFiles.length;
    if (rejectedCount > 0) {
      message.warning('仅支持上传 PDF、DOC、DOCX、XLSX 和 XLS 文件。');
    }

    const allowedFiles = acceptedFiles.slice(0, remainingSlots);
    if (allowedFiles.length < acceptedFiles.length) {
      message.warning(`本次仅保留前 ${remainingSlots} 个文件。`);
    }

    setFiles((current) => [...current, ...allowedFiles]);
    event.target.value = '';
  };

  const handleRemoveFile = (index: number) => {
    setFiles((current) => current.filter((_, currentIndex) => currentIndex !== index));
  };

  const handleAddTextEntry = () => {
    if (!canAddMore) {
      warnLimit();
      return;
    }
    setTextEntries((current) => [...current, newTextEntry()]);
  };

  const handleUpdateTextEntry = (id: string, field: keyof PublicReviewTextEntry, value: string) => {
    setTextEntries((current) => current.map((entry) => (entry.id === id ? { ...entry, [field]: value } : entry)));
  };

  const handleRemoveTextEntry = (id: string) => {
    setTextEntries((current) => current.filter((entry) => entry.id !== id));
  };

  const uploadFileToOss = async (file: File): Promise<PublicReviewFileItem> => {
    const policyResponse = await getReviewUploadPolicy();
    const policy = policyResponse.data.data;
    const suffix = getFileSuffix(file.name);
    const objectKey = suffix ? `${policy.key}${suffix}` : policy.key;

    const formData = new FormData();
    formData.append('key', objectKey);
    formData.append('policy', policy.policy);
    formData.append('OSSAccessKeyId', policy.accessKeyId);
    formData.append('signature', policy.signature);
    formData.append('success_action_status', '200');
    formData.append('file', file);

    await axios.post(policy.host, formData, { timeout: 600000 });
    return {
      originalFilename: file.name,
      contentType: file.type || 'application/octet-stream',
      objectKey,
    };
  };

  const handleSubmit = async () => {
    if (totalItems === 0) {
      message.warning('请先上传文件或补充一段文本内容。');
      return;
    }

    setSubmitting(true);
    const uploadMessage = files.length > 0 ? message.loading('正在上传文件到 OSS...', 0) : null;
    let createMessage: (() => void) | null = null;

    try {
      const fileItems = await Promise.all(files.map(uploadFileToOss));
      uploadMessage?.();

      createMessage = message.loading('正在创建评审任务...', 0);
      const response = await createReviewTask({
        taskTitle: taskTitle.trim() || undefined,
        provider: selectedModel.provider,
        modelName: selectedModel.modelName,
        fileItems,
        textItems: normalizedTextEntries.map(({ title, content }) => ({ title, content })),
      });
      createMessage();

      const publicId = response.data.data.publicId;
      const now = new Date().toISOString();
      saveRecentReviewTask({
        publicId,
        title: response.data.data.title,
        provider: selectedModel.provider,
        modelName: selectedModel.modelName,
        status: response.data.data.status,
        documentCount: totalItems,
        hasOverallReport: false,
        errorMessage: null,
        createdAt: now,
        updatedAt: now,
      });
      startTransition(() => {
        navigate(`/reviews/${publicId}`);
      });
    } catch (error) {
      uploadMessage?.();
      createMessage?.();
      const fallbackMessage = '创建评审任务失败，请稍后重试。';
      const errorMessage =
        (error as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallbackMessage;
      message.error(errorMessage);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="pf-review-page">
      <div className="sf-starfield" />
      <div className="sf-grid" />
      <div className="sf-particles" />

      <div className="pf-review-shell">
        <header className="pf-review-hero">
          <div className="pf-review-hero-copy">
            <div className="pf-review-eyebrow">登录后创建 · 公开结果页</div>
            <h1>多角色文档评审</h1>
            <p>
              你可以上传最多三份 PDF、Word、Excel 文档，也可以直接输入标题和正文。文件会先上传到 OSS，
              再由 Gemini 直接读取并规范化成 Markdown，随后交给所选模型执行 7 个固定角色的并行评审。
              创建任务需要登录，最终评审页保持公开可访问。
            </p>
          </div>
          <Link className="pf-review-link pf-review-link-compact" to="/">
            返回首页
          </Link>
        </header>

        <section className="pf-review-create-grid">
          <div className="pf-review-panel">
            <div className="pf-review-panel-header">
              <h2>评审内容</h2>
              <span>
                {totalItems}/{MAX_ITEMS}
              </span>
            </div>

            <label className="pf-review-field">
              <span>任务标题</span>
              <input
                value={taskTitle}
                onChange={(event) => setTaskTitle(event.target.value)}
                placeholder="例如：魂卡系统评审 V2"
              />
            </label>

            <div className="pf-review-upload-box">
              <div className="pf-review-inline-copy">
                <strong>上传文件</strong>
                <p>支持 `.pdf`、`.doc`、`.docx`、`.xlsx`、`.xls`。每份文件会先上传到 OSS，再由 Gemini 转换成 Markdown 后作为独立文档进入评审。</p>
              </div>
              <button
                className="pf-review-button secondary pf-review-action-button"
                onClick={openFilePicker}
                type="button"
              >
                <UploadOutlined /> 添加文件
              </button>
              <input
                ref={fileInputRef}
                className="pf-review-hidden-input"
                type="file"
                multiple
                accept=".pdf,.doc,.docx,.xlsx,.xls,.txt,.md,.markdown"
                onChange={handleFileChange}
              />
            </div>

            {files.length > 0 && (
              <div className="pf-review-chip-list">
                {files.map((file, index) => (
                  <div className="pf-review-chip" key={`${file.name}-${index}`}>
                    <div className="pf-review-chip-main">
                      <FileTextOutlined /> {file.name}
                    </div>
                    <button type="button" onClick={() => handleRemoveFile(index)}>
                      <DeleteOutlined />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <div className="pf-review-inline-header">
              <div className="pf-review-inline-copy">
                <strong>直接输入文本</strong>
                <p>手动输入的标题和正文也会走同一套 Markdown 归一化与评审流程。</p>
              </div>
              <button
                className="pf-review-button ghost pf-review-action-button"
                onClick={handleAddTextEntry}
                type="button"
                disabled={!canAddMore}
              >
                <PlusOutlined /> 添加文本
              </button>
            </div>

            {textEntries.length === 0 && (
              <div className="pf-review-empty-state">
                还没有手动输入的文本。如果你想把原始说明和上传文件一起评审，可以在这里补充。
              </div>
            )}

            <div className="pf-review-text-entry-list">
              {textEntries.map((entry, index) => (
                <div className="pf-review-text-entry" key={entry.id}>
                  <div className="pf-review-text-entry-header">
                    <strong>文本项 {index + 1}</strong>
                    <button type="button" onClick={() => handleRemoveTextEntry(entry.id)}>
                      <DeleteOutlined />
                    </button>
                  </div>
                  <input
                    value={entry.title}
                    onChange={(event) => handleUpdateTextEntry(entry.id, 'title', event.target.value)}
                    placeholder="标题"
                  />
                  <textarea
                    value={entry.content}
                    onChange={(event) => handleUpdateTextEntry(entry.id, 'content', event.target.value)}
                    placeholder="正文内容"
                    rows={6}
                  />
                </div>
              ))}
            </div>
          </div>

          <div className="pf-review-panel">
            <div className="pf-review-panel-header">
              <h2>模型选择</h2>
              <span>固定 3 个</span>
            </div>

            <div className="pf-review-model-list">
              {AVAILABLE_MODELS.map((model) => {
                const modelKey = `${model.provider}:${model.modelName}`;
                const active = selectedModelKey === modelKey;
                return (
                  <button
                    type="button"
                    key={modelKey}
                    className={`pf-review-model-card ${active ? 'active' : ''}`}
                    onClick={() => setSelectedModelKey(modelKey)}
                  >
                    <span className="pf-review-model-dot" style={{ backgroundColor: PROVIDER_COLORS[model.provider] }} />
                    <div>
                      <strong>{model.displayName}</strong>
                      <span>{PROVIDER_LABELS[model.provider]}</span>
                    </div>
                  </button>
                );
              })}
            </div>

            <div className="pf-review-checklist">
              <div>固定 7 个角色并行评审。</div>
              <div>创建任务前，文件会先上传到 OSS。</div>
              <div>所有输入内容都会先统一转换为标准 Markdown。</div>
              <div>创建任务需要登录账号。</div>
              <div>最终预览页公开可访问，无需登录。</div>
              <div>支持重叠高亮、角色切换和批注联动聚焦。</div>
            </div>

            <button className="pf-review-button primary" onClick={handleSubmit} type="button" disabled={submitting}>
              {submitting ? '正在创建任务...' : '开始评审'}
              {!submitting && <ArrowRightOutlined />}
            </button>
          </div>
        </section>
      </div>
    </div>
  );
};

export default PublicReviewCreatePage;
