package com.game.playforge.application.dto.review;

public record PublicReviewRoleRunDetail(
        String id,
        String roleKey,
        String roleName,
        String roleColor,
        String status,
        int annotationCount,
        String errorMessage,
        String completedAt
) {
}
