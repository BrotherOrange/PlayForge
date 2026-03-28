package com.game.playforge.application.dto.review;

import java.util.List;

public record PublicReviewDocumentDetail(
        String id,
        String sourceType,
        int sortOrder,
        String title,
        String originalFilename,
        String status,
        List<String> warnings,
        String summaryMarkdown,
        String errorMessage,
        List<ReviewBlockData> blocks,
        List<PublicReviewRoleRunDetail> roleRuns,
        List<PublicReviewAnnotationDetail> annotations
) {
}
