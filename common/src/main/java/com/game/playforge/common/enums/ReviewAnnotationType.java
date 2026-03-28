package com.game.playforge.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ReviewAnnotationType {
    QUESTION("question"),
    NOTE("note"),
    OPINION("opinion");

    private final String value;
}
