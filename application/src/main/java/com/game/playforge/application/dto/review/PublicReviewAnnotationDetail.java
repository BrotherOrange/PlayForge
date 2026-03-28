package com.game.playforge.application.dto.review;

public record PublicReviewAnnotationDetail(
        String id,
        String roleRunId,
        String roleKey,
        String roleName,
        String roleColor,
        String annotationType,
        String priority,
        String blockId,
        int startOffset,
        int endOffset,
        String quoteText,
        String title,
        String content,
        String createdAt
) {
}
