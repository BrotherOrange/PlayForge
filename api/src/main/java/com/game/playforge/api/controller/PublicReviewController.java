package com.game.playforge.api.controller;

import com.game.playforge.application.dto.review.PublicReviewTaskDetail;
import com.game.playforge.application.service.PublicReviewService;
import com.game.playforge.api.service.PublicReviewSseService;
import com.game.playforge.common.result.ApiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Public review preview endpoints.
 */
@RestController
@RequestMapping("/api/public/reviews")
@RequiredArgsConstructor
public class PublicReviewController {

    private final PublicReviewService publicReviewService;
    private final PublicReviewSseService publicReviewSseService;

    @GetMapping("/{publicId}")
    public ApiResult<PublicReviewTaskDetail> getReview(@PathVariable String publicId) {
        return ApiResult.success(publicReviewService.getReviewTask(publicId));
    }

    @GetMapping(path = "/{publicId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamReview(@PathVariable String publicId) {
        return publicReviewSseService.subscribe(publicId, publicReviewService.getReviewTask(publicId));
    }

    @PostMapping("/{publicId}/overall-report")
    public ApiResult<PublicReviewTaskDetail> generateOverallReport(@PathVariable String publicId) {
        return ApiResult.success(publicReviewService.generateOverallReport(publicId));
    }
}
