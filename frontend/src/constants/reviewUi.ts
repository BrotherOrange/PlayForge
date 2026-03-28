export const REVIEW_ROLE_LABELS: Record<string, string> = {
  planner: '策划',
  engineer: '程序',
  qa: 'QA',
  art: '美术',
  ui: 'UI',
  pm: 'PM',
  player: '玩家',
};

export const REVIEW_STATUS_LABELS: Record<string, string> = {
  created: '已创建',
  processing: '处理中',
  completed: '已完成',
  partial: '部分完成',
  failed: '失败',
  pending: '等待中',
  running: '执行中',
};

export const REVIEW_PRIORITY_LABELS: Record<string, string> = {
  high: '高',
  medium: '中',
  low: '低',
};

export const REVIEW_ANNOTATION_TYPE_LABELS: Record<string, string> = {
  question: '问题',
  note: '说明',
  opinion: '意见',
};

export const getReviewRoleLabel = (roleKey: string, fallback?: string) =>
  REVIEW_ROLE_LABELS[roleKey] ?? fallback ?? roleKey;

export const getReviewStatusLabel = (status: string) =>
  REVIEW_STATUS_LABELS[status] ?? status;

export const getReviewPriorityLabel = (priority: string) =>
  REVIEW_PRIORITY_LABELS[priority] ?? priority;

export const getReviewAnnotationTypeLabel = (annotationType: string) =>
  REVIEW_ANNOTATION_TYPE_LABELS[annotationType] ?? annotationType;
