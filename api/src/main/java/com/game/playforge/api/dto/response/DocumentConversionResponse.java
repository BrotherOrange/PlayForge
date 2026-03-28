package com.game.playforge.api.dto.response;

import com.game.playforge.infrastructure.external.document.DocumentConversionResult;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * API response for document-to-Markdown conversion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentConversionResponse {

    private String filename;
    private String sourceFormat;
    private String markdown;
    private String engine;
    private List<String> warnings;

    public static DocumentConversionResponse from(DocumentConversionResult result) {
        return new DocumentConversionResponse(
                result.filename(),
                result.sourceFormat(),
                result.markdown(),
                result.engine(),
                result.warnings()
        );
    }
}
