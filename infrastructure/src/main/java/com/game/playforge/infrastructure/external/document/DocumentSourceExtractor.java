package com.game.playforge.infrastructure.external.document;

import com.game.playforge.common.exception.BusinessException;
import com.game.playforge.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Extracts readable source text from supported documents before Gemini normalization.
 */
@Slf4j
@Component
public class DocumentSourceExtractor {

    public ExtractedDocumentSource extract(String filename, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_FAILED, "Uploaded file cannot be empty.");
        }

        String extension = extractExtension(filename);
        try {
            return switch (extension) {
                case "pdf" -> extractPdf(content);
                case "docx" -> extractDocx(content);
                case "doc" -> extractDoc(content);
                case "xlsx", "xls" -> extractWorkbook(content);
                default -> throw new BusinessException(
                        ResultCode.DOCUMENT_FORMAT_UNSUPPORTED,
                        "Supported formats are pdf, doc, docx, xls, and xlsx."
                );
            };
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract source text from document, filename={}", filename, e);
            throw new BusinessException(ResultCode.DOCUMENT_CONVERSION_FAILED, "Failed to extract text from the uploaded file.");
        }
    }

    private ExtractedDocumentSource extractPdf(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder builder = new StringBuilder();

            for (int page = 1; page <= document.getNumberOfPages(); page += 1) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = normalizeText(stripper.getText(document));
                if (pageText.isBlank()) {
                    continue;
                }
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append("Page ").append(page).append('\n').append(pageText);
            }

            return finalizeResult(builder.toString(), List.of());
        }
    }

    private ExtractedDocumentSource extractDocx(byte[] content) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content);
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            List<String> warnings = new ArrayList<>();
            if (!document.getAllPictures().isEmpty()) {
                warnings.add("The original Word document contains embedded images. Only readable text was extracted.");
            }
            return finalizeResult(extractor.getText(), warnings);
        }
    }

    private ExtractedDocumentSource extractDoc(byte[] content) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return finalizeResult(extractor.getText(), List.of());
        }
    }

    private ExtractedDocumentSource extractWorkbook(byte[] content) throws IOException {
        try (InputStream inputStream = new ByteArrayInputStream(content);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder builder = new StringBuilder();
            List<String> warnings = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex += 1) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                if (!builder.isEmpty()) {
                    builder.append("\n\n");
                }
                builder.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                appendWorkbookWarnings(sheet, warnings);

                boolean hasContent = false;
                int firstRow = Math.max(sheet.getFirstRowNum(), 0);
                int lastRow = Math.max(sheet.getLastRowNum(), firstRow);
                for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex += 1) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    String formattedRow = formatRow(row, evaluator, formatter);
                    if (formattedRow == null) {
                        continue;
                    }
                    hasContent = true;
                    builder.append("- Row ").append(rowIndex + 1).append(": ").append(formattedRow).append('\n');
                }

                if (!hasContent) {
                    builder.append("(empty sheet)\n");
                }
            }

            return finalizeResult(builder.toString(), warnings);
        }
    }

    private void appendWorkbookWarnings(Sheet sheet, List<String> warnings) {
        if (!(sheet instanceof XSSFSheet xssfSheet)) {
            return;
        }
        int imageCount = xssfSheet.getDrawingPatriarch() == null
                ? 0
                : xssfSheet.getDrawingPatriarch().getShapes().size();
        if (imageCount > 0) {
            warnings.add("The original spreadsheet sheet '" + sheet.getSheetName()
                    + "' contains embedded images or drawing objects. Only readable cell text was extracted.");
        }
    }

    private String formatRow(Row row, FormulaEvaluator evaluator, DataFormatter formatter) {
        int firstCell = Math.max(row.getFirstCellNum(), 0);
        int lastCell = Math.max(row.getLastCellNum(), firstCell);
        StringJoiner joiner = new StringJoiner(" | ");

        for (int cellIndex = firstCell; cellIndex < lastCell; cellIndex += 1) {
            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }

            String formattedValue = formatCell(cell, evaluator, formatter);
            if (formattedValue == null || formattedValue.isBlank()) {
                continue;
            }

            joiner.add(columnName(cellIndex) + (row.getRowNum() + 1) + "=" + sanitizeInline(formattedValue));
        }

        String result = joiner.toString().trim();
        return result.isBlank() ? null : result;
    }

    private String formatCell(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        CellType cellType = cell.getCellType();
        if (cellType == CellType.FORMULA) {
            try {
                return formatter.formatCellValue(cell, evaluator);
            } catch (Exception e) {
                log.warn("Failed to evaluate formula cell {}{}", columnName(cell.getColumnIndex()), cell.getRowIndex() + 1, e);
                return formatter.formatCellValue(cell);
            }
        }
        return formatter.formatCellValue(cell);
    }

    private ExtractedDocumentSource finalizeResult(String rawText, List<String> warnings) {
        String normalized = normalizeText(rawText);
        if (normalized.isBlank()) {
            throw new BusinessException(
                    ResultCode.DOCUMENT_CONVERSION_FAILED,
                    "The uploaded file did not contain extractable text content."
            );
        }
        return new ExtractedDocumentSource(normalized, List.copyOf(warnings));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace('\u0000', ' ')
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String sanitizeInline(String value) {
        return value
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replaceAll("\\s{2,}", " ")
                .trim();
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private String columnName(int columnIndex) {
        StringBuilder builder = new StringBuilder();
        int current = columnIndex;
        do {
            builder.insert(0, (char) ('A' + (current % 26)));
            current = current / 26 - 1;
        } while (current >= 0);
        return builder.toString();
    }

    public record ExtractedDocumentSource(
            String text,
            List<String> warnings
    ) {
    }
}
