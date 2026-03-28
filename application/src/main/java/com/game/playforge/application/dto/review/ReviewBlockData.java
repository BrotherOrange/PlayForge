package com.game.playforge.application.dto.review;

public record ReviewBlockData(
        String blockId,
        String blockType,
        String markdown,
        String text
) {
}
