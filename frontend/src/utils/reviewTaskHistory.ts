import { PublicReviewTaskDetail, PublicReviewTaskSummary } from '../types/api';

const STORAGE_KEY = 'playforge:review:recent-tasks';
const MAX_RECENT_TASKS = 24;

export type ReviewTaskHistoryItem = PublicReviewTaskSummary & {
  source: 'server' | 'local';
};

const isTaskSummary = (value: unknown): value is PublicReviewTaskSummary => {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<PublicReviewTaskSummary>;
  return typeof candidate.publicId === 'string'
    && typeof candidate.title === 'string'
    && typeof candidate.provider === 'string'
    && typeof candidate.modelName === 'string'
    && typeof candidate.status === 'string'
    && typeof candidate.documentCount === 'number';
};

const normalizeTimestamp = (value: string | null | undefined) => value ?? null;

const sortByRecency = (left: PublicReviewTaskSummary, right: PublicReviewTaskSummary) => {
  const leftStamp = Date.parse(left.updatedAt ?? left.createdAt ?? '') || 0;
  const rightStamp = Date.parse(right.updatedAt ?? right.createdAt ?? '') || 0;
  return rightStamp - leftStamp;
};

export const loadRecentReviewTasks = (): PublicReviewTaskSummary[] => {
  if (typeof window === 'undefined') {
    return [];
  }

  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return [];
    }
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed.filter(isTaskSummary).sort(sortByRecency).slice(0, MAX_RECENT_TASKS);
  } catch {
    return [];
  }
};

export const saveRecentReviewTask = (task: PublicReviewTaskSummary) => {
  if (typeof window === 'undefined') {
    return;
  }

  const existing = loadRecentReviewTasks();
  const merged = [task, ...existing.filter((item) => item.publicId !== task.publicId)]
    .sort(sortByRecency)
    .slice(0, MAX_RECENT_TASKS);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(merged));
};

export const summarizeReviewTask = (task: PublicReviewTaskDetail): PublicReviewTaskSummary => ({
  publicId: task.publicId,
  title: task.title,
  provider: task.provider,
  modelName: task.modelName,
  status: task.status,
  documentCount: task.documentCount,
  hasOverallReport: Boolean(task.overallReportMarkdown),
  errorMessage: task.errorMessage,
  createdAt: normalizeTimestamp(task.createdAt),
  updatedAt: normalizeTimestamp(task.reportHistory[0]?.createdAt ?? task.createdAt),
});

export const mergeReviewTaskHistory = (
  serverTasks: PublicReviewTaskSummary[],
  localTasks: PublicReviewTaskSummary[]
): ReviewTaskHistoryItem[] => {
  const taskMap = new Map<string, ReviewTaskHistoryItem>();

  localTasks.forEach((task) => {
    taskMap.set(task.publicId, { ...task, source: 'local' });
  });

  serverTasks.forEach((task) => {
    const localTask = taskMap.get(task.publicId);
    taskMap.set(task.publicId, {
      ...localTask,
      ...task,
      createdAt: task.createdAt ?? localTask?.createdAt ?? null,
      updatedAt: task.updatedAt ?? localTask?.updatedAt ?? null,
      source: 'server',
    });
  });

  return Array.from(taskMap.values()).sort(sortByRecency);
};
