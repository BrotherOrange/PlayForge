package com.game.playforge.application.dto.review;

import java.util.List;

public record PublicReviewTaskDetail(
        String publicId,
        String title,
        String provider,
        String modelName,
        String status,
        int documentCount,
        String overallReportMarkdown,
        String errorMessage,
        String createdAt,
        List<PublicReviewDocumentDetail> documents,
        List<PublicReviewReportHistoryDetail> reportHistory
) {
}
