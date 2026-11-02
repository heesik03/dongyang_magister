package com.magister.backend.domain.chapter.service.support;

import com.magister.backend.domain.chapter.dto.ExportFormat;
import com.magister.backend.domain.chapter.dto.ParsedManuscript;
import com.magister.backend.domain.chapter.entity.Chapter;
import com.magister.backend.domain.chapter.error.ChapterErrorCode;
import com.magister.backend.domain.chapter.service.support.MarkdownAstSupport.BlockType;
import com.magister.backend.domain.chapter.service.support.MarkdownAstSupport.InlineSpan;
import com.magister.backend.domain.chapter.service.support.MarkdownAstSupport.MarkdownBlock;
import com.magister.backend.domain.work.entity.Work;
import com.magister.backend.global.error.exception.CustomException;

import lombok.RequiredArgsConstructor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * PDFBox를 사용해 마크다운을 스타일이 적용된 PDF로 변환(Export)하고 PDF에서 원고 텍스트를 추출(Import)한다.
 */
@Component
@RequiredArgsConstructor
public class ManuscriptPdfSupport {
    private static final float PAGE_MARGIN_X = 50.0f;
    private static final float PAGE_MARGIN_TOP = 55.0f;
    private static final float PAGE_MARGIN_BOTTOM = 55.0f;
    private static final float BODY_FONT_SIZE = 11.0f;
    private static final float CODE_FONT_SIZE = 10.0f;
    private static final float QUOTE_INDENT = 16.0f;
    private static final float LIST_INDENT = 14.0f;
    private static final int MAX_PDF_PAGES = 2000;

