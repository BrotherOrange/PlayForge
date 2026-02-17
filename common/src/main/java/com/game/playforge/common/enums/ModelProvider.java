package com.game.playforge.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI模型供应商枚举
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Getter
@RequiredArgsConstructor
public enum ModelProvider {

    /**
     * OpenAI
     */
    OPENAI("openai"),

    /**
     * Anthropic Claude
     */
    ANTHROPIC("anthropic"),

    /**
     * Google Gemini
     */
    GEMINI("gemini");

    /**
     * 供应商标识值
     */
    private final String value;
}
