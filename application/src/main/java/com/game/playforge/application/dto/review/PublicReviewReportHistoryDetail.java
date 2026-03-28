package com.game.playforge.application.dto.review;

public record PublicReviewReportHistoryDetail(
        String id,
        String reportScope,
        String title,
        String provider,
        String modelName,
        String reportMarkdown,
        String createdAt
) {
}
