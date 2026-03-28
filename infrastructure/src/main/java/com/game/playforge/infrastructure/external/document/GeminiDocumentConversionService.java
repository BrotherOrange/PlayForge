package com.game.playforge.infrastructure.external.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts uploaded documents into Markdown through Gemini text normalization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiDocumentConversionService implements DocumentConversionService {

    private static final String ENGINE = "gemini-markdown-normalizer";
    private static final String DIRECT_ENGINE = "direct-text-pass-through";
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("doc", "docx", "pdf", "xls", "xlsx", "txt", "md", "markdown");
    private static final Pattern MD_TEXT_PATTERN =
            Pattern.compile("(?is)<md-text>\\s*(.*?)\\s*</md-text>");
    private static final Pattern GOOGLE_ERROR_MESSAGE_PATTERN =
            Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");
    private static final String FILE_SYSTEM_PROMPT = """
            You are a document normalization engine.
            You receive text that was extracted from the user's original file.
            Reconstruct it into a clean Markdown document while staying strictly faithful to the source.
            Rules:
            - Preserve the original language of the source document. Do not translate it into another language.
            - Keep the meaning, facts, numbers, requirements, names, and terminology exactly aligned with the source.
            - Improve structure and readability only through Markdown formatting such as headings, lists, tables, and blockquotes.
            - For spreadsheets, reconstruct meaningful Markdown sections and tables from the extracted rows and sheet names.
            - If the extraction notes mention omitted images or unreadable regions, reflect that uncertainty briefly instead of guessing.
            - Do not invent missing content.
            - Return only one wrapper block and nothing outside it.
            """;

    private final RestClient.Builder restClientBuilder;
    private final DocumentConverterProperties properties;
    private final DocumentSourceExtractor documentSourceExtractor;
    private final Environment environment;

    @Override
    public DocumentConversionResult convert(String filename, String contentType, byte[] content) {
        if (filename == null || filename.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Uploaded file name is required.");
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Uploaded file cannot be empty.");
        }

        String extension = extractExtension(filename);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(
                    ResultCode.DOCUMENT_FORMAT_UNSUPPORTED,
                    "Supported formats are pdf, doc, docx, xls, xlsx, txt, md, and markdown."
            );
        }

        if (isDirectTextExtension(extension)) {
            return buildDirectFileResult(filename, extension, content);
        }

        try {
            String apiKey = requireApiKey();
            ConversionPayload payload = buildConversionPayload(filename, contentType, content, extension);
            String rawResponse = switch (payload.kind()) {
                case PDF_INLINE -> generatePdfContent(apiKey, payload.prompt(), content);
                case EXTRACTED_TEXT -> generateTextContent(apiKey, payload.prompt());
            };
            MarkdownExtraction extraction = extractMarkdown(rawResponse);
            List<String> warnings = new ArrayList<>(payload.warnings());
            warnings.addAll(extraction.warnings());
            return new DocumentConversionResult(
                    filename,
                    extension,
                    extraction.markdown(),
                    ENGINE,
                    warnings
            );
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to convert document with Gemini, filename={}", filename, e);
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini document conversion failed.");
        }
    }

    @Override
    public String normalizeText(String title, String content) {
        if ((title == null || title.isBlank()) && (content == null || content.isBlank())) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Title or content is required.");
        }
        return buildDirectTextMarkdown(title, content);
    }

    private boolean isDirectTextExtension(String extension) {
        return "txt".equals(extension) || "md".equals(extension) || "markdown".equals(extension);
    }

    private DocumentConversionResult buildDirectFileResult(String filename, String extension, byte[] content) {
        DecodedText decodedText = decodePlainTextContent(content);
        String markdown = normalizeDirectMarkdown(decodedText.text());
        if (markdown.isBlank()) {
            throw new BusinessException(
                    ResultCode.DOCUMENT_CONVERSION_FAILED,
                    "The uploaded text file did not contain usable content."
            );
        }

        return new DocumentConversionResult(
                filename,
                extension,
                markdown,
                DIRECT_ENGINE,
                decodedText.warnings()
        );
    }

    private String buildDirectTextMarkdown(String title, String content) {
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : normalizeDirectMarkdown(content);

        if (safeTitle.isBlank()) {
            return safeContent;
        }
        if (safeContent.isBlank()) {
            return "# " + safeTitle;
        }
        return "# " + safeTitle + "\n\n" + safeContent;
    }

    private String normalizeDirectMarkdown(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
    }

    private DecodedText decodePlainTextContent(byte[] content) {
        byte[] sanitized = stripUtf8Bom(content);
        try {
            return new DecodedText(decodeStrict(sanitized, StandardCharsets.UTF_8), List.of());
        } catch (CharacterCodingException ignored) {
            try {
                return new DecodedText(
                        decodeStrict(sanitized, GB18030),
                        List.of("The original text file was decoded with GB18030 because it was not valid UTF-8.")
                );
            } catch (CharacterCodingException secondFailure) {
                String fallback = new String(sanitized, StandardCharsets.UTF_8);
                return new DecodedText(
                        fallback,
                        List.of("The original text file could not be decoded cleanly; UTF-8 fallback was used.")
                );
            }
        }
    }

    private String decodeStrict(byte[] content, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(content))
                .toString();
    }

    private byte[] stripUtf8Bom(byte[] content) {
        if (content.length >= 3
                && (content[0] & 0xFF) == 0xEF
                && (content[1] & 0xFF) == 0xBB
                && (content[2] & 0xFF) == 0xBF) {
            byte[] trimmed = new byte[content.length - 3];
            System.arraycopy(content, 3, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return content;
    }

    private ConversionPayload buildConversionPayload(String filename, String contentType, byte[] content, String extension) {
        if ("pdf".equals(extension)) {
            return new ConversionPayload(
                    ConversionKind.PDF_INLINE,
                    buildPdfPrompt(filename),
                    List.of()
            );
        }

        DocumentSourceExtractor.ExtractedDocumentSource source = documentSourceExtractor.extract(filename, content);
        return new ConversionPayload(
                ConversionKind.EXTRACTED_TEXT,
                buildExtractedFilePrompt(filename, contentType, source),
                source.warnings()
        );
    }

    private String generatePdfContent(String apiKey, String userPrompt, byte[] pdfContent) {
        String encodedPdf = Base64.getEncoder().encodeToString(pdfContent);
        GenerateContentRequest request = new GenerateContentRequest(
                List.of(new Content("user", List.of(
                        new InlineDataPart(new InlineData("application/pdf", encodedPdf)),
                        new TextPart(userPrompt)
                ))),
                new GenerationConfig(0.1, 32_768)
        );
        return invokeGemini(apiKey, request);
    }

    private String generateTextContent(String apiKey, String userPrompt) {
        GenerateContentRequest request = new GenerateContentRequest(
                List.of(new Content("user", List.of(new TextPart(userPrompt)))),
                new GenerationConfig(0.1, 32_768)
        );
        return invokeGemini(apiKey, request);
    }

    private String invokeGemini(String apiKey, GenerateContentRequest request) {
        try {
            GenerateContentResponse response = buildClient()
                    .post()
                    .uri("/v1beta/models/{modelName}:generateContent", properties.getModelName())
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(GenerateContentResponse.class);

            String text = extractResponseText(response);
            if (text == null || text.isBlank()) {
                throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini returned empty content.");
            }
            return text;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientResponseException e) {
            String message = extractGoogleErrorMessage(e.getResponseBodyAsString());
            log.warn("Gemini conversion request failed, status={}, message={}", e.getStatusCode(), message);
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, message);
        } catch (RestClientException e) {
            log.error("Failed to call Gemini generateContent", e);
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini conversion service is unavailable.");
        }
    }

    private RestClient buildClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.toIntExact(properties.getTimeout().toMillis());
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);

        return restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private String requireApiKey() {
        String apiKey = environment.getProperty("langchain4j.google-ai-gemini.chat-model.api-key");
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini API key is not configured.");
        }
        return apiKey;
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String buildPdfPrompt(String filename) {
        return """
                %s

                The attached PDF is the original source document.
                Read it directly and convert it into Markdown now.
                Return only the following wrapper and nothing else:
                <md-text>
                ...markdown content...
                </md-text>

                <file-metadata>
                name: %s
                content_type: application/pdf
                </file-metadata>
                """.formatted(FILE_SYSTEM_PROMPT, filename);
    }

    private String buildExtractedFilePrompt(
            String filename,
            String contentType,
            DocumentSourceExtractor.ExtractedDocumentSource source
    ) {
        String notes = source.warnings().isEmpty()
                ? "(none)"
                : String.join("\n", source.warnings());
        return """
                %s

                Convert the extracted source into Markdown now.
                Return only the following wrapper and nothing else:
                <md-text>
                ...markdown content...
                </md-text>

                <file-metadata>
                name: %s
                content_type: %s
                </file-metadata>
                <extraction-notes>
                %s
                </extraction-notes>
                <source-text>
                %s
                </source-text>
                """.formatted(
                FILE_SYSTEM_PROMPT,
                filename,
                contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType,
                notes,
                source.text()
        );
    }

    private String extractResponseText(GenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (Candidate candidate : response.candidates()) {
            if (candidate == null || candidate.content() == null || candidate.content().parts() == null) {
                continue;
            }
            for (ResponsePart part : candidate.content().parts()) {
                if (part != null && part.text() != null && !part.text().isBlank()) {
                    if (!builder.isEmpty()) {
                        builder.append('\n');
                    }
                    builder.append(part.text().trim());
                }
            }
            if (!builder.isEmpty()) {
                break;
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private MarkdownExtraction extractMarkdown(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini returned empty content.");
        }

        Matcher matcher = MD_TEXT_PATTERN.matcher(rawResponse);
        if (matcher.find()) {
            String markdown = matcher.group(1).trim();
            if (markdown.isBlank()) {
                throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini returned an empty Markdown block.");
            }
            return new MarkdownExtraction(markdown, List.of());
        }

        String fallback = stripCodeFence(rawResponse).trim();
        if (fallback.isBlank()) {
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Gemini response did not contain usable Markdown.");
        }

        log.warn("Gemini response did not contain <md-text> wrapper, using raw response fallback.");
        return new MarkdownExtraction(
                fallback,
                List.of("Gemini response omitted <md-text> wrapper; raw response was used.")
        );
    }

    private String stripCodeFence(String value) {
        String trimmed = value.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String withoutOpeningFence = trimmed.substring(firstNewline + 1);
        int closingFence = withoutOpeningFence.lastIndexOf("```");
        if (closingFence < 0) {
            return withoutOpeningFence;
        }
        return withoutOpeningFence.substring(0, closingFence);
    }

    private String extractGoogleErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Gemini document conversion failed.";
        }

        Matcher matcher = GOOGLE_ERROR_MESSAGE_PATTERN.matcher(responseBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return responseBody;
    }

    private record MarkdownExtraction(
            String markdown,
            List<String> warnings
    ) {
    }

    private record DecodedText(
            String text,
            List<String> warnings
    ) {
    }

    private record GenerateContentRequest(
            List<Content> contents,
            @JsonProperty("generationConfig")
            GenerationConfig generationConfig
    ) {
    }

    private record Content(
            String role,
            List<Part> parts
    ) {
    }

    private sealed interface Part permits TextPart, InlineDataPart {
    }

    private record TextPart(String text) implements Part {
    }

    private record InlineDataPart(
            @JsonProperty("inline_data")
            InlineData inlineData
    ) implements Part {
    }

    private record InlineData(
            @JsonProperty("mime_type")
            String mimeType,
            String data
    ) {
    }

    private record GenerationConfig(
            double temperature,
            @JsonProperty("maxOutputTokens")
            int maxOutputTokens
    ) {
    }

    private record GenerateContentResponse(List<Candidate> candidates) {
    }

    private record Candidate(ResponseContent content) {
    }

    private record ResponseContent(List<ResponsePart> parts) {
    }

    private record ResponsePart(String text) {
    }

    private enum ConversionKind {
        PDF_INLINE,
        EXTRACTED_TEXT
    }

    private record ConversionPayload(
            ConversionKind kind,
            String prompt,
            List<String> warnings
    ) {
    }
}
