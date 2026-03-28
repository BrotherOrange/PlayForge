package com.game.playforge.application.dto.review;

public record PublicReviewTaskSummary(
        String publicId,
        String title,
        String provider,
        String modelName,
        String status,
        int documentCount,
        boolean hasOverallReport,
        String errorMessage,
        String createdAt,
        String updatedAt
) {
}
