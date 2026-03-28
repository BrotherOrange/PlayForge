package com.game.playforge.infrastructure.external.document;

/**
 * Converts uploaded documents into Markdown text.
 */
public interface DocumentConversionService {

    DocumentConversionResult convert(String filename, String contentType, byte[] content);

    String normalizeText(String title, String content);
}
