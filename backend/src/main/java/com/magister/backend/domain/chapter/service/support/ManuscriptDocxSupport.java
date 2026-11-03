package com.magister.backend.domain.chapter.service.support;

import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.domain.chapter.service.support.MarkdownAstSupport.InlineSpan;
import com.magister.backend.domain.chapter.service.support.MarkdownAstSupport.MarkdownBlock;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Apache POI(XWPFDocument)를 사용해 마크다운을 Word(.docx) 스타일로 변환(Export)하고 문단을 추출(Import)한다.
 */
@Component
@RequiredArgsConstructor
public class ManuscriptDocxSupport {
    private static final int BODY_FONT_PT = 11;
    private static final int CODE_FONT_PT = 10;
    private static final int MAX_RAW_CHARS = 250_000;
    private static final int MAX_PARAGRAPHS = 20_000;
    private static final long MAX_ZIP_ENTRY_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_ZIP_TEXT_CHARS = 1_000_000L;
    private static final double MIN_INFLATE_RATIO = 0.01d;

    static {
        // OOXML 압축 폭탄(Zip Bomb) 및 과도한 엔트리 확장을 차단한다.
        ZipSecureFile.setMinInflateRatio(MIN_INFLATE_RATIO);
        ZipSecureFile.setMaxEntrySize(MAX_ZIP_ENTRY_BYTES);
        ZipSecureFile.setMaxTextSize(MAX_ZIP_TEXT_CHARS);
    }

    private final MarkdownAstSupport markdownAstSupport;
    private final ManuscriptTextFormatter textFormatter;

