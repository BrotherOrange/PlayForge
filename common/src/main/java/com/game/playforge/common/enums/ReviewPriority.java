package com.game.playforge.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewPriority {
    HIGH("high"),
    MEDIUM("medium"),
    LOW("low");

    private final String value;
}
