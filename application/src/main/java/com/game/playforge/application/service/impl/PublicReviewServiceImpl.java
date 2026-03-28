package com.game.playforge.application.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.playforge.application.dto.review.PublicReviewAnnotationDetail;
import com.game.playforge.application.dto.review.PublicReviewCreateCommand;
import com.game.playforge.application.dto.review.PublicReviewCreateResult;
import com.game.playforge.application.dto.review.PublicReviewDocumentDetail;
import com.game.playforge.application.dto.review.PublicReviewReportHistoryDetail;
import com.game.playforge.application.dto.review.PublicReviewRoleRunDetail;
import com.game.playforge.application.dto.review.PublicReviewTaskDetail;
import com.game.playforge.application.dto.review.PublicReviewTaskSummary;
import com.game.playforge.application.dto.review.ReviewBlockData;
import com.game.playforge.application.dto.review.ReviewInputItem;
import com.game.playforge.application.service.PublicReviewService;
import com.game.playforge.application.service.agent.AgentScopedChatModel;
import com.game.playforge.application.service.review.ReviewMarkdownBlockParser;
import com.game.playforge.application.service.review.PublicReviewStreamPublisher;
import com.game.playforge.common.enums.ModelProvider;
import com.game.playforge.common.enums.ReviewAnnotationType;
import com.game.playforge.common.enums.ReviewPriority;
import com.game.playforge.common.enums.ReviewRoleStatus;
import com.game.playforge.common.enums.ReviewSourceType;
import com.game.playforge.common.enums.ReviewTaskStatus;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.domain.model.ReviewAnnotation;
import com.game.playforge.domain.model.ReviewDocument;
import com.game.playforge.domain.model.ReviewReportHistory;
import com.game.playforge.domain.model.ReviewRoleRun;
import com.game.playforge.domain.model.ReviewTask;
import com.game.playforge.domain.repository.ReviewAnnotationRepository;
import com.game.playforge.domain.repository.ReviewDocumentRepository;
import com.game.playforge.domain.repository.ReviewReportHistoryRepository;
import com.game.playforge.domain.repository.ReviewRoleRunRepository;
import com.game.playforge.domain.repository.ReviewTaskRepository;
import com.game.playforge.infrastructure.external.document.DocumentConversionService;
import com.game.playforge.infrastructure.external.ai.ModelProviderRegistry;
import com.game.playforge.infrastructure.external.oss.OssService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublicReviewServiceImpl implements PublicReviewService {

    private static final int MAX_INPUT_ITEMS = 3;
    private static final int CHUNK_CHAR_LIMIT = 6000;
    private static final int MAX_ANNOTATIONS_PER_CHUNK = 8;
    private static final int MAX_QUOTE_LENGTH = 500;
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_ERROR_LENGTH = 1000;
    private static final int MAX_BLOCK_FALLBACK_LENGTH = 120;
    private static final int MAX_JSON_REPAIR_INPUT_LENGTH = 12_000;
    private static final int REPORT_HISTORY_LIMIT = 12;
    private static final int MAX_REPORT_SOURCE_LENGTH_PER_DOCUMENT = 12_000;
    private static final int MAX_REPORT_ANNOTATIONS_PER_DOCUMENT = 18;
    private static final String REPORT_SCOPE_OVERALL = "overall";
    private static final DateTimeFormatter TITLE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);
    private static final TypeReference<List<ReviewBlockData>> BLOCK_LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final Pattern CODE_FENCE_PATTERN = Pattern.compile("(?is)```(?:json|xml)?\\s*(.*?)\\s*```");
    private static final Pattern ANNOTATIONS_XML_PATTERN = Pattern.compile("(?is)<annotations\\b[^>]*>.*?</annotations>");
    private static final Pattern ANNOTATION_XML_PATTERN = Pattern.compile("(?is)<annotation\\b[^>]*>(.*?)</annotation>");
    private static final Pattern CDATA_PATTERN = Pattern.compile("(?is)^<!\\[CDATA\\[(.*)]]>$");
    private static final Set<String> ALLOWED_MODEL_KEYS = Set.of(
            "openai:gpt-5.4-mini",
            "anthropic:claude-sonnet-4-6",
            "gemini:gemini-3.1-flash-lite-preview"
    );

    private static final List<ReviewRoleDefinition> REVIEW_ROLES = List.of(
            new ReviewRoleDefinition("planner", "Planner", "#3b82f6",
                    "Review from the perspective of product goals, gameplay intent, and clarity of scope.",
                    "Focus on unclear goals, missing requirements, conflicting design intent, weak success criteria, and ambiguity that would confuse execution."),
            new ReviewRoleDefinition("engineer", "Engineer", "#22c55e",
                    "Review from the perspective of implementation feasibility and system clarity.",
                    "Focus on missing technical constraints, undefined data flows, unrealistic complexity, hidden dependencies, and integration risks."),
            new ReviewRoleDefinition("qa", "QA", "#8b5cf6",
                    "Review from the perspective of testability, edge cases, and operational risk.",
                    "Focus on ambiguous expected results, missing edge cases, unclear failure handling, inconsistent rules, and weak acceptance criteria."),
            new ReviewRoleDefinition("art", "Art", "#f97316",
                    "Review from the perspective of art production, asset scope, and visual clarity.",
                    "Focus on vague visual requirements, missing asset definitions, style ambiguity, content scope risk, and coordination gaps with production."),
            new ReviewRoleDefinition("ui", "UI", "#ec4899",
                    "Review from the perspective of interface flow, readability, and interaction clarity.",
                    "Focus on unclear states, weak information hierarchy, ambiguous interaction rules, missing feedback, and usability friction."),
            new ReviewRoleDefinition("pm", "PM", "#6366f1",
                    "Review from the perspective of delivery planning, stakeholder alignment, and execution risk.",
                    "Focus on prioritization gaps, unclear ownership, missing milestones, unresolved assumptions, and scope that is hard to schedule."),
            new ReviewRoleDefinition("player", "Player", "#ef4444",
                    "Review from the perspective of player experience, comprehension, and perceived value.",
                    "Focus on confusion, frustration, weak motivation, missing payoff, unfairness, and places where the document fails to explain the player-facing experience.")
    );

    private final ReviewTaskRepository reviewTaskRepository;
    private final ReviewDocumentRepository reviewDocumentRepository;
    private final ReviewRoleRunRepository reviewRoleRunRepository;
    private final ReviewAnnotationRepository reviewAnnotationRepository;
    private final ReviewReportHistoryRepository reviewReportHistoryRepository;
    private final DocumentConversionService documentConversionService;
    private final ModelProviderRegistry modelProviderRegistry;
    private final ReviewMarkdownBlockParser reviewMarkdownBlockParser;
    private final ObjectMapper objectMapper;
    private final OssService ossService;
    private final PublicReviewStreamPublisher publicReviewStreamPublisher;

    private ExecutorService reviewExecutor;

    @PostConstruct
    void initExecutor() {
        reviewExecutor = Executors.newVirtualThreadPerTaskExecutor();
        failInterruptedTasks();
    }

    @PreDestroy
    void shutdownExecutor() {
        if (reviewExecutor != null) {
            reviewExecutor.shutdown();
        }
    }

    @Override
    @Transactional
    public PublicReviewCreateResult createReviewTask(PublicReviewCreateCommand command) {
        List<ReviewInputItem> items = sanitizeItems(command.items());
        validateModel(command.provider(), command.modelName());

        String taskTitle = resolveTaskTitle(command.taskTitle(), items);
        ReviewTask reviewTask = new ReviewTask();
        reviewTask.setPublicId(generatePublicId());
        reviewTask.setTitle(taskTitle);
        reviewTask.setProvider(command.provider());
        reviewTask.setModelName(command.modelName());
        reviewTask.setStatus(ReviewTaskStatus.CREATED.getValue());
        reviewTask.setDocumentCount(items.size());
        reviewTask.setCreatedByUserId(command.creatorUserId());
        reviewTaskRepository.insert(reviewTask);

        for (int index = 0; index < items.size(); index += 1) {
            ReviewInputItem item = items.get(index);
            persistDocument(reviewTask, item, index + 1);
        }

        submitReviewTask(reviewTask.getId(), items);
        return new PublicReviewCreateResult(reviewTask.getPublicId(), reviewTask.getStatus(), reviewTask.getTitle());
    }

    @Override
    public List<PublicReviewTaskSummary> listReviewTasks(Long userId, int limit) {
        if (userId == null) {
            throw new BusinessException(ResultCode.NOT_LOGGED_IN);
        }
        return reviewTaskRepository.findRecentByUserId(userId, limit).stream()
                .map(this::toTaskSummary)
                .toList();
    }

    @Override
    public PublicReviewTaskDetail getReviewTask(String publicId) {
        ReviewTask reviewTask = reviewTaskRepository.findByPublicId(publicId);
        if (reviewTask == null) {
            throw new BusinessException(ResultCode.REVIEW_TASK_NOT_FOUND);
        }

        List<ReviewDocument> documents = reviewDocumentRepository.findByTaskId(reviewTask.getId());
        List<Long> documentIds = documents.stream().map(ReviewDocument::getId).toList();
        Map<Long, List<ReviewRoleRun>> roleRunsByDocument = reviewRoleRunRepository.findByDocumentIds(documentIds).stream()
                .collect(Collectors.groupingBy(ReviewRoleRun::getDocumentId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<ReviewAnnotation>> annotationsByDocument = reviewAnnotationRepository.findByDocumentIds(documentIds).stream()
                .collect(Collectors.groupingBy(ReviewAnnotation::getDocumentId, LinkedHashMap::new, Collectors.toList()));

        List<PublicReviewDocumentDetail> documentDetails = documents.stream()
                .map(document -> toDocumentDetail(
                        document,
                        roleRunsByDocument.getOrDefault(document.getId(), List.of()),
                        annotationsByDocument.getOrDefault(document.getId(), List.of())
                ))
                .toList();
        List<PublicReviewReportHistoryDetail> reportHistory = reviewReportHistoryRepository
                .findByTaskId(reviewTask.getId(), REPORT_SCOPE_OVERALL, REPORT_HISTORY_LIMIT)
                .stream()
                .map(this::toReportHistoryDetail)
                .toList();

        return new PublicReviewTaskDetail(
                reviewTask.getPublicId(),
                reviewTask.getTitle(),
                reviewTask.getProvider(),
                reviewTask.getModelName(),
                reviewTask.getStatus(),
                reviewTask.getDocumentCount() == null ? 0 : reviewTask.getDocumentCount(),
                reviewTask.getOverallReportMarkdown(),
                reviewTask.getErrorMessage(),
                formatDateTime(reviewTask.getCreatedAt()),
                documentDetails,
                reportHistory
        );
    }

    @Override
    @Transactional
    public PublicReviewTaskDetail generateOverallReport(String publicId) {
        ReviewTask reviewTask = reviewTaskRepository.findByPublicId(publicId);
        if (reviewTask == null) {
            throw new BusinessException(ResultCode.REVIEW_TASK_NOT_FOUND);
        }
        if (ReviewTaskStatus.CREATED.getValue().equals(reviewTask.getStatus())
                || ReviewTaskStatus.PROCESSING.getValue().equals(reviewTask.getStatus())) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Review task is still processing.");
        }

        List<ReviewDocument> documents = reviewDocumentRepository.findByTaskId(reviewTask.getId());
        List<Long> documentIds = documents.stream().map(ReviewDocument::getId).toList();
        List<ReviewAnnotation> annotations = reviewAnnotationRepository.findByDocumentIds(documentIds);
        Map<Long, List<ReviewRoleRun>> roleRunsByDocument = reviewRoleRunRepository.findByDocumentIds(documentIds).stream()
                .collect(Collectors.groupingBy(ReviewRoleRun::getDocumentId, LinkedHashMap::new, Collectors.toList()));

        String overallReportMarkdown = generateOverallReportMarkdown(reviewTask, documents, roleRunsByDocument, annotations);
        reviewTask.setOverallReportMarkdown(overallReportMarkdown);
        reviewTaskRepository.update(reviewTask);
        ReviewReportHistory reviewReportHistory = new ReviewReportHistory();
        reviewReportHistory.setTaskId(reviewTask.getId());
        reviewReportHistory.setDocumentId(null);
        reviewReportHistory.setReportScope(REPORT_SCOPE_OVERALL);
        reviewReportHistory.setTitle(reviewTask.getTitle());
        reviewReportHistory.setProvider(reviewTask.getProvider());
        reviewReportHistory.setModelName(reviewTask.getModelName());
        reviewReportHistory.setReportMarkdown(overallReportMarkdown);
        reviewReportHistoryRepository.insert(reviewReportHistory);

        PublicReviewTaskDetail detail = getReviewTask(publicId);
        publicReviewStreamPublisher.publishSnapshot(publicId, detail);
        return detail;
    }

    private void persistDocument(ReviewTask reviewTask, ReviewInputItem item, int sortOrder) {
        ReviewDocument reviewDocument = new ReviewDocument();
        reviewDocument.setTaskId(reviewTask.getId());
        reviewDocument.setSourceType(item.sourceType().getValue());
        reviewDocument.setSortOrder(sortOrder);
        reviewDocument.setTitle(resolveInitialDocumentTitle(item));
        reviewDocument.setOriginalFilename(trimToNull(item.originalFilename()));
        reviewDocument.setMarkdownContent("");
        reviewDocument.setBlocksJson(writeJson(List.of()));
        reviewDocument.setWarningsJson(writeJson(List.of()));
        reviewDocument.setStatus(ReviewTaskStatus.CREATED.getValue());
        reviewDocumentRepository.insert(reviewDocument);

        for (ReviewRoleDefinition roleDefinition : REVIEW_ROLES) {
            ReviewRoleRun reviewRoleRun = new ReviewRoleRun();
            reviewRoleRun.setDocumentId(reviewDocument.getId());
            reviewRoleRun.setRoleKey(roleDefinition.key());
            reviewRoleRun.setRoleName(roleDefinition.label());
            reviewRoleRun.setRoleColor(roleDefinition.color());
            reviewRoleRun.setStatus(ReviewRoleStatus.PENDING.getValue());
            reviewRoleRun.setAnnotationCount(0);
            reviewRoleRunRepository.insert(reviewRoleRun);
        }
    }

    private void submitReviewTask(Long taskId, List<ReviewInputItem> items) {
        List<ReviewInputItem> capturedItems = List.copyOf(items);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reviewExecutor.execute(() -> processTask(taskId, capturedItems));
                }
            });
            return;
        }
        reviewExecutor.execute(() -> processTask(taskId, capturedItems));
    }

    private void processTask(Long taskId, List<ReviewInputItem> items) {
        ReviewTask reviewTask = reviewTaskRepository.findById(taskId);
        if (reviewTask == null) {
            return;
        }

        try {
            reviewTask.setStatus(ReviewTaskStatus.PROCESSING.getValue());
            reviewTask.setErrorMessage(null);
            reviewTaskRepository.update(reviewTask);
            publishTaskSnapshot(reviewTask.getPublicId());

            List<ReviewDocument> documents = reviewDocumentRepository.findByTaskId(taskId);
            List<CompletableFuture<Void>> futures = documents.stream()
                    .map(document -> CompletableFuture.runAsync(
                            () -> processDocument(reviewTask, document, resolveInputItem(items, document.getSortOrder())),
                            reviewExecutor
                    ))
                    .toList();
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

            List<ReviewDocument> refreshedDocuments = reviewDocumentRepository.findByTaskId(taskId);
            reviewTask.setStatus(determineTaskStatus(refreshedDocuments));
            reviewTask.setErrorMessage(null);
            reviewTaskRepository.update(reviewTask);
            publishTaskSnapshot(reviewTask.getPublicId());
            log.info("Completed public review task, taskId={}, status={}", taskId, reviewTask.getStatus());
        } catch (Exception e) {
            log.error("Failed to process public review task, taskId={}", taskId, e);
            reviewTask.setStatus(ReviewTaskStatus.FAILED.getValue());
            reviewTask.setErrorMessage(truncate(e.getMessage(), MAX_ERROR_LENGTH));
            reviewTaskRepository.update(reviewTask);
            publishTaskSnapshot(reviewTask.getPublicId());
        }
    }

    private void processDocument(ReviewTask reviewTask, ReviewDocument reviewDocument, ReviewInputItem item) {
        reviewDocument.setStatus(ReviewTaskStatus.PROCESSING.getValue());
        reviewDocument.setErrorMessage(null);
        reviewDocumentRepository.update(reviewDocument);
        publishTaskSnapshot(reviewTask.getPublicId());

        List<ReviewRoleRun> roleRuns = reviewRoleRunRepository.findByDocumentId(reviewDocument.getId());

        if (item == null) {
            failDocument(reviewTask, reviewDocument, roleRuns, "Review source payload is missing.");
            return;
        }

        NormalizedReviewInput normalizedInput;
        try {
            normalizedInput = normalizeInput(item);
        } catch (Exception e) {
            log.error("Failed to normalize review document, documentId={}", reviewDocument.getId(), e);
            failDocument(reviewTask, reviewDocument, roleRuns, truncate(e.getMessage(), MAX_ERROR_LENGTH));
            return;
        }

        List<ReviewBlockData> blocks = normalizedInput.blocks();
        Map<String, ReviewBlockData> blocksById = blocks.stream()
                .collect(Collectors.toMap(ReviewBlockData::blockId, block -> block, (left, right) -> left, LinkedHashMap::new));

        reviewDocument.setTitle(normalizedInput.title());
        reviewDocument.setOriginalFilename(normalizedInput.originalFilename());
        reviewDocument.setMarkdownContent(normalizedInput.markdown());
        reviewDocument.setBlocksJson(writeJson(blocks));
        reviewDocument.setWarningsJson(writeJson(normalizedInput.warnings()));
        reviewDocument.setSummaryMarkdown(null);
        reviewDocument.setErrorMessage(null);
        reviewDocumentRepository.update(reviewDocument);
        publishTaskSnapshot(reviewTask.getPublicId());

        List<CompletableFuture<Void>> futures = roleRuns.stream()
                .map(roleRun -> CompletableFuture.runAsync(
                        () -> processRoleRun(reviewTask, reviewDocument, roleRun, blocks, blocksById),
                        reviewExecutor
                ))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        List<ReviewRoleRun> refreshedRoleRuns = reviewRoleRunRepository.findByDocumentId(reviewDocument.getId());
        List<ReviewAnnotation> annotations = reviewAnnotationRepository.findByDocumentId(reviewDocument.getId());
        reviewDocument.setStatus(determineDocumentStatus(refreshedRoleRuns));
        reviewDocument.setSummaryMarkdown(buildDocumentSummary(reviewDocument, refreshedRoleRuns, annotations));
        reviewDocument.setErrorMessage(resolveDocumentError(refreshedRoleRuns));
        reviewDocumentRepository.update(reviewDocument);
        publishTaskSnapshot(reviewTask.getPublicId());
    }

    private void failDocument(
            ReviewTask reviewTask,
            ReviewDocument reviewDocument,
            List<ReviewRoleRun> roleRuns,
            String errorMessage
    ) {
        String safeErrorMessage = truncate(
                errorMessage == null || errorMessage.isBlank() ? "Document conversion failed." : errorMessage,
                MAX_ERROR_LENGTH
        );

        reviewDocument.setStatus(ReviewTaskStatus.FAILED.getValue());
        reviewDocument.setSummaryMarkdown(null);
        reviewDocument.setErrorMessage(safeErrorMessage);
        reviewDocumentRepository.update(reviewDocument);

        LocalDateTime completedAt = LocalDateTime.now();
        for (ReviewRoleRun roleRun : roleRuns) {
            if (ReviewRoleStatus.COMPLETED.getValue().equals(roleRun.getStatus())
                    || ReviewRoleStatus.FAILED.getValue().equals(roleRun.getStatus())) {
                continue;
            }
            roleRun.setStatus(ReviewRoleStatus.FAILED.getValue());
            roleRun.setErrorMessage(safeErrorMessage);
            roleRun.setCompletedAt(completedAt);
            reviewRoleRunRepository.update(roleRun);
        }

        publishTaskSnapshot(reviewTask.getPublicId());
    }

    private void processRoleRun(
            ReviewTask reviewTask,
            ReviewDocument reviewDocument,
            ReviewRoleRun reviewRoleRun,
            List<ReviewBlockData> blocks,
            Map<String, ReviewBlockData> blocksById
    ) {
        ReviewRoleDefinition roleDefinition = roleDefinition(reviewRoleRun.getRoleKey());
        reviewRoleRun.setStatus(ReviewRoleStatus.RUNNING.getValue());
        reviewRoleRun.setErrorMessage(null);
        reviewRoleRunRepository.update(reviewRoleRun);
        publishTaskSnapshot(reviewTask.getPublicId());

        try {
            ChatModel chatModel = createReviewModel(reviewTask, reviewDocument, roleDefinition);
            List<List<ReviewBlockData>> chunks = chunkBlocks(blocks);
            Set<String> dedupeKeys = new LinkedHashSet<>();
            int insertedAnnotationCount = 0;

            for (int chunkIndex = 0; chunkIndex < chunks.size(); chunkIndex += 1) {
                List<ReviewBlockData> chunk = chunks.get(chunkIndex);
                List<GeneratedAnnotation> generatedAnnotations = readGeneratedAnnotations(
                        chatModel,
                        reviewTask,
                        reviewDocument,
                        roleDefinition,
                        chatModel.chat(buildRolePrompt(reviewTask, reviewDocument, roleDefinition, chunk, chunkIndex + 1, chunks.size()))
                );

                for (GeneratedAnnotation generatedAnnotation : generatedAnnotations) {
                    ReviewBlockData targetBlock = blocksById.get(generatedAnnotation.blockId());
                    if (targetBlock == null || targetBlock.text() == null || targetBlock.text().isBlank()) {
                        continue;
                    }

                    LocatedSpan locatedSpan = locateQuote(targetBlock.text(), generatedAnnotation.quoteText());
                    ReviewAnnotationDraft draft = new ReviewAnnotationDraft(
                            targetBlock.blockId(),
                            locatedSpan.startOffset(),
                            locatedSpan.endOffset(),
                            locatedSpan.quoteText(),
                            normalizeAnnotationType(generatedAnnotation.annotationType()),
                            normalizePriority(generatedAnnotation.priority()),
                            truncate(sanitizeInline(generatedAnnotation.title()), MAX_TITLE_LENGTH),
                            sanitizeBody(generatedAnnotation.content())
                    );

                    if (draft.title().isBlank() || draft.content().isBlank()) {
                        continue;
                    }

                    String dedupeKey = String.join("|",
                            draft.blockId(),
                            String.valueOf(draft.startOffset()),
                            String.valueOf(draft.endOffset()),
                            reviewRoleRun.getRoleKey(),
                            draft.title().toLowerCase(Locale.ROOT)
                    );
                    if (!dedupeKeys.add(dedupeKey)) {
                        continue;
                    }

                    ReviewAnnotation reviewAnnotation = new ReviewAnnotation();
                    reviewAnnotation.setDocumentId(reviewDocument.getId());
                    reviewAnnotation.setRoleRunId(reviewRoleRun.getId());
                    reviewAnnotation.setRoleKey(reviewRoleRun.getRoleKey());
                    reviewAnnotation.setRoleName(reviewRoleRun.getRoleName());
                    reviewAnnotation.setRoleColor(reviewRoleRun.getRoleColor());
                    reviewAnnotation.setAnnotationType(draft.annotationType());
                    reviewAnnotation.setPriority(draft.priority());
                    reviewAnnotation.setBlockId(draft.blockId());
                    reviewAnnotation.setStartOffset(draft.startOffset());
                    reviewAnnotation.setEndOffset(draft.endOffset());
                    reviewAnnotation.setQuoteText(truncate(draft.quoteText(), MAX_QUOTE_LENGTH));
                    reviewAnnotation.setTitle(draft.title());
                    reviewAnnotation.setContent(draft.content());
                    reviewAnnotationRepository.insert(reviewAnnotation);

                    insertedAnnotationCount += 1;
                    reviewRoleRun.setAnnotationCount(insertedAnnotationCount);
                    reviewRoleRunRepository.update(reviewRoleRun);
                    publishTaskSnapshot(reviewTask.getPublicId());
                }
            }

            reviewRoleRun.setStatus(ReviewRoleStatus.COMPLETED.getValue());
            reviewRoleRun.setAnnotationCount(insertedAnnotationCount);
            reviewRoleRun.setCompletedAt(LocalDateTime.now());
            reviewRoleRun.setErrorMessage(null);
            reviewRoleRunRepository.update(reviewRoleRun);
            publishTaskSnapshot(reviewTask.getPublicId());
        } catch (Exception e) {
            log.warn("Role review failed, taskId={}, documentId={}, role={}",
                    reviewTask.getId(), reviewDocument.getId(), reviewRoleRun.getRoleKey(), e);
            reviewRoleRun.setStatus(ReviewRoleStatus.FAILED.getValue());
            reviewRoleRun.setErrorMessage(truncate(e.getMessage(), MAX_ERROR_LENGTH));
            reviewRoleRun.setCompletedAt(LocalDateTime.now());
            reviewRoleRunRepository.update(reviewRoleRun);
            publishTaskSnapshot(reviewTask.getPublicId());
        }
    }

    private ChatModel createReviewModel(ReviewTask reviewTask, ReviewDocument reviewDocument, ReviewRoleDefinition roleDefinition) {
        return createScopedModel(reviewTask, 0.1, 4096, Map.of(
                "reviewTaskId", reviewTask.getId(),
                "reviewDocumentId", reviewDocument.getId(),
                "reviewRoleKey", roleDefinition.key()
        ));
    }

    private ChatModel createOverallReportModel(ReviewTask reviewTask) {
        return createScopedModel(reviewTask, 0.2, 6144, Map.of(
                "reviewTaskId", reviewTask.getId(),
                "reviewScope", REPORT_SCOPE_OVERALL
        ));
    }

    private ChatModel createScopedModel(
            ReviewTask reviewTask,
            double temperature,
            int maxOutputTokens,
            Map<Object, Object> attributes
    ) {
        ModelProvider provider = resolveProvider(reviewTask.getProvider());
        ChatModel delegate = modelProviderRegistry.getChatModel(provider);

        var parametersBuilder = ChatRequestParameters.builder()
                .modelName(reviewTask.getModelName())
                .temperature(temperature);
        if (provider != ModelProvider.OPENAI) {
            parametersBuilder.maxOutputTokens(maxOutputTokens);
        }
        return new AgentScopedChatModel(delegate, parametersBuilder.build(), attributes);
    }

    private List<ReviewInputItem> sanitizeItems(List<ReviewInputItem> items) {
        List<ReviewInputItem> normalizedItems = items == null ? List.of() : items.stream()
                .filter(Objects::nonNull)
                .filter(item -> item.sourceType() != null)
                .filter(item -> {
                    if (item.sourceType() == ReviewSourceType.FILE) {
                        return (item.fileContent() != null && item.fileContent().length > 0)
                                || (item.objectKey() != null && !item.objectKey().isBlank());
                    }
                    return (item.title() != null && !item.title().isBlank())
                            || (item.textContent() != null && !item.textContent().isBlank());
                })
                .toList();

        if (normalizedItems.isEmpty()) {
            throw new BusinessException(ResultCode.REVIEW_INPUT_EMPTY);
        }
        if (normalizedItems.size() > MAX_INPUT_ITEMS) {
            throw new BusinessException(ResultCode.REVIEW_TASK_LIMIT_EXCEEDED);
        }
        return normalizedItems;
    }

    private void validateModel(String provider, String modelName) {
        String key = (provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT))
                + ":"
                + (modelName == null ? "" : modelName.trim());
        if (!ALLOWED_MODEL_KEYS.contains(key)) {
            throw new BusinessException(ResultCode.REVIEW_MODEL_UNSUPPORTED);
        }
    }

    private NormalizedReviewInput normalizeInput(ReviewInputItem item) {
        return switch (item.sourceType()) {
            case FILE -> normalizeFileInput(item);
            case TEXT -> normalizeTextInput(item);
        };
    }

    private String resolveTaskTitle(String requestedTitle, List<ReviewInputItem> items) {
        if (requestedTitle != null && !requestedTitle.isBlank()) {
            return requestedTitle.trim();
        }
        if (items.size() == 1) {
            return resolveInitialDocumentTitle(items.get(0));
        }
        return "Review Task " + TITLE_TIME_FORMATTER.format(LocalDateTime.now());
    }

    private String resolveInitialDocumentTitle(ReviewInputItem item) {
        return switch (item.sourceType()) {
            case FILE -> {
                String filename = trimToNull(item.originalFilename());
                if (filename == null) {
                    filename = extractFilenameFromObjectKey(item.objectKey());
                }
                yield resolveDocumentTitle(trimToNull(item.title()), stripExtension(filename), "Uploaded document");
            }
            case TEXT -> resolveDocumentTitle(trimToNull(item.title()), "Text document");
        };
    }

    private ReviewInputItem resolveInputItem(List<ReviewInputItem> items, Integer sortOrder) {
        if (items == null || sortOrder == null || sortOrder <= 0 || sortOrder > items.size()) {
            return null;
        }
        return items.get(sortOrder - 1);
    }

    private String generatePublicId() {
        return "review-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private PublicReviewDocumentDetail toDocumentDetail(
            ReviewDocument document,
            List<ReviewRoleRun> roleRuns,
            List<ReviewAnnotation> annotations
    ) {
        List<ReviewBlockData> blocks = readBlocks(document.getBlocksJson());
        List<String> warnings = readWarnings(document.getWarningsJson());
        List<PublicReviewRoleRunDetail> roleRunDetails = roleRuns.stream()
                .map(roleRun -> new PublicReviewRoleRunDetail(
                        String.valueOf(roleRun.getId()),
                        roleRun.getRoleKey(),
                        roleRun.getRoleName(),
                        roleRun.getRoleColor(),
                        roleRun.getStatus(),
                        roleRun.getAnnotationCount() == null ? 0 : roleRun.getAnnotationCount(),
                        roleRun.getErrorMessage(),
                        formatDateTime(roleRun.getCompletedAt())
                ))
                .toList();
        List<PublicReviewAnnotationDetail> annotationDetails = annotations.stream()
                .map(annotation -> new PublicReviewAnnotationDetail(
                        String.valueOf(annotation.getId()),
                        String.valueOf(annotation.getRoleRunId()),
                        annotation.getRoleKey(),
                        annotation.getRoleName(),
                        annotation.getRoleColor(),
                        annotation.getAnnotationType(),
                        annotation.getPriority(),
                        annotation.getBlockId(),
                        annotation.getStartOffset() == null ? 0 : annotation.getStartOffset(),
                        annotation.getEndOffset() == null ? 0 : annotation.getEndOffset(),
                        annotation.getQuoteText(),
                        annotation.getTitle(),
                        annotation.getContent(),
                        formatDateTime(annotation.getCreatedAt())
                ))
                .toList();

        return new PublicReviewDocumentDetail(
                String.valueOf(document.getId()),
                document.getSourceType(),
                document.getSortOrder() == null ? 0 : document.getSortOrder(),
                document.getTitle(),
                document.getOriginalFilename(),
                document.getStatus(),
                warnings,
                document.getSummaryMarkdown(),
                document.getErrorMessage(),
                blocks,
                roleRunDetails,
                annotationDetails
        );
    }

    private PublicReviewReportHistoryDetail toReportHistoryDetail(ReviewReportHistory reportHistory) {
        return new PublicReviewReportHistoryDetail(
                String.valueOf(reportHistory.getId()),
                reportHistory.getReportScope(),
                reportHistory.getTitle(),
                reportHistory.getProvider(),
                reportHistory.getModelName(),
                reportHistory.getReportMarkdown(),
                formatDateTime(reportHistory.getCreatedAt())
        );
    }

    private PublicReviewTaskSummary toTaskSummary(ReviewTask reviewTask) {
        return new PublicReviewTaskSummary(
                reviewTask.getPublicId(),
                reviewTask.getTitle(),
                reviewTask.getProvider(),
                reviewTask.getModelName(),
                reviewTask.getStatus(),
                reviewTask.getDocumentCount() == null ? 0 : reviewTask.getDocumentCount(),
                reviewTask.getOverallReportMarkdown() != null && !reviewTask.getOverallReportMarkdown().isBlank(),
                reviewTask.getErrorMessage(),
                formatDateTime(reviewTask.getCreatedAt()),
                formatDateTime(reviewTask.getUpdatedAt())
        );
    }

    private void publishTaskSnapshot(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return;
        }
        try {
            publicReviewStreamPublisher.publishSnapshot(publicId, getReviewTask(publicId));
        } catch (Exception e) {
            log.debug("Skipping public review SSE snapshot publish, publicId={}", publicId, e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize review payload.", e);
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    private void failInterruptedTasks() {
        List<ReviewTask> tasks = reviewTaskRepository.findByStatuses(List.of(
                ReviewTaskStatus.CREATED.getValue(),
                ReviewTaskStatus.PROCESSING.getValue()
        ));

        for (ReviewTask task : tasks) {
            task.setStatus(ReviewTaskStatus.FAILED.getValue());
            task.setErrorMessage("Processing stopped during a previous restart.");
            reviewTaskRepository.update(task);

            List<ReviewDocument> documents = reviewDocumentRepository.findByTaskId(task.getId());
            for (ReviewDocument document : documents) {
                if (ReviewTaskStatus.COMPLETED.getValue().equals(document.getStatus())
                        || ReviewTaskStatus.PARTIAL.getValue().equals(document.getStatus())
                        || ReviewTaskStatus.FAILED.getValue().equals(document.getStatus())) {
                    continue;
                }
                document.setStatus(ReviewTaskStatus.FAILED.getValue());
                document.setErrorMessage("Processing stopped during a previous restart.");
                reviewDocumentRepository.update(document);

                List<ReviewRoleRun> roleRuns = reviewRoleRunRepository.findByDocumentId(document.getId());
                for (ReviewRoleRun roleRun : roleRuns) {
                    if (ReviewRoleStatus.COMPLETED.getValue().equals(roleRun.getStatus())
                            || ReviewRoleStatus.FAILED.getValue().equals(roleRun.getStatus())) {
                        continue;
                    }
                    roleRun.setStatus(ReviewRoleStatus.FAILED.getValue());
                    roleRun.setErrorMessage("Processing stopped during a previous restart.");
                    roleRun.setCompletedAt(LocalDateTime.now());
                    reviewRoleRunRepository.update(roleRun);
                }
            }
        }
    }

    private NormalizedReviewInput normalizeFileInput(ReviewInputItem item) {
        String conversionFilename = resolveConversionFilename(item);
        String storedFilename = resolveStoredFilename(item, conversionFilename);
        byte[] fileBytes = resolveFileBytes(item);
        var conversionResult = documentConversionService.convert(
                conversionFilename,
                item.contentType(),
                fileBytes
        );
        List<ReviewBlockData> blocks = reviewMarkdownBlockParser.parse(conversionResult.markdown());
        String title = resolveDocumentTitle(
                item.title(),
                stripExtension(storedFilename),
                extractHeading(blocks),
                "Uploaded document"
        );
        return new NormalizedReviewInput(
                ReviewSourceType.FILE,
                title,
                storedFilename,
                conversionResult.markdown(),
                blocks,
                conversionResult.warnings() == null ? List.of() : conversionResult.warnings()
        );
    }

    private NormalizedReviewInput normalizeTextInput(ReviewInputItem item) {
        String markdown = documentConversionService.normalizeText(item.title(), item.textContent());
        List<ReviewBlockData> blocks = reviewMarkdownBlockParser.parse(markdown);
        String title = resolveDocumentTitle(item.title(), extractHeading(blocks), "Text document");
        return new NormalizedReviewInput(
                ReviewSourceType.TEXT,
                title,
                null,
                markdown,
                blocks,
                List.of()
        );
    }

    private byte[] resolveFileBytes(ReviewInputItem item) {
        if (item.fileContent() != null && item.fileContent().length > 0) {
            return item.fileContent();
        }
        if (item.objectKey() != null && !item.objectKey().isBlank()) {
            return ossService.downloadObjectBytes(item.objectKey().trim());
        }
        throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Review file payload is missing.");
    }

    private String resolveConversionFilename(ReviewInputItem item) {
        String originalFilename = trimToNull(item.originalFilename());
        if (originalFilename != null && hasFileExtension(originalFilename)) {
            return originalFilename;
        }

        String objectKeyFilename = extractFilenameFromObjectKey(item.objectKey());
        if (objectKeyFilename != null && hasFileExtension(objectKeyFilename)) {
            if (originalFilename != null) {
                log.warn(
                        "Review upload filename missing extension, falling back to OSS object key. originalFilename={}, objectKey={}",
                        originalFilename,
                        item.objectKey()
                );
            }
            return objectKeyFilename;
        }

        if (originalFilename != null) {
            return originalFilename;
        }

        throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Uploaded file name is required.");
    }

    private String resolveStoredFilename(ReviewInputItem item, String fallbackFilename) {
        String originalFilename = trimToNull(item.originalFilename());
        return originalFilename != null ? originalFilename : fallbackFilename;
    }

    private String extractFilenameFromObjectKey(String objectKey) {
        String normalizedKey = trimToNull(objectKey);
        if (normalizedKey == null) {
            return null;
        }

        int lastSlash = normalizedKey.lastIndexOf('/');
        return lastSlash >= 0 ? normalizedKey.substring(lastSlash + 1) : normalizedKey;
    }

    private boolean hasFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > -1 && lastDot < filename.length() - 1;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveDocumentTitle(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return "Untitled document";
    }

    private String extractHeading(List<ReviewBlockData> blocks) {
        return blocks.stream()
                .filter(block -> "heading".equals(block.blockType()))
                .map(ReviewBlockData::text)
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse(null);
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot <= 0) {
            return filename;
        }
        return filename.substring(0, lastDot);
    }

    private String determineTaskStatus(List<ReviewDocument> documents) {
        Collection<String> statuses = documents.stream().map(ReviewDocument::getStatus).toList();
        return determineAggregateTaskStatus(statuses);
    }

    private String determineDocumentStatus(List<ReviewRoleRun> roleRuns) {
        Collection<String> statuses = roleRuns.stream().map(ReviewRoleRun::getStatus).toList();
        return determineAggregateRoleStatus(statuses);
    }

    private String determineAggregateTaskStatus(Collection<String> statuses) {
        if (statuses.stream().allMatch(ReviewTaskStatus.COMPLETED.getValue()::equals)) {
            return ReviewTaskStatus.COMPLETED.getValue();
        }
        if (statuses.stream().anyMatch(ReviewTaskStatus.PROCESSING.getValue()::equals)
                || statuses.stream().anyMatch(ReviewTaskStatus.CREATED.getValue()::equals)) {
            return ReviewTaskStatus.PROCESSING.getValue();
        }
        if (statuses.stream().anyMatch(ReviewTaskStatus.PARTIAL.getValue()::equals)) {
            return ReviewTaskStatus.PARTIAL.getValue();
        }
        boolean anySucceeded = statuses.stream().anyMatch(status ->
                ReviewTaskStatus.COMPLETED.getValue().equals(status) || ReviewTaskStatus.PARTIAL.getValue().equals(status));
        boolean anyFailed = statuses.stream().anyMatch(ReviewTaskStatus.FAILED.getValue()::equals);
        if (anySucceeded && anyFailed) {
            return ReviewTaskStatus.PARTIAL.getValue();
        }
        if (anyFailed) {
            return ReviewTaskStatus.FAILED.getValue();
        }
        return ReviewTaskStatus.CREATED.getValue();
    }

    private String determineAggregateRoleStatus(Collection<String> statuses) {
        if (statuses.stream().allMatch(ReviewRoleStatus.COMPLETED.getValue()::equals)) {
            return ReviewTaskStatus.COMPLETED.getValue();
        }
        if (statuses.stream().anyMatch(ReviewRoleStatus.RUNNING.getValue()::equals)
                || statuses.stream().anyMatch(ReviewRoleStatus.PENDING.getValue()::equals)) {
            return ReviewTaskStatus.PROCESSING.getValue();
        }
        boolean anyCompleted = statuses.stream().anyMatch(ReviewRoleStatus.COMPLETED.getValue()::equals);
        boolean anyFailed = statuses.stream().anyMatch(ReviewRoleStatus.FAILED.getValue()::equals);
        if (anyCompleted && anyFailed) {
            return ReviewTaskStatus.PARTIAL.getValue();
        }
        if (anyFailed) {
            return ReviewTaskStatus.FAILED.getValue();
        }
        return ReviewTaskStatus.CREATED.getValue();
    }

    private String resolveDocumentError(List<ReviewRoleRun> roleRuns) {
        return roleRuns.stream()
                .map(ReviewRoleRun::getErrorMessage)
                .filter(message -> message != null && !message.isBlank())
                .findFirst()
                .orElse(null);
    }

    private List<List<ReviewBlockData>> chunkBlocks(List<ReviewBlockData> blocks) {
        List<List<ReviewBlockData>> chunks = new ArrayList<>();
        List<ReviewBlockData> currentChunk = new ArrayList<>();
        int currentSize = 0;

        for (ReviewBlockData block : blocks) {
            int blockSize = block.text() == null ? 0 : block.text().length();
            if (!currentChunk.isEmpty() && currentSize + blockSize > CHUNK_CHAR_LIMIT) {
                chunks.add(List.copyOf(currentChunk));
                currentChunk.clear();
                currentSize = 0;
            }

            currentChunk.add(block);
            currentSize += blockSize;
        }

        if (!currentChunk.isEmpty()) {
            chunks.add(List.copyOf(currentChunk));
        }
        if (chunks.isEmpty()) {
            chunks.add(List.of());
        }
        return chunks;
    }

    private String buildRolePrompt(
            ReviewTask reviewTask,
            ReviewDocument reviewDocument,
            ReviewRoleDefinition roleDefinition,
            List<ReviewBlockData> chunk,
            int chunkIndex,
            int chunkCount
    ) {
        ReviewLanguage language = inferLanguage(
                reviewDocument.getMarkdownContent(),
                reviewDocument.getTitle(),
                chunk.stream().map(ReviewBlockData::text).collect(Collectors.joining("\n"))
        );
        StringBuilder builder = new StringBuilder();
        builder.append("You are a strict document reviewer.\n");
        builder.append("Role: ").append(roleDefinition.label()).append('\n');
        builder.append("Perspective: ").append(roleDefinition.perspective()).append('\n');
        builder.append("Role focus: ").append(roleDefinition.focus()).append('\n');
        builder.append("Output language: ").append(language.promptDescription()).append('\n');
        builder.append("Task title: ").append(reviewTask.getTitle()).append('\n');
        builder.append("Document title: ").append(reviewDocument.getTitle()).append('\n');
        builder.append("Chunk: ").append(chunkIndex).append('/').append(chunkCount).append("\n\n");
        builder.append("Review rules:\n");
        builder.append("- Return only a machine-readable payload and nothing else.\n");
        builder.append("- Each annotation must target exactly one blockId from the provided blocks.\n");
        builder.append("- quoteText must be an exact substring copied from that block's plain text.\n");
        builder.append("- annotationType must be one of: question, note, opinion.\n");
        builder.append("- priority must be one of: high, medium, low.\n");
        builder.append("- title must be short and concrete.\n");
        builder.append("- content must explain the issue, risk, or suggestion in 1-3 sentences.\n");
        builder.append("- Always write title and content in the same primary language as the original document.\n");
        builder.append("- If the document is Chinese, use Simplified Chinese for all annotations.\n");
        builder.append("- Ignore parts that do not need useful feedback.\n");
        builder.append("- Do not invent blockIds, people, features, or facts.\n");
        builder.append("- Keep quoteText in the exact original wording and language from the document.\n");
        builder.append("- Maximum ").append(MAX_ANNOTATIONS_PER_CHUNK).append(" annotations for this chunk.\n\n");
        if (prefersXmlReviewPayload(reviewTask)) {
            builder.append("Response format:\n");
            builder.append("- Return only XML in the exact shape below.\n");
            builder.append("- Wrap the full response in one <annotations> root.\n");
            builder.append("- Use one <annotation> item per finding.\n");
            builder.append("- Use these child tags exactly once inside each <annotation>: <blockId>, <quoteText>, <annotationType>, <priority>, <title>, <content>.\n");
            builder.append("- Put quoteText, title, and content inside <![CDATA[...]]> if that helps preserve punctuation or line breaks.\n");
            builder.append("- If there are no useful findings, return <annotations></annotations>.\n\n");
            builder.append("Example:\n");
            builder.append("<annotations>\n");
            builder.append("  <annotation>\n");
            builder.append("    <blockId>block-1</blockId>\n");
            builder.append("    <quoteText><![CDATA[Exact original quote]]></quoteText>\n");
            builder.append("    <annotationType>question</annotationType>\n");
            builder.append("    <priority>high</priority>\n");
            builder.append("    <title><![CDATA[Short title]]></title>\n");
            builder.append("    <content><![CDATA[1-3 sentences of feedback.]]></content>\n");
            builder.append("  </annotation>\n");
            builder.append("</annotations>\n\n");
        } else {
            builder.append("Response format:\n");
            builder.append("- Return only valid JSON.\n");
            builder.append("- Output this shape: {\"annotations\":[...]}\n");
            builder.append("- If there are no useful findings, return {\"annotations\":[]}.\n\n");
        }
        builder.append("Blocks:\n");

        for (ReviewBlockData block : chunk) {
            builder.append("[").append(block.blockId()).append("] ");
            builder.append(block.blockType()).append('\n');
            builder.append(block.text() == null ? "" : block.text()).append("\n\n");
        }

        builder.append(prefersXmlReviewPayload(reviewTask) ? "Return XML now." : "Return JSON now.");
        return builder.toString();
    }

    private List<GeneratedAnnotation> readGeneratedAnnotations(
            ChatModel chatModel,
            ReviewTask reviewTask,
            ReviewDocument reviewDocument,
            ReviewRoleDefinition roleDefinition,
            String rawResponse
    ) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return List.of();
        }

        try {
            return parseGeneratedAnnotations(rawResponse);
        } catch (Exception firstFailure) {
            log.warn(
                    "Review response parse failed, attempting repair. taskId={}, documentId={}, role={}",
                    reviewTask.getId(),
                    reviewDocument.getId(),
                    roleDefinition.key(),
                    firstFailure
            );
            try {
                String repairedResponse = repairGeneratedAnnotations(chatModel, reviewTask, rawResponse);
                return parseGeneratedAnnotations(repairedResponse);
            } catch (Exception secondFailure) {
                throw new IllegalStateException("Failed to parse review response payload.", secondFailure);
            }
        }
    }

    private List<GeneratedAnnotation> parseGeneratedAnnotations(String rawResponse) throws JsonProcessingException {
        IllegalStateException xmlFailure = null;
        try {
            return parseGeneratedAnnotationsFromXml(rawResponse);
        } catch (IllegalStateException e) {
            xmlFailure = e;
        }

        IllegalStateException jsonFailure = null;
        try {
            return parseGeneratedAnnotationsFromJson(rawResponse);
        } catch (IllegalStateException e) {
            jsonFailure = e;
        }

        if (jsonFailure != null) {
            throw jsonFailure;
        }
        if (xmlFailure != null) {
            throw xmlFailure;
        }
        throw new IllegalStateException("No annotation payload found in review response.");
    }

    private List<GeneratedAnnotation> parseGeneratedAnnotationsFromJson(String rawResponse) throws JsonProcessingException {
        for (String candidate : extractJsonCandidates(rawResponse)) {
            JsonNode root = objectMapper.readTree(candidate);
            JsonNode annotationsNode = root.isArray() ? root : root.path("annotations");
            if (!annotationsNode.isArray()) {
                continue;
            }

            List<GeneratedAnnotation> annotations = new ArrayList<>();
            for (JsonNode annotationNode : annotationsNode) {
                annotations.add(new GeneratedAnnotation(
                        textValue(annotationNode, "blockId"),
                        textValue(annotationNode, "quoteText"),
                        textValue(annotationNode, "annotationType"),
                        textValue(annotationNode, "priority"),
                        textValue(annotationNode, "title"),
                        textValue(annotationNode, "content")
                ));
            }
            return annotations;
        }

        throw new IllegalStateException("No JSON payload found in review response.");
    }

    private List<GeneratedAnnotation> parseGeneratedAnnotationsFromXml(String rawResponse) {
        for (String candidate : extractXmlCandidates(rawResponse)) {
            Matcher annotationMatcher = ANNOTATION_XML_PATTERN.matcher(candidate);
            List<GeneratedAnnotation> annotations = new ArrayList<>();
            while (annotationMatcher.find()) {
                String annotationBody = annotationMatcher.group(1);
                annotations.add(new GeneratedAnnotation(
                        extractXmlField(annotationBody, "blockId"),
                        extractXmlField(annotationBody, "quoteText"),
                        extractXmlField(annotationBody, "annotationType"),
                        extractXmlField(annotationBody, "priority"),
                        extractXmlField(annotationBody, "title"),
                        extractXmlField(annotationBody, "content")
                ));
            }
            if (!annotations.isEmpty()) {
                return annotations;
            }
            if (candidate.contains("<annotations")) {
                return List.of();
            }
        }

        throw new IllegalStateException("No XML payload found in review response.");
    }

    private List<String> extractJsonCandidates(String rawResponse) {
        String cleaned = rawResponse == null ? "" : rawResponse.trim();
        if (cleaned.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(cleaned);

        for (String fencedBody : extractCodeFenceBodies(cleaned)) {
            candidates.add(fencedBody);
        }

        for (int index = 0; index < cleaned.length(); index += 1) {
            char current = cleaned.charAt(index);
            if (current != '{' && current != '[') {
                continue;
            }

            String balanced = tryExtractBalancedJson(cleaned, index);
            if (balanced == null || balanced.isBlank()) {
                continue;
            }
            candidates.add(balanced);
            if (balanced.startsWith("[")) {
                candidates.add("{\"annotations\":" + balanced + "}");
            }
        }

        return List.copyOf(candidates);
    }

    private List<String> extractXmlCandidates(String rawResponse) {
        String cleaned = rawResponse == null ? "" : rawResponse.trim();
        if (cleaned.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(cleaned);
        for (String fencedBody : extractCodeFenceBodies(cleaned)) {
            candidates.add(fencedBody);
        }

        Matcher rootMatcher = ANNOTATIONS_XML_PATTERN.matcher(cleaned);
        while (rootMatcher.find()) {
            candidates.add(rootMatcher.group());
        }
        for (String candidate : List.copyOf(candidates)) {
            Matcher candidateRootMatcher = ANNOTATIONS_XML_PATTERN.matcher(candidate);
            while (candidateRootMatcher.find()) {
                candidates.add(candidateRootMatcher.group());
            }
        }
        return List.copyOf(candidates);
    }

    private List<String> extractCodeFenceBodies(String rawResponse) {
        Matcher matcher = CODE_FENCE_PATTERN.matcher(rawResponse);
        LinkedHashSet<String> bodies = new LinkedHashSet<>();
        while (matcher.find()) {
            String body = matcher.group(1);
            if (body != null && !body.isBlank()) {
                bodies.add(body.trim());
            }
        }
        return List.copyOf(bodies);
    }

    private String tryExtractBalancedJson(String value, int startIndex) {
        char opening = value.charAt(startIndex);
        char closing = opening == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int index = startIndex; index < value.length(); index += 1) {
            char current = value.charAt(index);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == opening) {
                depth += 1;
            } else if (current == closing) {
                depth -= 1;
                if (depth == 0) {
                    return value.substring(startIndex, index + 1);
                }
            }
        }

        return null;
    }

    private String repairGeneratedAnnotations(ChatModel chatModel, ReviewTask reviewTask, String rawResponse) {
        StringBuilder builder = new StringBuilder();
        if (prefersXmlReviewPayload(reviewTask)) {
            builder.append("Convert the following review output into strict XML.\n");
            builder.append("Return only one <annotations> XML block.\n");
            builder.append("Each <annotation> must contain these tags exactly once: <blockId>, <quoteText>, <annotationType>, <priority>, <title>, <content>.\n");
            builder.append("annotationType must be one of: question, note, opinion.\n");
            builder.append("priority must be one of: high, medium, low.\n");
            builder.append("If there is no recoverable finding, return <annotations></annotations>.\n");
            builder.append("Do not add markdown fences or commentary.\n\n");
        } else {
            builder.append("Convert the following review output into strict JSON.\n");
            builder.append("Return only valid JSON in this shape: {\"annotations\":[...]}\n");
            builder.append("Each annotation item must use these fields exactly: blockId, quoteText, annotationType, priority, title, content.\n");
            builder.append("annotationType must be one of: question, note, opinion.\n");
            builder.append("priority must be one of: high, medium, low.\n");
            builder.append("If information is missing, keep the field as an empty string.\n");
            builder.append("If nothing can be recovered, return {\"annotations\":[]}.\n");
            builder.append("Do not add markdown fences or commentary.\n\n");
        }
        builder.append("Input:\n```text\n");
        builder.append(truncate(rawResponse, MAX_JSON_REPAIR_INPUT_LENGTH));
        builder.append("\n```");
        return chatModel.chat(builder.toString());
    }

    private boolean prefersXmlReviewPayload(ReviewTask reviewTask) {
        return resolveProvider(reviewTask.getProvider()) == ModelProvider.ANTHROPIC;
    }

    private String extractXmlField(String xml, String tagName) {
        Pattern fieldPattern = Pattern.compile("(?is)<" + Pattern.quote(tagName) + "\\b[^>]*>(.*?)</" + Pattern.quote(tagName) + ">");
        Matcher matcher = fieldPattern.matcher(xml);
        if (!matcher.find()) {
            return "";
        }

        String value = matcher.group(1);
        if (value == null) {
            return "";
        }

        Matcher cdataMatcher = CDATA_PATTERN.matcher(value.trim());
        String normalized = cdataMatcher.matches() ? cdataMatcher.group(1) : value;
        return unescapeXml(normalized.trim());
    }

    private String unescapeXml(String value) {
        return value
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
                .replace("&amp;", "&");
    }

    private String textValue(JsonNode node, String fieldName) {
        JsonNode valueNode = node.get(fieldName);
        return valueNode == null || valueNode.isNull() ? "" : valueNode.asText("");
    }

    private LocatedSpan locateQuote(String blockText, String quoteText) {
        String safeBlockText = blockText == null ? "" : blockText;
        String safeQuoteText = quoteText == null ? "" : quoteText.trim();

        if (safeBlockText.isBlank()) {
            return new LocatedSpan(0, 0, "");
        }
        if (safeQuoteText.isBlank()) {
            return fallbackSpan(safeBlockText);
        }

        int directIndex = safeBlockText.indexOf(safeQuoteText);
        if (directIndex >= 0) {
            return new LocatedSpan(directIndex, directIndex + safeQuoteText.length(), safeQuoteText);
        }

        int ignoreCaseIndex = safeBlockText.toLowerCase(Locale.ROOT).indexOf(safeQuoteText.toLowerCase(Locale.ROOT));
        if (ignoreCaseIndex >= 0) {
            return new LocatedSpan(
                    ignoreCaseIndex,
                    ignoreCaseIndex + safeQuoteText.length(),
                    safeBlockText.substring(ignoreCaseIndex, ignoreCaseIndex + safeQuoteText.length())
            );
        }

        NormalizedText normalizedBlock = normalizeForSearch(safeBlockText);
        NormalizedText normalizedQuote = normalizeForSearch(safeQuoteText);
        if (!normalizedBlock.value().isBlank() && !normalizedQuote.value().isBlank()) {
            int normalizedIndex = normalizedBlock.value().indexOf(normalizedQuote.value());
            if (normalizedIndex >= 0
                    && normalizedIndex + normalizedQuote.value().length() - 1 < normalizedBlock.originalPositions().size()) {
                int startOffset = normalizedBlock.originalPositions().get(normalizedIndex);
                int endOffset = normalizedBlock.originalPositions().get(
                        normalizedIndex + normalizedQuote.value().length() - 1
                ) + 1;
                return new LocatedSpan(startOffset, endOffset, safeBlockText.substring(startOffset, endOffset));
            }
        }

        return fallbackSpan(safeBlockText);
    }

    private NormalizedText normalizeForSearch(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        List<Integer> originalPositions = new ArrayList<>();
        boolean previousWhitespace = false;

        for (int index = 0; index < lower.length(); index += 1) {
            char ch = lower.charAt(index);
            if (Character.isWhitespace(ch)) {
                if (!previousWhitespace && !builder.isEmpty()) {
                    builder.append(' ');
                    originalPositions.add(index);
                }
                previousWhitespace = true;
                continue;
            }

            builder.append(ch);
            originalPositions.add(index);
            previousWhitespace = false;
        }

        return new NormalizedText(builder.toString().trim(), originalPositions);
    }

    private LocatedSpan fallbackSpan(String blockText) {
        if (blockText.isBlank()) {
            return new LocatedSpan(0, 0, "");
        }
        int endOffset = blockText.length() <= MAX_BLOCK_FALLBACK_LENGTH
                ? blockText.length()
                : MAX_BLOCK_FALLBACK_LENGTH;
        return new LocatedSpan(0, endOffset, blockText.substring(0, endOffset).trim());
    }

    private String normalizeAnnotationType(String annotationType) {
        String normalized = annotationType == null ? "" : annotationType.trim().toLowerCase(Locale.ROOT);
        for (ReviewAnnotationType value : ReviewAnnotationType.values()) {
            if (value.getValue().equals(normalized)) {
                return value.getValue();
            }
        }
        return ReviewAnnotationType.NOTE.getValue();
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toLowerCase(Locale.ROOT);
        for (ReviewPriority value : ReviewPriority.values()) {
            if (value.getValue().equals(normalized)) {
                return value.getValue();
            }
        }
        return ReviewPriority.MEDIUM.getValue();
    }

    private String buildDocumentSummary(ReviewDocument reviewDocument, List<ReviewRoleRun> roleRuns, List<ReviewAnnotation> annotations) {
        ReviewLanguage language = inferLanguage(reviewDocument.getMarkdownContent(), reviewDocument.getTitle());
        ReviewCopy copy = reviewCopy(language);
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(reviewDocument.getTitle()).append("\n\n");
        builder.append("- ").append(copy.statusLabel()).append(": ").append(localizeStatus(reviewDocument.getStatus(), language)).append('\n');
        builder.append("- ").append(copy.totalAnnotationsLabel()).append(": ").append(annotations.size()).append('\n');
        builder.append("- ").append(copy.highPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.HIGH.getValue())).append('\n');
        builder.append("- ").append(copy.mediumPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.MEDIUM.getValue())).append('\n');
        builder.append("- ").append(copy.lowPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.LOW.getValue())).append("\n\n");
        builder.append("## ").append(copy.roleCoverageHeading()).append('\n');
        for (ReviewRoleDefinition roleDefinition : REVIEW_ROLES) {
            ReviewRoleRun roleRun = roleRuns.stream()
                    .filter(candidate -> roleDefinition.key().equals(candidate.getRoleKey()))
                    .findFirst()
                    .orElse(null);
            String roleStatus = roleRun == null ? ReviewRoleStatus.PENDING.getValue() : roleRun.getStatus();
            int annotationCount = roleRun == null || roleRun.getAnnotationCount() == null ? 0 : roleRun.getAnnotationCount();
            builder.append("- ")
                    .append(localizeRoleLabel(roleDefinition.key(), language))
                    .append(": ")
                    .append(localizeStatus(roleStatus, language))
                    .append(" (")
                    .append(annotationCount)
                    .append(")\n");
        }
        builder.append('\n');
        if (annotations.isEmpty()) {
            builder.append("## ").append(copy.keyFindingsHeading()).append('\n');
            builder.append("- ").append(copy.noActionableFindings()).append('\n');
            return builder.toString();
        }
        builder.append("## ").append(copy.keyFindingsHeading()).append('\n');
        annotations.stream().sorted(annotationComparator()).limit(12)
                .forEach(annotation -> builder.append("- [").append(localizePriority(annotation.getPriority(), language)).append("] ")
                        .append(localizeRoleLabel(annotation.getRoleKey(), language)).append(": ")
                        .append(annotation.getTitle()).append(" - ")
                        .append(annotation.getContent()).append('\n'));
        return builder.toString();
    }

    private String buildOverallReport(ReviewTask reviewTask, List<ReviewDocument> documents, List<ReviewAnnotation> annotations) {
        ReviewLanguage language = inferTaskLanguage(reviewTask, documents, annotations);
        ReviewCopy copy = reviewCopy(language);
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(reviewTask.getTitle()).append("\n\n");
        builder.append("- ").append(copy.statusLabel()).append(": ").append(localizeStatus(reviewTask.getStatus(), language)).append('\n');
        builder.append("- ").append(copy.documentsLabel()).append(": ").append(documents.size()).append('\n');
        builder.append("- ").append(copy.totalAnnotationsLabel()).append(": ").append(annotations.size()).append('\n');
        builder.append("- ").append(copy.highPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.HIGH.getValue())).append("\n\n");
        builder.append("## ").append(copy.documentsHeading()).append('\n');
        for (ReviewDocument document : documents) {
            long documentAnnotationCount = annotations.stream().filter(annotation -> Objects.equals(annotation.getDocumentId(), document.getId())).count();
            builder.append("- ")
                    .append(document.getTitle())
                    .append(": ")
                    .append(localizeStatus(document.getStatus(), language))
                    .append(" (")
                    .append(documentAnnotationCount)
                    .append(' ')
                    .append(copy.findingUnit())
                    .append(")\n");
        }
        builder.append("\n## ").append(copy.highestPriorityFindingsHeading()).append('\n');
        annotations.stream().sorted(annotationComparator()).limit(18)
                .forEach(annotation -> builder.append("- [").append(localizePriority(annotation.getPriority(), language)).append("] ")
                        .append(resolveDocumentTitle(documents, annotation.getDocumentId(), language)).append(" / ")
                        .append(localizeRoleLabel(annotation.getRoleKey(), language)).append(": ")
                        .append(annotation.getTitle()).append(" - ")
                        .append(annotation.getContent()).append('\n'));
        return builder.toString();
    }

    private String resolveDocumentTitle(List<ReviewDocument> documents, Long documentId, ReviewLanguage language) {
        return documents.stream()
                .filter(document -> Objects.equals(document.getId(), documentId))
                .map(ReviewDocument::getTitle)
                .findFirst()
                .orElse(reviewCopy(language).unknownDocumentLabel());
    }

    private Comparator<ReviewAnnotation> annotationComparator() {
        return Comparator
                .comparingInt((ReviewAnnotation annotation) -> priorityRank(annotation.getPriority()))
                .thenComparing(ReviewAnnotation::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private int priorityRank(String priority) {
        return switch (priority) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private long countPriority(List<ReviewAnnotation> annotations, String priority) {
        return annotations.stream().filter(annotation -> priority.equals(annotation.getPriority())).count();
    }

    private List<ReviewBlockData> readBlocks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, BLOCK_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize review blocks.", e);
        }
    }

    private List<String> readWarnings(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize review warnings.", e);
        }
    }

    private ModelProvider resolveProvider(String providerValue) {
        try {
            return ModelProvider.valueOf(providerValue.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BusinessException(ResultCode.REVIEW_MODEL_UNSUPPORTED);
        }
    }

    private ReviewRoleDefinition roleDefinition(String roleKey) {
        return REVIEW_ROLES.stream()
                .filter(role -> role.key().equals(roleKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown review role: " + roleKey));
    }

    private String generateOverallReportMarkdown(
            ReviewTask reviewTask,
            List<ReviewDocument> documents,
            Map<Long, List<ReviewRoleRun>> roleRunsByDocument,
            List<ReviewAnnotation> annotations
    ) {
        ReviewLanguage language = inferTaskLanguage(reviewTask, documents, annotations);
        ChatModel chatModel = createOverallReportModel(reviewTask);
        String rawResponse = chatModel.chat(buildOverallReportPrompt(reviewTask, documents, roleRunsByDocument, annotations, language));
        String markdown = sanitizeGeneratedMarkdownReport(rawResponse);
        if (markdown.isBlank()) {
            throw new IllegalStateException("Generated overall report is empty.");
        }
        return markdown;
    }

    private String buildOverallReportPrompt(
            ReviewTask reviewTask,
            List<ReviewDocument> documents,
            Map<Long, List<ReviewRoleRun>> roleRunsByDocument,
            List<ReviewAnnotation> annotations,
            ReviewLanguage language
    ) {
        ReviewCopy copy = reviewCopy(language);
        StringBuilder builder = new StringBuilder();
        builder.append("You are writing the final multi-role review report for a game design documentation workflow.\n");
        builder.append("Output language: ").append(language.promptDescription()).append('\n');
        builder.append("Return only Markdown. Do not wrap the answer in code fences.\n");
        builder.append("Do not output XML, JSON, or implementation notes.\n");
        builder.append("Write like an experienced reviewer producing a polished report for humans, not a raw machine summary.\n");
        builder.append("Base every conclusion strictly on the provided source materials and review annotations.\n");
        builder.append("If a detail cannot be supported by the source, call it out as uncertainty or risk instead of inventing facts.\n");
        builder.append("Synthesize repeated feedback from different roles into clear conclusions instead of listing everything mechanically.\n");
        builder.append("Recommended structure:\n");
        builder.append("1. One H1 title.\n");
        builder.append("2. Executive summary.\n");
        builder.append("3. Per-document findings.\n");
        builder.append("4. Cross-document risks or conflicts.\n");
        builder.append("5. Prioritized next actions.\n");
        builder.append("6. Optional appendix for role-by-role notes if useful.\n\n");

        builder.append("Task context:\n");
        builder.append("- Task title: ").append(reviewTask.getTitle()).append('\n');
        builder.append("- Model: ").append(reviewTask.getProvider()).append(" / ").append(reviewTask.getModelName()).append('\n');
        builder.append("- ").append(copy.documentsLabel()).append(": ").append(documents.size()).append('\n');
        builder.append("- ").append(copy.totalAnnotationsLabel()).append(": ").append(annotations.size()).append('\n');
        builder.append("- ").append(copy.highPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.HIGH.getValue())).append('\n');
        builder.append("- ").append(copy.mediumPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.MEDIUM.getValue())).append('\n');
        builder.append("- ").append(copy.lowPriorityLabel()).append(": ").append(countPriority(annotations, ReviewPriority.LOW.getValue())).append("\n\n");

        Map<Long, List<ReviewAnnotation>> annotationsByDocument = annotations.stream()
                .collect(Collectors.groupingBy(ReviewAnnotation::getDocumentId, LinkedHashMap::new, Collectors.toList()));

        for (int index = 0; index < documents.size(); index += 1) {
            ReviewDocument document = documents.get(index);
            List<ReviewRoleRun> roleRuns = roleRunsByDocument.getOrDefault(document.getId(), List.of());
            List<ReviewAnnotation> documentAnnotations = annotationsByDocument.getOrDefault(document.getId(), List.of()).stream()
                    .sorted(annotationComparator())
                    .limit(MAX_REPORT_ANNOTATIONS_PER_DOCUMENT)
                    .toList();

            builder.append("## Source document ").append(index + 1).append('\n');
            builder.append("Title: ").append(document.getTitle()).append('\n');
            builder.append("Status: ").append(localizeStatus(document.getStatus(), language)).append('\n');
            if (document.getOriginalFilename() != null && !document.getOriginalFilename().isBlank()) {
                builder.append("Original filename: ").append(document.getOriginalFilename()).append('\n');
            }

            List<String> warnings = readWarnings(document.getWarningsJson());
            if (!warnings.isEmpty()) {
                builder.append("Warnings:\n");
                warnings.forEach(warning -> builder.append("- ").append(warning).append('\n'));
            }

            builder.append("Role coverage:\n");
            for (ReviewRoleDefinition roleDefinition : REVIEW_ROLES) {
                ReviewRoleRun roleRun = roleRuns.stream()
                        .filter(candidate -> roleDefinition.key().equals(candidate.getRoleKey()))
                        .findFirst()
                        .orElse(null);
                String roleStatus = roleRun == null ? ReviewRoleStatus.PENDING.getValue() : roleRun.getStatus();
                int annotationCount = roleRun == null || roleRun.getAnnotationCount() == null ? 0 : roleRun.getAnnotationCount();
                builder.append("- ")
                        .append(localizeRoleLabel(roleDefinition.key(), language))
                        .append(": ")
                        .append(localizeStatus(roleStatus, language))
                        .append(" (")
                        .append(annotationCount)
                        .append(")\n");
            }

            builder.append("Source markdown excerpt:\n```md\n");
            builder.append(limitReportSource(document.getMarkdownContent()));
            builder.append("\n```\n");

            builder.append("Annotations to synthesize:\n");
            if (documentAnnotations.isEmpty()) {
                builder.append("- None.\n");
            } else {
                for (int annotationIndex = 0; annotationIndex < documentAnnotations.size(); annotationIndex += 1) {
                    ReviewAnnotation annotation = documentAnnotations.get(annotationIndex);
                    builder.append(annotationIndex + 1)
                            .append(". [")
                            .append(localizePriority(annotation.getPriority(), language))
                            .append("] ")
                            .append(localizeRoleLabel(annotation.getRoleKey(), language))
                            .append(" / ")
                            .append(localizeAnnotationType(annotation.getAnnotationType(), language))
                            .append('\n');
                    builder.append("   Title: ").append(annotation.getTitle()).append('\n');
                    builder.append("   Quote: ").append(annotation.getQuoteText()).append('\n');
                    builder.append("   Comment: ").append(annotation.getContent()).append('\n');
                }
            }
            builder.append('\n');
        }

        builder.append("Write the final report now.");
        return builder.toString();
    }

    private String limitReportSource(String markdown) {
        String safeMarkdown = markdown == null ? "" : markdown.trim();
        if (safeMarkdown.length() <= MAX_REPORT_SOURCE_LENGTH_PER_DOCUMENT) {
            return safeMarkdown;
        }
        return safeMarkdown.substring(0, MAX_REPORT_SOURCE_LENGTH_PER_DOCUMENT)
                + "\n\n[Source excerpt truncated for report generation]";
    }

    private String sanitizeGeneratedMarkdownReport(String rawResponse) {
        String cleaned = rawResponse == null ? "" : rawResponse.trim();
        if (cleaned.isBlank()) {
            return "";
        }

        List<String> fencedBodies = extractCodeFenceBodies(cleaned);
        if (!fencedBodies.isEmpty() && cleaned.startsWith("```")) {
            cleaned = fencedBodies.get(0).trim();
        }

        if (cleaned.regionMatches(true, 0, "<md-text>", 0, "<md-text>".length())) {
            cleaned = cleaned.substring("<md-text>".length()).trim();
        }
        if (cleaned.toLowerCase(Locale.ROOT).endsWith("</md-text>")) {
            cleaned = cleaned.substring(0, cleaned.length() - "</md-text>".length()).trim();
        }
        return cleaned.trim();
    }

    private String sanitizeInline(String value) {
        return value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String sanitizeBody(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private ReviewLanguage inferTaskLanguage(ReviewTask reviewTask, List<ReviewDocument> documents, List<ReviewAnnotation> annotations) {
        StringBuilder builder = new StringBuilder();
        builder.append(reviewTask.getTitle()).append('\n');
        documents.forEach(document -> {
            builder.append(document.getTitle()).append('\n');
            if (document.getMarkdownContent() != null) {
                builder.append(truncate(document.getMarkdownContent(), 1200)).append('\n');
            }
        });
        annotations.stream().limit(20).forEach(annotation ->
                builder.append(annotation.getTitle()).append('\n').append(annotation.getContent()).append('\n')
        );
        return inferLanguage(builder.toString());
    }

    private ReviewLanguage inferLanguage(String... texts) {
        int hanCount = 0;
        int hiraganaKatakanaCount = 0;
        int hangulCount = 0;
        int latinCount = 0;

        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            for (int index = 0; index < text.length(); index += 1) {
                char ch = text.charAt(index);
                Character.UnicodeScript script = Character.UnicodeScript.of(ch);
                if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) {
                    hiraganaKatakanaCount += 1;
                } else if (script == Character.UnicodeScript.HANGUL) {
                    hangulCount += 1;
                } else if (script == Character.UnicodeScript.HAN) {
                    hanCount += 1;
                } else if ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z')) {
                    latinCount += 1;
                }
            }
        }

        if (hiraganaKatakanaCount >= 2) {
            return ReviewLanguage.JAPANESE;
        }
        if (hangulCount >= 2) {
            return ReviewLanguage.KOREAN;
        }
        if (hanCount > 0 && hanCount >= latinCount) {
            return ReviewLanguage.CHINESE;
        }
        if (latinCount > 0) {
            return ReviewLanguage.ENGLISH;
        }
        return ReviewLanguage.ENGLISH;
    }

    private ReviewCopy reviewCopy(ReviewLanguage language) {
        return switch (language) {
            case CHINESE -> new ReviewCopy(
                    "状态",
                    "批注总数",
                    "高优先级",
                    "中优先级",
                    "低优先级",
                    "角色覆盖",
                    "关键发现",
                    "当前没有产出可执行的批注。",
                    "文档数量",
                    "文档列表",
                    "最高优先级问题",
                    "条问题",
                    "未知文档"
            );
            case JAPANESE -> new ReviewCopy(
                    "状態",
                    "注釈数",
                    "高優先度",
                    "中優先度",
                    "低優先度",
                    "ロール別カバレッジ",
                    "主要な指摘",
                    "対応が必要な指摘はありませんでした。",
                    "ドキュメント数",
                    "ドキュメント",
                    "最優先の指摘",
                    "件",
                    "不明なドキュメント"
            );
            case KOREAN -> new ReviewCopy(
                    "상태",
                    "주석 수",
                    "높은 우선순위",
                    "중간 우선순위",
                    "낮은 우선순위",
                    "역할별 커버리지",
                    "핵심 발견사항",
                    "실행 가능한 지적이 없습니다.",
                    "문서 수",
                    "문서 목록",
                    "최우선 이슈",
                    "건",
                    "알 수 없는 문서"
            );
            case ENGLISH -> new ReviewCopy(
                    "Status",
                    "Total annotations",
                    "High priority",
                    "Medium priority",
                    "Low priority",
                    "Role coverage",
                    "Key findings",
                    "No actionable findings were produced.",
                    "Documents",
                    "Documents",
                    "Highest priority findings",
                    "findings",
                    "Unknown document"
            );
        };
    }

    private String localizeStatus(String status, ReviewLanguage language) {
        return switch (language) {
            case CHINESE -> switch (status) {
                case "created" -> "已创建";
                case "processing" -> "处理中";
                case "completed" -> "已完成";
                case "partial" -> "部分完成";
                case "failed" -> "失败";
                case "pending" -> "等待中";
                case "running" -> "执行中";
                default -> status;
            };
            case JAPANESE -> switch (status) {
                case "created" -> "作成済み";
                case "processing" -> "処理中";
                case "completed" -> "完了";
                case "partial" -> "一部完了";
                case "failed" -> "失敗";
                case "pending" -> "待機中";
                case "running" -> "実行中";
                default -> status;
            };
            case KOREAN -> switch (status) {
                case "created" -> "생성됨";
                case "processing" -> "처리 중";
                case "completed" -> "완료";
                case "partial" -> "부분 완료";
                case "failed" -> "실패";
                case "pending" -> "대기 중";
                case "running" -> "실행 중";
                default -> status;
            };
            case ENGLISH -> status;
        };
    }

    private String localizePriority(String priority, ReviewLanguage language) {
        return switch (language) {
            case CHINESE -> switch (priority) {
                case "high" -> "高";
                case "medium" -> "中";
                case "low" -> "低";
                default -> priority;
            };
            case JAPANESE -> switch (priority) {
                case "high" -> "高";
                case "medium" -> "中";
                case "low" -> "低";
                default -> priority;
            };
            case KOREAN -> switch (priority) {
                case "high" -> "높음";
                case "medium" -> "보통";
                case "low" -> "낮음";
                default -> priority;
            };
            case ENGLISH -> priority;
        };
    }

    private String localizeAnnotationType(String annotationType, ReviewLanguage language) {
        return switch (language) {
            case CHINESE -> switch (annotationType) {
                case "question" -> "问题";
                case "note" -> "说明";
                case "opinion" -> "意见";
                default -> annotationType;
            };
            case JAPANESE -> switch (annotationType) {
                case "question" -> "質問";
                case "note" -> "補足";
                case "opinion" -> "意見";
                default -> annotationType;
            };
            case KOREAN -> switch (annotationType) {
                case "question" -> "질문";
                case "note" -> "설명";
                case "opinion" -> "의견";
                default -> annotationType;
            };
            case ENGLISH -> annotationType;
        };
    }

    private String localizeRoleLabel(String roleKey, ReviewLanguage language) {
        return switch (language) {
            case CHINESE -> switch (roleKey) {
                case "planner" -> "策划";
                case "engineer" -> "程序";
                case "qa" -> "QA";
                case "art" -> "美术";
                case "ui" -> "UI";
                case "pm" -> "PM";
                case "player" -> "玩家";
                default -> roleKey;
            };
            case JAPANESE -> switch (roleKey) {
                case "planner" -> "企画";
                case "engineer" -> "エンジニア";
                case "qa" -> "QA";
                case "art" -> "アート";
                case "ui" -> "UI";
                case "pm" -> "PM";
                case "player" -> "プレイヤー";
                default -> roleKey;
            };
            case KOREAN -> switch (roleKey) {
                case "planner" -> "기획";
                case "engineer" -> "개발";
                case "qa" -> "QA";
                case "art" -> "아트";
                case "ui" -> "UI";
                case "pm" -> "PM";
                case "player" -> "플레이어";
                default -> roleKey;
            };
            case ENGLISH -> switch (roleKey) {
                case "planner" -> "Planner";
                case "engineer" -> "Engineer";
                case "qa" -> "QA";
                case "art" -> "Art";
                case "ui" -> "UI";
                case "pm" -> "PM";
                case "player" -> "Player";
                default -> roleKey;
            };
        };
    }

    private record NormalizedReviewInput(
            ReviewSourceType sourceType,
            String title,
            String originalFilename,
            String markdown,
            List<ReviewBlockData> blocks,
            List<String> warnings
    ) {
    }

    private record ReviewRoleDefinition(
            String key,
            String label,
            String color,
            String perspective,
            String focus
    ) {
    }

    private record GeneratedAnnotation(
            String blockId,
            String quoteText,
            String annotationType,
            String priority,
            String title,
            String content
    ) {
    }

    private record ReviewAnnotationDraft(
            String blockId,
            int startOffset,
            int endOffset,
            String quoteText,
            String annotationType,
            String priority,
            String title,
            String content
    ) {
    }

    private record LocatedSpan(
            int startOffset,
            int endOffset,
            String quoteText
    ) {
    }

    private record NormalizedText(
            String value,
            List<Integer> originalPositions
    ) {
    }

    private enum ReviewLanguage {
        CHINESE("Simplified Chinese"),
        JAPANESE("Japanese"),
        KOREAN("Korean"),
        ENGLISH("English");

        private final String promptDescription;

        ReviewLanguage(String promptDescription) {
            this.promptDescription = promptDescription;
        }

        public String promptDescription() {
            return promptDescription;
        }
    }

    private record ReviewCopy(
            String statusLabel,
            String totalAnnotationsLabel,
            String highPriorityLabel,
            String mediumPriorityLabel,
            String lowPriorityLabel,
            String roleCoverageHeading,
            String keyFindingsHeading,
            String noActionableFindings,
            String documentsLabel,
            String documentsHeading,
            String highestPriorityFindingsHeading,
            String findingUnit,
            String unknownDocumentLabel
    ) {
    }
}