    public byte[] exportChapter(Work work, Chapter chapter) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeMetadataHeader(document, work.getTitle(), work.getGenre(), chapter.getTitle());
            List<MarkdownBlock> blocks = markdownAstSupport.parseBlocks(chapter.getContent());
            writeBlocks(document, blocks);
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new CustomException(ChapterErrorCode.EXPORT_GENERATION_FAILED);
        }
    }

    public byte[] exportWork(Work work, List<Chapter> chapters) {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeMetadataHeader(document, work.getTitle(), work.getGenre(), null);
            for (Chapter chapter : chapters) {
                XWPFParagraph banner = document.createParagraph();
                banner.setSpacingBefore(240);
                banner.setSpacingAfter(120);
                XWPFRun bannerRun = banner.createRun();
                bannerRun.setBold(true);
                bannerRun.setFontSize(14);
                bannerRun.setText("=== " + chapter.getTitle() + " ===");

                List<MarkdownBlock> blocks = markdownAstSupport.parseBlocks(chapter.getContent());
                writeBlocks(document, blocks);
            }
            document.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new CustomException(ChapterErrorCode.EXPORT_GENERATION_FAILED);
        }
    }

    public ParsedManuscript parseDocxBytes(byte[] rawBytes, String fallbackChapterTitle) {
        if (rawBytes == null || rawBytes.length < 4) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(rawBytes))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            if (paragraphs.size() > MAX_PARAGRAPHS) {
                throw new CustomException(ChapterErrorCode.MANUSCRIPT_TOO_LONG);
            }
            List<String> lines = new ArrayList<>();
            int accumulatedChars = 0;

            for (XWPFParagraph paragraph : paragraphs) {
                String text = paragraph.getText();
                if (text == null) {
                    continue;
                }
                String trimmed = text.strip();
                int headingLevel = detectHeadingLevel(paragraph, trimmed);
                String line =
                        (headingLevel >= 1 && headingLevel <= 6 && !trimmed.isEmpty())
                                ? "#".repeat(headingLevel) + " " + trimmed
                                : text;
                accumulatedChars += line.length() + 1;
                if (accumulatedChars > MAX_RAW_CHARS) {
                    throw new CustomException(ChapterErrorCode.MANUSCRIPT_TOO_LONG);
                }
                lines.add(line);
            }

            String joined = String.join("\n", lines);
            return textFormatter.extractHeaderAndBody(
                    joined, ExportFormat.DOCX, fallbackChapterTitle);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
    }

    private int detectHeadingLevel(XWPFParagraph paragraph, String trimmedText) {
        if (trimmedText.startsWith("제목: ")
                || trimmedText.startsWith("장르: ")
                || trimmedText.startsWith("회차: ")
                || trimmedText.equals("---")
                || (trimmedText.startsWith("=== ") && trimmedText.endsWith(" ==="))) {
            return 0;
        }
        String style = paragraph.getStyle();
        if (style != null) {
            String lower = style.strip().toLowerCase(Locale.ROOT);
            if (lower.startsWith("heading") && lower.length() == 8) {
                char levelChar = lower.charAt(7);
                if (levelChar >= '1' && levelChar <= '6') {
                    return levelChar - '0';
                }
            }
        }
        return 0;
    }

    private void writeMetadataHeader(
            XWPFDocument document, String workTitle, String genre, String chapterTitle) {
        XWPFParagraph titleParagraph = document.createParagraph();
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(16);
        titleRun.setText("제목: " + workTitle);

        XWPFParagraph genreParagraph = document.createParagraph();
        XWPFRun genreRun = genreParagraph.createRun();
        genreRun.setFontSize(BODY_FONT_PT);
        genreRun.setText("장르: " + genre);

        if (chapterTitle != null && !chapterTitle.isBlank()) {
            XWPFParagraph chapterParagraph = document.createParagraph();
            XWPFRun chapterRun = chapterParagraph.createRun();
            chapterRun.setBold(true);
            chapterRun.setFontSize(12);
            chapterRun.setText("회차: " + chapterTitle);
        }

        XWPFParagraph separatorParagraph = document.createParagraph();
        separatorParagraph.setBorderBottom(Borders.SINGLE);
        XWPFRun separatorRun = separatorParagraph.createRun();
        separatorRun.setFontSize(9);
        separatorRun.setText("---");
    }

    private void writeBlocks(XWPFDocument document, List<MarkdownBlock> blocks) {
        for (MarkdownBlock block : blocks) {
            XWPFParagraph paragraph = document.createParagraph();
            if (block.quoted()) {
                paragraph.setIndentationLeft(360);
                paragraph.setBorderLeft(Borders.SINGLE);
            }
            switch (block.type()) {
                case HEADING -> {
                    int level = Math.max(1, Math.min(6, block.headingLevel()));
                    paragraph.setStyle("Heading" + level);
                    paragraph.setSpacingBefore(160);
                    paragraph.setSpacingAfter(80);
                    int fontSize = Math.round(ManuscriptPdfSupport.headingFontSize(level));
                    writeSpans(paragraph, block.spans(), fontSize, true);
                }
                case PARAGRAPH, BLOCK_QUOTE -> {
                    paragraph.setSpacingAfter(80);
                    writeSpans(paragraph, block.spans(), BODY_FONT_PT, false);
                }
                case BULLET_ITEM -> {
                    paragraph.setIndentationLeft(block.quoted() ? 600 : 300);
                    XWPFRun prefix = paragraph.createRun();
                    prefix.setBold(true);
                    prefix.setFontSize(BODY_FONT_PT);
                    prefix.setText("• ");
                    writeSpans(paragraph, block.spans(), BODY_FONT_PT, false);
                }
                case ORDERED_ITEM -> {
                    paragraph.setIndentationLeft(block.quoted() ? 600 : 300);
                    XWPFRun prefix = paragraph.createRun();
                    prefix.setBold(true);
                    prefix.setFontSize(BODY_FONT_PT);
                    prefix.setText(block.itemNumber() + ". ");
                    writeSpans(paragraph, block.spans(), BODY_FONT_PT, false);
                }
                case CODE_BLOCK -> {
                    paragraph.setIndentationLeft(block.quoted() ? 600 : 240);
                    writeSpans(paragraph, block.spans(), CODE_FONT_PT, false);
                }
                case THEMATIC_BREAK -> paragraph.setBorderBottom(Borders.SINGLE);
            }
        }
    }

    private void writeSpans(
            XWPFParagraph paragraph,
            List<InlineSpan> spans,
            int fontSize,
            boolean forceBold) {
        for (InlineSpan span : spans) {
            XWPFRun run = paragraph.createRun();
            run.setFontSize(fontSize);
            run.setBold(forceBold || span.bold());
            run.setItalic(span.italic());
            run.setStrikeThrough(span.strikethrough());
            String[] lines = span.text().split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                if (index > 0) {
                    run.addBreak();
                }
                run.setText(lines[index]);
            }
        }
    }
}
