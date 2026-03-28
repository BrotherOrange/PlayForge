import { useEffect, useMemo, useRef, useState } from 'react';
import {
  getReviewAnnotationTypeLabel,
  getReviewPriorityLabel,
  getReviewRoleLabel,
  getReviewStatusLabel,
} from '../../constants/reviewUi';
import { PublicReviewAnnotationDetail, PublicReviewDocumentDetail, PublicReviewRoleRunDetail } from '../../types/api';

interface ReviewDocumentViewerProps {
  document: PublicReviewDocumentDetail;
  visibleAnnotations: PublicReviewAnnotationDetail[];
  activeAnnotationId: string | null;
  collapsed: boolean;
  onFocusAnnotation: (annotation: PublicReviewAnnotationDetail) => void;
  onToggleCollapsed: () => void;
}

interface TextSegment {
  key: string;
  text: string;
  annotations: PublicReviewAnnotationDetail[];
}

interface FlowLine {
  key: string;
  blockId: string;
  blockType: string;
  segments: TextSegment[];
  noteAnnotations: PublicReviewAnnotationDetail[];
  lineAnnotations: PublicReviewAnnotationDetail[];
  isBlank: boolean;
  isFirstLine: boolean;
  isLastLine: boolean;
}

interface PopoverState {
  annotations: PublicReviewAnnotationDetail[];
  left: number;
  top: number;
}

const priorityRank = (priority: string) => {
  switch (priority) {
    case 'high':
      return 0;
    case 'medium':
      return 1;
    default:
      return 2;
  }
};

const statusRank = (status: string) => {
  switch (status) {
    case 'running':
      return 0;
    case 'completed':
      return 1;
    case 'failed':
      return 2;
    case 'pending':
      return 3;
    default:
      return 4;
  }
};

const sortAnnotations = (annotations: PublicReviewAnnotationDetail[]) =>
  [...annotations].sort((left, right) => {
    const priorityDiff = priorityRank(left.priority) - priorityRank(right.priority);
    if (priorityDiff !== 0) {
      return priorityDiff;
    }
    return (left.createdAt ?? '').localeCompare(right.createdAt ?? '');
  });

const sortRoleRuns = (roleRuns: PublicReviewRoleRunDetail[]) =>
  [...roleRuns].sort((left, right) => {
    const statusDiff = statusRank(left.status) - statusRank(right.status);
    if (statusDiff !== 0) {
      return statusDiff;
    }
    return left.roleName.localeCompare(right.roleName);
  });

const hexToRgba = (hex: string, alpha: number) => {
  const normalized = hex.replace('#', '');
  const safeHex =
    normalized.length === 3 ? normalized.split('').map((value) => value + value).join('') : normalized;

  if (safeHex.length !== 6) {
    return `rgba(255, 255, 255, ${alpha})`;
  }

  const red = Number.parseInt(safeHex.slice(0, 2), 16);
  const green = Number.parseInt(safeHex.slice(2, 4), 16);
  const blue = Number.parseInt(safeHex.slice(4, 6), 16);
  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
};

const clampRange = (value: number, max: number) => Math.max(0, Math.min(max, value));

const buildSegments = (text: string, annotations: PublicReviewAnnotationDetail[]) => {
  if (!text) {
    return [] as TextSegment[];
  }

  const boundaries = new Set<number>([0, text.length]);
  annotations.forEach((annotation) => {
    boundaries.add(clampRange(annotation.startOffset, text.length));
    boundaries.add(clampRange(annotation.endOffset, text.length));
  });

  const sortedBoundaries = Array.from(boundaries).sort((left, right) => left - right);
  const segments: TextSegment[] = [];

  for (let index = 0; index < sortedBoundaries.length - 1; index += 1) {
    const start = sortedBoundaries[index];
    const end = sortedBoundaries[index + 1];
    if (start === end) {
      continue;
    }

    const matchingAnnotations = annotations.filter(
      (annotation) => annotation.startOffset < end && annotation.endOffset > start
    );

    segments.push({
      key: `${start}-${end}`,
      text: text.slice(start, end),
      annotations: matchingAnnotations,
    });
  }

  return segments;
};

