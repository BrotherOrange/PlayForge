package com.game.playforge.application.service.review;

import com.game.playforge.application.dto.review.ReviewBlockData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Splits canonical Markdown into stable review blocks with plain-text mirrors.
 */
@Component
public class ReviewMarkdownBlockParser {

    private static final Pattern HEADING_PATTERN = Pattern.compile("^#{1,6}\\s+.*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("^\\s*(?:[-+*]|\\d+\\.)\\s+.*$");
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\s*\\d+\\.\\s+.*$");
    private static final Pattern THEMATIC_BREAK_PATTERN = Pattern.compile("^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$");
    private static final Pattern TABLE_SEPARATOR_PATTERN = Pattern.compile("^\\s*\\|?(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$");

    public List<ReviewBlockData> parse(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of(new ReviewBlockData("b001", "paragraph", "", ""));
        }

        String[] lines = normalized.split("\n");
        List<ReviewBlockData> blocks = new ArrayList<>();
        int index = 0;
        int blockNumber = 1;

        while (index < lines.length) {
            if (lines[index].isBlank()) {
                index += 1;
                continue;
            }

            ParsedBlock parsedBlock = readBlock(lines, index);
            blocks.add(toBlock(blockNumber, parsedBlock));
            blockNumber += 1;
            index = parsedBlock.nextIndex();
        }

        return blocks;
    }

    private ParsedBlock readBlock(String[] lines, int startIndex) {
        String line = lines[startIndex];

        if (line.startsWith("```")) {
            return readFencedCode(lines, startIndex);
        }
        if (isTableLine(line)) {
            return readTable(lines, startIndex);
        }
        if (HEADING_PATTERN.matcher(line).matches()) {
            return new ParsedBlock("heading", line, startIndex + 1);
        }
        if (line.startsWith(">")) {
            return readQuote(lines, startIndex);
        }
        if (LIST_PATTERN.matcher(line).matches()) {
            return readList(lines, startIndex);
        }
        if (THEMATIC_BREAK_PATTERN.matcher(line).matches()) {
            return new ParsedBlock("rule", line, startIndex + 1);
        }
        return readParagraph(lines, startIndex);
    }

    private ParsedBlock readFencedCode(String[] lines, int startIndex) {
        StringBuilder builder = new StringBuilder(lines[startIndex]);
        int index = startIndex + 1;
        while (index < lines.length) {
            builder.append('\n').append(lines[index]);
            if (lines[index].startsWith("```")) {
                index += 1;
                break;
            }
            index += 1;
        }
        return new ParsedBlock("code", builder.toString(), index);
    }

    private ParsedBlock readTable(String[] lines, int startIndex) {
        StringBuilder builder = new StringBuilder(lines[startIndex]);
        int index = startIndex + 1;
        while (index < lines.length && isTableLine(lines[index])) {
            builder.append('\n').append(lines[index]);
            index += 1;
        }
        return new ParsedBlock("table", builder.toString(), index);
    }

    private ParsedBlock readQuote(String[] lines, int startIndex) {
        StringBuilder builder = new StringBuilder(lines[startIndex]);
        int index = startIndex + 1;
        while (index < lines.length && !lines[index].isBlank() && lines[index].startsWith(">")) {
            builder.append('\n').append(lines[index]);
            index += 1;
        }
        return new ParsedBlock("quote", builder.toString(), index);
    }

    private ParsedBlock readList(String[] lines, int startIndex) {
        StringBuilder builder = new StringBuilder(lines[startIndex]);
        int index = startIndex + 1;
        while (index < lines.length) {
            String current = lines[index];
            if (current.isBlank()) {
                break;
            }
            if (LIST_PATTERN.matcher(current).matches()
                    || current.startsWith("  ")
                    || current.startsWith("\t")
                    || current.startsWith(">")) {
                builder.append('\n').append(current);
                index += 1;
                continue;
            }
            break;
        }
        return new ParsedBlock("list", builder.toString(), index);
    }

    private ParsedBlock readParagraph(String[] lines, int startIndex) {
        StringBuilder builder = new StringBuilder(lines[startIndex]);
        int index = startIndex + 1;
        while (index < lines.length) {
            String current = lines[index];
            if (current.isBlank()
                    || current.startsWith("```")
                    || current.startsWith(">")
                    || HEADING_PATTERN.matcher(current).matches()
                    || LIST_PATTERN.matcher(current).matches()
                    || THEMATIC_BREAK_PATTERN.matcher(current).matches()
                    || isTableLine(current)) {
                break;
            }
            builder.append('\n').append(current);
            index += 1;
        }
        return new ParsedBlock("paragraph", builder.toString(), index);
    }

    private ReviewBlockData toBlock(int blockNumber, ParsedBlock parsedBlock) {
        String blockId = "b" + String.format(Locale.ROOT, "%03d", blockNumber);
        String text = toPlainText(parsedBlock.markdown(), parsedBlock.blockType());
        return new ReviewBlockData(blockId, parsedBlock.blockType(), parsedBlock.markdown(), text);
    }

    private String toPlainText(String markdown, String blockType) {
        String text = switch (blockType) {
            case "heading" -> markdown.replaceFirst("^#{1,6}\\s+", "");
            case "list" -> stripListMarkdown(markdown);
            case "quote" -> markdown.replaceAll("(?m)^>\\s?", "");
            case "table" -> stripTableMarkdown(markdown);
            case "code" -> markdown.replaceAll("(?m)^```.*$", "").trim();
            case "rule" -> "---";
            default -> markdown;
        };

        text = text
                .replaceAll("!\\[[^\\]]*]\\(([^)]+)\\)", "$1")
                .replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "$1")
                .replaceAll("(?<!`)`([^`]+)`", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
                .replace("_", "")
                .replace("~~", "")
                .replace("&nbsp;", " ")
                .trim();

        return text;
    }

    private String stripListMarkdown(String markdown) {
        StringBuilder builder = new StringBuilder();
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            String normalized = line.replaceFirst("^\\s*(?:[-+*]|\\d+\\.)\\s+", "")
                    .replaceFirst("^\\s{2,}", "");
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(normalized);
        }
        return builder.toString();
    }

    private String stripTableMarkdown(String markdown) {
        StringBuilder builder = new StringBuilder();
        String[] lines = markdown.split("\n");
        for (String line : lines) {
            if (TABLE_SEPARATOR_PATTERN.matcher(line).matches()) {
                continue;
            }
            String normalized = line.trim();
            if (normalized.startsWith("|")) {
                normalized = normalized.substring(1);
            }
            if (normalized.endsWith("|")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            normalized = normalized.replace("\\|", "|");
            String[] cells = normalized.split("\\|");
            List<String> cleanCells = new ArrayList<>();
            for (String cell : cells) {
                cleanCells.add(cell.trim());
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(String.join(" | ", cleanCells));
        }
        return builder.toString();
    }

    private boolean isTableLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || !trimmed.contains("|")) {
            return false;
        }
        return trimmed.startsWith("|")
                || trimmed.endsWith("|")
                || TABLE_SEPARATOR_PATTERN.matcher(trimmed).matches()
                || (trimmed.contains("|") && !ORDERED_LIST_PATTERN.matcher(trimmed).matches());
    }

    private record ParsedBlock(
            String blockType,
            String markdown,
            int nextIndex
    ) {
    }
}
