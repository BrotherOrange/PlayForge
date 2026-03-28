package com.game.playforge.application.dto.review;

public record PublicReviewCreateResult(
        String publicId,
        String status,
        String title
) {
}