const buildFlowLines = (blockId: string, blockType: string, text: string, annotations: PublicReviewAnnotationDetail[]) => {
  const source = text ?? '';
  const lines = source.split('\n');
  let lineStart = 0;

  return lines.map((lineText, index) => {
    const lineEnd = lineStart + lineText.length;
    const lineAnnotations = sortAnnotations(
      annotations.filter((annotation) => annotation.startOffset < lineEnd && annotation.endOffset > lineStart)
    );
    const noteAnnotations = sortAnnotations(
      annotations.filter((annotation) => {
        if (annotation.startOffset === annotation.endOffset && lineStart === lineEnd) {
          return annotation.startOffset === lineStart;
        }
        return annotation.startOffset >= lineStart && annotation.startOffset <= lineEnd;
      })
    );

    const localizedAnnotations = lineAnnotations.map((annotation) => ({
      ...annotation,
      startOffset: clampRange(annotation.startOffset - lineStart, lineText.length),
      endOffset: clampRange(annotation.endOffset - lineStart, lineText.length),
    }));

    const result: FlowLine = {
      key: `${blockId}:${index}`,
      blockId,
      blockType,
      segments: buildSegments(lineText, localizedAnnotations),
      noteAnnotations,
      lineAnnotations,
      isBlank: lineText.length === 0,
      isFirstLine: index === 0,
      isLastLine: index === lines.length - 1,
    };

    lineStart = lineEnd + 1;
    return result;
  });
};

