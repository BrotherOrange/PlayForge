package com.game.playforge.api.controller;

import com.game.playforge.api.dto.response.DocumentConversionResponse;
import com.game.playforge.common.constant.AuthConstants;
import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ApiResult;
import com.game.playforge.common.result.ResultCode;
import com.game.playforge.infrastructure.external.document.DocumentConversionResult;
import com.game.playforge.infrastructure.external.document.DocumentConversionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Document conversion endpoints.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentConversionService documentConversionService;

    @PostMapping(value = "/convert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<DocumentConversionResponse> convertToMarkdown(
            HttpServletRequest request,
            @RequestParam("file") MultipartFile file) {
        Long userId = (Long) request.getAttribute(AuthConstants.CURRENT_USER_ID);
        log.info("Convert document to markdown, userId={}, filename={}, size={}",
                userId, file.getOriginalFilename(), file.getSize());

        try {
            DocumentConversionResult result = documentConversionService.convert(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getBytes()
            );
            return ApiResult.success(DocumentConversionResponse.from(result));
        } catch (IOException e) {
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Failed to read uploaded file.");
        }
    }
}
