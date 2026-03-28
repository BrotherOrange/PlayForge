package com.game.playforge.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewTaskStatus {
    CREATED("created"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    PARTIAL("partial"),
    FAILED("failed");

    private final String value;
}