const ReviewDocumentViewer = ({
  document,
  visibleAnnotations,
  activeAnnotationId,
  collapsed,
  onFocusAnnotation,
  onToggleCollapsed,
}: ReviewDocumentViewerProps) => {
  const [popover, setPopover] = useState<PopoverState | null>(null);
  const [freshAnnotationIds, setFreshAnnotationIds] = useState<string[]>([]);
  const annotationRowRefs = useRef<Record<string, HTMLDivElement | null>>({});
  const annotationRefs = useRef<Record<string, HTMLButtonElement | null>>({});
  const knownAnnotationIdsRef = useRef<Set<string>>(new Set(document.annotations.map((annotation) => annotation.id)));

  const annotationsByBlock = useMemo(() => {
    const mapping = new Map<string, PublicReviewAnnotationDetail[]>();
    visibleAnnotations.forEach((annotation) => {
      const items = mapping.get(annotation.blockId) ?? [];
      items.push(annotation);
      mapping.set(annotation.blockId, sortAnnotations(items));
    });
    return mapping;
  }, [visibleAnnotations]);

  const flowLines = useMemo(
    () =>
      document.blocks.flatMap((block) =>
        buildFlowLines(block.blockId, block.blockType, block.text ?? '', annotationsByBlock.get(block.blockId) ?? [])
      ),
    [annotationsByBlock, document.blocks]
  );

  const roleRuns = useMemo(() => sortRoleRuns(document.roleRuns), [document.roleRuns]);

  useEffect(() => {
    const previousIds = knownAnnotationIdsRef.current;
    const nextIds = new Set(document.annotations.map((annotation) => annotation.id));
    const newIds = document.annotations
      .filter((annotation) => !previousIds.has(annotation.id))
      .map((annotation) => annotation.id);

    if (newIds.length > 0) {
      setFreshAnnotationIds((current) => Array.from(new Set([...current, ...newIds])));
      const timer = window.setTimeout(() => {
        setFreshAnnotationIds((current) => current.filter((id) => !newIds.includes(id)));
      }, 3600);
      knownAnnotationIdsRef.current = nextIds;
      return () => window.clearTimeout(timer);
    }

    knownAnnotationIdsRef.current = nextIds;
    return undefined;
  }, [document.annotations]);

  useEffect(() => {
    if (!popover) {
      return undefined;
    }

    const closePopover = () => setPopover(null);
    window.addEventListener('click', closePopover);
    return () => window.removeEventListener('click', closePopover);
  }, [popover]);

  useEffect(() => {
    if (!activeAnnotationId || collapsed) {
      return;
    }

    const activeAnnotation = visibleAnnotations.find((annotation) => annotation.id === activeAnnotationId);
    if (!activeAnnotation) {
      return;
    }

    annotationRowRefs.current[activeAnnotation.id]?.scrollIntoView({
      behavior: 'smooth',
      block: 'center',
    });

    window.setTimeout(() => {
      annotationRefs.current[activeAnnotation.id]?.scrollIntoView({
        behavior: 'smooth',
        block: 'nearest',
        inline: 'nearest',
      });
    }, 120);
  }, [activeAnnotationId, collapsed, visibleAnnotations]);

  const isAwaitingConversion =
    document.blocks.length === 0 && (document.status === 'created' || document.status === 'processing');
  const filteredAnnotationCount = visibleAnnotations.length;

  const handleSegmentClick = (
    event: React.MouseEvent<HTMLButtonElement>,
    annotations: PublicReviewAnnotationDetail[]
  ) => {
    event.stopPropagation();
    if (annotations.length === 0) {
      return;
    }

    if (annotations.length === 1) {
      onFocusAnnotation(annotations[0]);
      setPopover(null);
      return;
    }

    setPopover({
      annotations: sortAnnotations(annotations),
      left: event.clientX,
      top: event.clientY,
    });
  };

  return (
    <section className={`pf-review-document-card ${collapsed ? 'collapsed' : ''}`}>
      <div className="pf-review-document-header">
        <div className="pf-review-document-title-wrap">
          <div>
            <h2>{document.title}</h2>
            <p>{document.sourceType === 'file' ? document.originalFilename ?? '已上传文件' : '直接输入内容'}</p>
          </div>
          <div className="pf-review-document-mini-meta">
            <span>{filteredAnnotationCount} 条可见批注</span>
            <span>{document.blocks.length} 段内容</span>
          </div>
        </div>

        <div className="pf-review-document-header-actions">
          <div className={`pf-review-status-chip ${document.status}`}>{getReviewStatusLabel(document.status)}</div>
          <button className="pf-review-button ghost pf-review-document-toggle" onClick={onToggleCollapsed} type="button">
            {collapsed ? '展开文档' : '折叠文档'}
          </button>
        </div>
      </div>

      <div className="pf-review-role-run-row">
        {roleRuns.map((roleRun) => (
          <div className={`pf-review-role-run-chip ${roleRun.status}`} key={roleRun.id}>
            <span className="pf-review-role-swatch" style={{ backgroundColor: roleRun.roleColor }} />
            <strong>{getReviewRoleLabel(roleRun.roleKey, roleRun.roleName)}</strong>
            <span>{getReviewStatusLabel(roleRun.status)}</span>
            <em>{roleRun.annotationCount}</em>
          </div>
        ))}
      </div>

      {document.warnings.length > 0 && (
        <div className="pf-review-warning-list">
          {document.warnings.map((warning) => (
            <span key={warning}>{warning}</span>
          ))}
        </div>
      )}

      {document.errorMessage && <div className="pf-review-error-banner">{document.errorMessage}</div>}

      {!collapsed && (
        <div className="pf-review-document-viewport">
          {isAwaitingConversion ? (
            <div className="pf-review-document-placeholder">
              <div className="pf-review-document-placeholder-text">
                任务已经创建成功，正在等待 Gemini 把原始文档整理成 Markdown。
              </div>
              <div className="pf-review-document-placeholder-note">
                转换完成后，左侧正文会先填充进来，右侧批注再按对应位置一条条流式出现。
              </div>
            </div>
          ) : (
            <div className="pf-review-document-flow">
              {flowLines.map((line) => {
                const hasNotes = line.noteAnnotations.length > 0;
                const activeInLine = line.lineAnnotations.some((annotation) => annotation.id === activeAnnotationId);

                return (
                  <div
                    className={`pf-review-flow-line ${hasNotes ? 'has-notes' : 'no-notes'} ${line.blockType} ${
                      line.isFirstLine ? 'first-line' : ''
                    } ${line.isLastLine ? 'last-line' : ''} ${line.isBlank ? 'blank' : ''} ${
                      activeInLine ? 'active' : ''
                    }`}
                    key={line.key}
                    ref={(element) => {
                      line.noteAnnotations.forEach((annotation) => {
                        annotationRowRefs.current[annotation.id] = element;
                      });
                    }}
                  >
                    <div className={`pf-review-flow-main ${line.blockType}`}>
                      <div className="pf-review-flow-content">
                        {line.segments.length > 0 ? (
                          line.segments.map((segment) => {
                            if (segment.annotations.length === 0) {
                              return <span key={segment.key}>{segment.text}</span>;
                            }

                            const activeAnnotation = segment.annotations.find((annotation) => annotation.id === activeAnnotationId);
                            const dominantAnnotation = activeAnnotation ?? segment.annotations[segment.annotations.length - 1];
                            const overlap = segment.annotations.length > 1;

                            return (
                              <button
                                className={`pf-review-highlight ${overlap ? 'overlap' : ''} ${activeAnnotation ? 'active' : ''}`}
                                key={segment.key}
                                onClick={(event) => handleSegmentClick(event, segment.annotations)}
                                style={{
                                  backgroundColor: hexToRgba(dominantAnnotation.roleColor, activeAnnotation ? 0.48 : 0.3),
                                  boxShadow: overlap
                                    ? `inset 0 -2px 0 ${hexToRgba(dominantAnnotation.roleColor, 0.92)}`
                                    : undefined,
                                }}
                                type="button"
                              >
                                {segment.text}
                                {overlap && <span className="pf-review-overlap-badge">{segment.annotations.length}</span>}
                              </button>
                            );
                          })
                        ) : (
                          <span>{line.isBlank ? '\u00A0' : ''}</span>
                        )}
                      </div>
                    </div>

                    {hasNotes && (
                      <aside className="pf-review-flow-notes">
                        {line.noteAnnotations.map((annotation) => {
                          const isActive = annotation.id === activeAnnotationId;
                          const isFresh = freshAnnotationIds.includes(annotation.id);

                          return (
                            <button
                              className={`pf-review-inline-annotation ${isActive ? 'active' : ''} ${isFresh ? 'fresh' : ''}`}
                              key={annotation.id}
                              onClick={() => onFocusAnnotation(annotation)}
                              ref={(element) => {
                                annotationRefs.current[annotation.id] = element;
                              }}
                              style={{
                                borderColor: hexToRgba(annotation.roleColor, isActive ? 0.85 : 0.35),
                                boxShadow: isActive
                                  ? `0 0 0 1px ${hexToRgba(annotation.roleColor, 0.42)}`
                                  : undefined,
                              }}
                              type="button"
                            >
                              <div className="pf-review-inline-annotation-head">
                                <span className="pf-review-role-swatch" style={{ backgroundColor: annotation.roleColor }} />
                                <strong>{getReviewRoleLabel(annotation.roleKey, annotation.roleName)}</strong>
                                <span>{getReviewAnnotationTypeLabel(annotation.annotationType)}</span>
                                <span>{getReviewPriorityLabel(annotation.priority)}</span>
                              </div>
                              <h3>{annotation.title}</h3>
                              <p>{annotation.content}</p>
                              <div className="pf-review-annotation-quote">{annotation.quoteText}</div>
                            </button>
                          );
                        })}
                      </aside>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {popover && !collapsed && (
        <div
          className="pf-review-role-popover"
          onClick={(event) => event.stopPropagation()}
          style={{ left: popover.left + 12, top: popover.top + 12 }}
        >
          <div className="pf-review-role-popover-title">这段文字有重叠批注，请选择你要聚焦的角色。</div>
          {popover.annotations.map((annotation) => (
            <button
              className="pf-review-role-option"
              key={annotation.id}
              onClick={() => {
                onFocusAnnotation(annotation);
                setPopover(null);
              }}
              type="button"
            >
              <span className="pf-review-role-option-meta">
                <span className="pf-review-role-swatch" style={{ backgroundColor: annotation.roleColor }} />
                <span>{getReviewRoleLabel(annotation.roleKey, annotation.roleName)}</span>
                <span>{getReviewPriorityLabel(annotation.priority)}</span>
              </span>
              <strong>{annotation.title}</strong>
              <span>{annotation.quoteText}</span>
            </button>
          ))}
        </div>
      )}
    </section>
  );
};

export default ReviewDocumentViewer;
