package com.game.playforge.infrastructure.external.document;

import java.util.List;

/**
 * Result returned by the document normalization engine.
 */
public record DocumentConversionResult(
        String filename,
        String sourceFormat,
        String markdown,
        String engine,
        List<String> warnings
) {
}
