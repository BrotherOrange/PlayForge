package com.game.playforge.application.dto.review;

import com.game.playforge.common.enums.ReviewSourceType;

import java.util.Optional;

public record ReviewInputItem(
        ReviewSourceType sourceType,
        String title,
        String originalFilename,
        String contentType,
        String objectKey,
        byte[] fileContent,
        String textContent
) {

    public Optional<byte[]> fileContentOptional() {
        return Optional.ofNullable(fileContent);
    }

    public Optional<String> objectKeyOptional() {
        return Optional.ofNullable(objectKey);
    }
}
