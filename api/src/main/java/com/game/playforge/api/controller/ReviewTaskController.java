package com.game.playforge.api.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.playforge.api.dto.response.OssPolicyResponse;
import com.game.playforge.application.dto.review.PublicReviewTaskSummary;
import com.game.playforge.application.dto.review.PublicReviewCreateCommand;
import com.game.playforge.application.dto.review.PublicReviewCreateResult;
import com.game.playforge.application.dto.review.ReviewInputItem;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.application.service.PublicReviewService;
import com.game.playforge.common.enums.ReviewSourceType;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.infrastructure.external.oss.OssService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Authenticated review task creation endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewTaskController {

    private static final long REVIEW_UPLOAD_MAX_BYTES = 50L * 1024 * 1024;
    private static final DateTimeFormatter DIRECTORY_DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final TypeReference<List<TextEntryRequest>> TEXT_ENTRY_TYPE = new TypeReference<>() {};

    private final PublicReviewService publicReviewService;
    private final OssService ossService;
    private final ObjectMapper objectMapper;

    @GetMapping("/upload-policy")
    public ApiResult<OssPolicyResponse> getUploadPolicy() {
        String directory = "reviews/uploads/" + DIRECTORY_DATE_FORMATTER.format(LocalDate.now());
        OssService.PolicyResult result = ossService.generatePostPolicy(directory, REVIEW_UPLOAD_MAX_BYTES);
        log.info("Generated authenticated review upload policy, directory={}, maxBytes={}", directory, REVIEW_UPLOAD_MAX_BYTES);
        return ApiResult.success(new OssPolicyResponse(
                result.host(),
                result.policy(),
                result.signature(),
                result.accessKeyId(),
                result.key(),
                result.expire()
        ));
    }

    @GetMapping("/history")
    public ApiResult<List<PublicReviewTaskSummary>> listReviewHistory(
            HttpServletRequest request,
            @RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        int normalizedLimit = Math.min(Math.max(limit == null ? 20 : limit, 1), 50);
        return ApiResult.success(publicReviewService.listReviewTasks(userId, normalizedLimit));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<PublicReviewCreateResult> createReview(
            HttpServletRequest servletRequest,
            @RequestBody CreateReviewRequest request) {
        Long userId = (Long) servletRequest.getAttribute(AuthConstants.CURRENT_USER_ID);
        List<ReviewInputItem> items = new ArrayList<>();

        if (request.fileItems() != null) {
            for (FileEntryRequest fileItem : request.fileItems()) {
                if (fileItem == null) {
                    continue;
                }
                items.add(new ReviewInputItem(
                        ReviewSourceType.FILE,
                        fileItem.title(),
                        fileItem.originalFilename(),
                        fileItem.contentType(),
                        fileItem.objectKey(),
                        null,
                        null
                ));
            }
        }

        if (request.textItems() != null) {
            for (TextEntryRequest textItem : request.textItems()) {
                if (textItem == null) {
                    continue;
                }
                items.add(new ReviewInputItem(
                        ReviewSourceType.TEXT,
                        textItem.title(),
                        null,
                        "text/plain",
                        null,
                        null,
                        textItem.content()
                ));
            }
        }

        log.info(
                "Create authenticated review task, userId={}, provider={}, model={}, fileCount={}, textCount={}, itemCount={}",
                userId,
                request.provider(),
                request.modelName(),
                request.fileItems() == null ? 0 : request.fileItems().size(),
                request.textItems() == null ? 0 : request.textItems().size(),
                items.size()
        );
        return ApiResult.success(publicReviewService.createReviewTask(
                new PublicReviewCreateCommand(userId, request.taskTitle(), request.provider(), request.modelName(), items)
        ));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<PublicReviewCreateResult> createReviewMultipart(
            HttpServletRequest servletRequest,
            @RequestParam(value = "taskTitle", required = false) String taskTitle,
            @RequestParam("provider") String provider,
            @RequestParam("modelName") String modelName,
            @RequestParam(value = "textItems", required = false) String textItemsJson,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {
        Long userId = (Long) servletRequest.getAttribute(AuthConstants.CURRENT_USER_ID);
        List<ReviewInputItem> items = new ArrayList<>();

        if (files != null) {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                try {
                    items.add(new ReviewInputItem(
                            ReviewSourceType.FILE,
                            null,
                            file.getOriginalFilename(),
                            file.getContentType(),
                            null,
                            file.getBytes(),
                            null
                    ));
                } catch (IOException e) {
                    throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Failed to read uploaded file.");
                }
            }
        }

        for (TextEntryRequest textItem : parseTextItems(textItemsJson)) {
            items.add(new ReviewInputItem(
                    ReviewSourceType.TEXT,
                    textItem.title(),
                    null,
                    "text/plain",
                    null,
                    null,
                    textItem.content()
            ));
        }

        log.info("Create authenticated review task from multipart, userId={}, provider={}, model={}, itemCount={}", userId, provider, modelName, items.size());
        return ApiResult.success(publicReviewService.createReviewTask(
                new PublicReviewCreateCommand(userId, taskTitle, provider, modelName, items)
        ));
    }

    private List<TextEntryRequest> parseTextItems(String textItemsJson) {
        if (textItemsJson == null || textItemsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(textItemsJson, TEXT_ENTRY_TYPE);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Invalid textItems payload.");
        }
    }

    private record CreateReviewRequest(
            String taskTitle,
            String provider,
            String modelName,
            List<FileEntryRequest> fileItems,
            List<TextEntryRequest> textItems
    ) {
    }

    private record FileEntryRequest(
            String title,
            String originalFilename,
            String contentType,
            String objectKey
    ) {
    }

    private record TextEntryRequest(
            String title,
            String content
    ) {
    }
}
