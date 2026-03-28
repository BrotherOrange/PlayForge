package com.game.playforge.application.dto.review;

import java.util.List;

public record PublicReviewCreateCommand(
        Long creatorUserId,
        String taskTitle,
        String provider,
        String modelName,
        List<ReviewInputItem> items
) {
}