    private static final List<String> REGULAR_FONT_PATHS =
            List.of(
                    "C:/Windows/Fonts/malgun.ttf",
                    "C:/Windows/Fonts/NanumGothic.ttf",
                    "/usr/share/fonts/truetype/nanum/NanumGothic.ttf",
                    "/usr/share/fonts/truetype/noto/NotoSansKR-Regular.ttf",
                    "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
                    "/usr/share/fonts/truetype/unfonts-core/UnDotum.ttf",
                    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");

    private static final List<String> BOLD_FONT_PATHS =
            List.of(
                    "C:/Windows/Fonts/malgunbd.ttf",
                    "C:/Windows/Fonts/NanumGothicBold.ttf",
                    "/usr/share/fonts/truetype/nanum/NanumGothicBold.ttf",
                    "/usr/share/fonts/truetype/noto/NotoSansKR-Bold.ttf",
                    "/usr/share/fonts/truetype/unfonts-core/UnDotumBold.ttf",
                    "/System/Library/Fonts/Supplemental/AppleGothic.ttf",
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");

    private final MarkdownAstSupport markdownAstSupport;
    private final ManuscriptTextFormatter textFormatter;

    public byte[] exportChapter(Work work, Chapter chapter) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfFontSet fonts = loadFonts(document);
            try (PdfLayoutCursor cursor = new PdfLayoutCursor(document, fonts)) {
                cursor.writeMetadataHeader(work.getTitle(), work.getGenre(), chapter.getTitle());
                List<MarkdownBlock> blocks = markdownAstSupport.parseBlocks(chapter.getContent());
                cursor.writeBlocks(blocks);
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new CustomException(ChapterErrorCode.EXPORT_GENERATION_FAILED);
        }
    }

    public byte[] exportWork(Work work, List<Chapter> chapters) {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfFontSet fonts = loadFonts(document);
            try (PdfLayoutCursor cursor = new PdfLayoutCursor(document, fonts)) {
                cursor.writeMetadataHeader(work.getTitle(), work.getGenre(), null);
                for (int index = 0; index < chapters.size(); index++) {
                    Chapter chapter = chapters.get(index);
                    if (index > 0) {
                        cursor.drawHorizontalRule();
                    }
                    cursor.writeChapterBanner(chapter.getTitle());
                    List<MarkdownBlock> blocks =
                            markdownAstSupport.parseBlocks(chapter.getContent());
                    cursor.writeBlocks(blocks);
                }
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new CustomException(ChapterErrorCode.EXPORT_GENERATION_FAILED);
        }
    }

    public ParsedManuscript parsePdfBytes(byte[] rawBytes, String fallbackChapterTitle) {
        if (rawBytes == null || rawBytes.length < 5) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
        try (PDDocument document = Loader.loadPDF(rawBytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String extracted = stripper.getText(document);
            String strippedContent = stripTrailingSpacing(extracted);
            return textFormatter.extractHeaderAndBody(
                    strippedContent, ExportFormat.PDF, fallbackChapterTitle);
        } catch (CustomException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CustomException(ChapterErrorCode.INVALID_MANUSCRIPT_FILE);
        }
    }

    public static float headingFontSize(int headingLevel) {
        return switch (headingLevel) {
            case 1 -> 22.0f;
            case 2 -> 18.0f;
            case 3 -> 15.0f;
            case 4 -> 13.0f;
            case 5 -> 12.0f;
            default -> 11.5f;
        };
    }

    private String stripTrailingSpacing(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private PdfFontSet loadFonts(PDDocument document) throws IOException {
        PDFont regular = loadFontCandidate(document, "/fonts/NotoSansKR-Regular.ttf", REGULAR_FONT_PATHS);
        PDFont bold = loadFontCandidate(document, "/fonts/NotoSansKR-Bold.ttf", BOLD_FONT_PATHS);
        if (regular == null) {
            regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        }
        if (bold == null) {
            bold =
                    regular instanceof PDType0Font
                            ? regular
                            : new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        }
        return new PdfFontSet(regular, bold);
    }

    private PDFont loadFontCandidate(
            PDDocument document, String classpathResource, List<String> systemPaths)
            throws IOException {
        try (InputStream resourceStream =
                ManuscriptPdfSupport.class.getResourceAsStream(classpathResource)) {
            if (resourceStream != null) {
                return PDType0Font.load(document, resourceStream, true);
            }
        }
        for (String path : systemPaths) {
            File file = new File(path);
            if (file.isFile() && file.canRead() && path.endsWith(".ttf")) {
                return PDType0Font.load(document, file);
            }
        }
        return null;
    }

    private record PdfFontSet(PDFont regular, PDFont bold) {}

    private record StyledSegment(
            String text, boolean bold, boolean italic, boolean strikethrough, boolean code) {}

    private static final class PdfLayoutCursor implements AutoCloseable {
        private final PDDocument document;
        private final PdfFontSet fonts;
        private PDPage currentPage;
        private PDPageContentStream stream;
        private float cursorY;
        private float usableWidth;

        private PdfLayoutCursor(PDDocument document, PdfFontSet fonts) throws IOException {
            this.document = document;
            this.fonts = fonts;
            openNewPage();
        }

        private void openNewPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            PDRectangle mediaBox = currentPage.getMediaBox();
            usableWidth = mediaBox.getWidth() - (PAGE_MARGIN_X * 2);
            cursorY = mediaBox.getHeight() - PAGE_MARGIN_TOP;
            stream = new PDPageContentStream(document, currentPage);
        }

        private void ensureSpace(float requiredHeight) throws IOException {
            if (cursorY - requiredHeight < PAGE_MARGIN_BOTTOM) {
                openNewPage();
            }
        }

        private void writeMetadataHeader(String workTitle, String genre, String chapterTitle)
                throws IOException {
            writeStyledParagraph(
                    List.of(new StyledSegment("제목: " + workTitle, true, false, false, false)),
                    16.0f,
                    0.0f,
                    6.0f,
                    false);
            writeStyledParagraph(
                    List.of(new StyledSegment("장르: " + genre, false, false, false, false)),
                    11.0f,
                    0.0f,
                    4.0f,
                    false);
            if (chapterTitle != null && !chapterTitle.isBlank()) {
                writeStyledParagraph(
                        List.of(new StyledSegment("회차: " + chapterTitle, true, false, false, false)),
                        12.0f,
                        0.0f,
                        6.0f,
                        false);
            }
            writeStyledParagraph(
                    List.of(new StyledSegment("---", false, false, false, false)),
                    8.0f,
                    0.0f,
                    4.0f,
                    false);
            drawHorizontalRule();
        }

        private void writeChapterBanner(String chapterTitle) throws IOException {
            cursorY -= 6.0f;
            writeStyledParagraph(
                    List.of(
                            new StyledSegment(
                                    "=== " + chapterTitle + " ===", true, false, false, false)),
                    14.0f,
                    0.0f,
                    8.0f,
                    false);
        }

        private void writeBlocks(List<MarkdownBlock> blocks) throws IOException {
            for (MarkdownBlock block : blocks) {
                switch (block.type()) {
                    case HEADING -> {
                        float size = headingFontSize(block.headingLevel());
                        cursorY -= Math.max(4.0f, (size - BODY_FONT_SIZE) * 0.6f);
                        writeStyledParagraph(
                                toSegments(block.spans(), true),
                                size,
                                block.quoted() ? QUOTE_INDENT : 0.0f,
                                6.0f,
                                block.quoted());
                    }
                    case PARAGRAPH ->
                            writeStyledParagraph(
                                    toSegments(block.spans(), false),
                                    BODY_FONT_SIZE,
                                    block.quoted() ? QUOTE_INDENT : 0.0f,
                                    6.0f,
                                    block.quoted());
                    case BLOCK_QUOTE ->
                            writeStyledParagraph(
                                    toSegments(block.spans(), false),
                                    BODY_FONT_SIZE,
                                    QUOTE_INDENT,
                                    6.0f,
                                    true);
                    case BULLET_ITEM -> {
                        List<StyledSegment> segments = new ArrayList<>();
                        segments.add(new StyledSegment("• ", true, false, false, false));
                        segments.addAll(toSegments(block.spans(), false));
                        writeStyledParagraph(
                                segments,
                                BODY_FONT_SIZE,
                                LIST_INDENT + (block.quoted() ? QUOTE_INDENT : 0.0f),
                                4.0f,
                                block.quoted());
                    }
                    case ORDERED_ITEM -> {
                        List<StyledSegment> segments = new ArrayList<>();
                        segments.add(
                                new StyledSegment(
                                        block.itemNumber() + ". ", true, false, false, false));
                        segments.addAll(toSegments(block.spans(), false));
                        writeStyledParagraph(
                                segments,
                                BODY_FONT_SIZE,
                                LIST_INDENT + (block.quoted() ? QUOTE_INDENT : 0.0f),
                                4.0f,
                                block.quoted());
                    }
                    case CODE_BLOCK ->
                            writeStyledParagraph(
                                    toSegments(block.spans(), false),
                                    CODE_FONT_SIZE,
                                    10.0f + (block.quoted() ? QUOTE_INDENT : 0.0f),
                                    6.0f,
                                    block.quoted());
                    case THEMATIC_BREAK -> drawHorizontalRule();
                }
            }
        }

        private List<StyledSegment> toSegments(List<InlineSpan> spans, boolean forceBold) {
            List<StyledSegment> segments = new ArrayList<>();
            for (InlineSpan span : spans) {
                segments.add(
                        new StyledSegment(
                                span.text(),
                                forceBold || span.bold(),
                                span.italic(),
                                span.strikethrough(),
                                span.code()));
            }
            return segments;
        }

        private void drawHorizontalRule() throws IOException {
            float lineHeight = 14.0f;
            ensureSpace(lineHeight);
            float lineY = cursorY - 6.0f;
            stream.setLineWidth(0.6f);
            stream.moveTo(PAGE_MARGIN_X, lineY);
            stream.lineTo(PAGE_MARGIN_X + usableWidth, lineY);
            stream.stroke();
            cursorY -= lineHeight;
        }

        private void writeStyledParagraph(
                List<StyledSegment> segments,
                float fontSize,
                float indent,
                float paragraphSpacingAfter,
                boolean drawQuoteBar)
                throws IOException {
            List<List<StyledSegment>> wrappedLines =
                    wrapSegments(segments, fontSize, Math.max(80.0f, usableWidth - indent));
            float lineHeight = fontSize * 1.55f;

            for (List<StyledSegment> line : wrappedLines) {
                ensureSpace(lineHeight);
                float baselineY = cursorY - fontSize;
                if (drawQuoteBar) {
                    stream.setLineWidth(1.5f);
                    stream.moveTo(PAGE_MARGIN_X + 4.0f, baselineY - 2.0f);
                    stream.lineTo(PAGE_MARGIN_X + 4.0f, baselineY + fontSize);
                    stream.stroke();
                }
                float currentX = PAGE_MARGIN_X + indent;
                for (StyledSegment segment : line) {
                    PDFont font = segment.bold() ? fonts.bold() : fonts.regular();
                    String encodable = filterEncodable(font, segment.text());
                    if (encodable.isEmpty()) {
                        continue;
                    }
                    float segmentWidth = stringWidth(font, encodable, fontSize);
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(currentX, baselineY);
                    stream.showText(encodable);
                    stream.endText();

                    if (segment.strikethrough()) {
                        float strikeY = baselineY + (fontSize * 0.32f);
                        stream.setLineWidth(0.7f);
                        stream.moveTo(currentX, strikeY);
                        stream.lineTo(currentX + segmentWidth, strikeY);
                        stream.stroke();
                    }
                    currentX += segmentWidth;
                }
                cursorY -= lineHeight;
            }
            cursorY -= paragraphSpacingAfter;
        }

        private List<List<StyledSegment>> wrapSegments(
                List<StyledSegment> segments, float fontSize, float maxWidth) throws IOException {
            List<List<StyledSegment>> lines = new ArrayList<>();
            List<StyledSegment> currentLine = new ArrayList<>();
            float currentWidth = 0.0f;

            for (StyledSegment segment : segments) {
                PDFont font = segment.bold() ? fonts.bold() : fonts.regular();
                String text = segment.text();
                StringBuilder chunk = new StringBuilder();

                for (int index = 0; index < text.length(); index++) {
                    char character = text.charAt(index);
                    if (character == '\n') {
                        flushChunk(currentLine, chunk, segment);
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = 0.0f;
                        continue;
                    }
                    String candidate = filterEncodable(font, String.valueOf(character));
                    if (candidate.isEmpty()) {
                        continue;
                    }
                    float charWidth = stringWidth(font, candidate, fontSize);
                    if (currentWidth + charWidth > maxWidth && (currentWidth > 0.0f || !chunk.isEmpty())) {
                        flushChunk(currentLine, chunk, segment);
                        lines.add(currentLine);
                        currentLine = new ArrayList<>();
                        currentWidth = 0.0f;
                    }
                    chunk.append(candidate);
                    currentWidth += charWidth;
                }
                flushChunk(currentLine, chunk, segment);
            }
            if (!currentLine.isEmpty() || lines.isEmpty()) {
                lines.add(currentLine);
            }
            return lines;
        }

        private void flushChunk(
                List<StyledSegment> line, StringBuilder chunk, StyledSegment template) {
            if (!chunk.isEmpty()) {
                line.add(
                        new StyledSegment(
                                chunk.toString(),
                                template.bold(),
                                template.italic(),
                                template.strikethrough(),
                                template.code()));
                chunk.setLength(0);
            }
        }

        private String filterEncodable(PDFont font, String input) {
            StringBuilder valid = new StringBuilder(input.length());
            for (int offset = 0; offset < input.length(); ) {
                int codePoint = input.codePointAt(offset);
                String character = new String(Character.toChars(codePoint));
                if (canEncode(font, character)) {
                    valid.append(character);
                } else if (canEncode(font, "?")) {
                    valid.append("?");
                }
                offset += Character.charCount(codePoint);
            }
            return valid.toString();
        }

        private boolean canEncode(PDFont font, String character) {
            try {
                font.encode(character);
                return true;
            } catch (Exception exception) {
                return false;
            }
        }

        private float stringWidth(PDFont font, String text, float fontSize) throws IOException {
            return (font.getStringWidth(text) / 1000.0f) * fontSize;
        }

        @Override
        public void close() throws IOException {
            if (stream != null) {
                stream.close();
            }
        }
    }
}
