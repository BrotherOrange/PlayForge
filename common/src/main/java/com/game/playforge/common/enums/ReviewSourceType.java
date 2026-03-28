package com.game.playforge.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewSourceType {
    FILE("file"),
    TEXT("text");

    private final String value;
}
