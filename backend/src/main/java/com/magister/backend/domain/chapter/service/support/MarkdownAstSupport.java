package com.magister.backend.domain.chapter.service.support;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 마크다운 원문을 CommonMark AST로 파싱하여 PDF·DOCX 렌더러가 공통으로 사용할 블록·인라인 구조로 변환한다.
 */
@Component
public class MarkdownAstSupport {
    private final Parser parser;

    public MarkdownAstSupport() {
        List<Extension> extensions = List.of(StrikethroughExtension.create());
        this.parser = Parser.builder().extensions(extensions).build();
    }

    public List<MarkdownBlock> parseBlocks(String markdownContent) {
        if (markdownContent == null || markdownContent.isEmpty()) {
            return List.of();
        }
        String normalized = markdownContent.replace("\r\n", "\n").replace('\r', '\n');
        Node document = parser.parse(normalized);
        List<MarkdownBlock> blocks = new ArrayList<>();
        collectBlocks(document, blocks, false);
        return blocks;
    }

    private void collectBlocks(Node parent, List<MarkdownBlock> target, boolean insideQuote) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                List<InlineSpan> spans = new ArrayList<>();
                collectInlineSpans(heading, spans, true, false, false, false);
                target.add(
                        new MarkdownBlock(
                                BlockType.HEADING, heading.getLevel(), 0, insideQuote, spans));
            } else if (child instanceof Paragraph paragraph) {
                List<InlineSpan> spans = new ArrayList<>();
                collectInlineSpans(paragraph, spans, false, false, false, false);
                BlockType type = insideQuote ? BlockType.BLOCK_QUOTE : BlockType.PARAGRAPH;
                target.add(new MarkdownBlock(type, 0, 0, insideQuote, spans));
            } else if (child instanceof BlockQuote blockQuote) {
                collectBlocks(blockQuote, target, true);
            } else if (child instanceof BulletList bulletList) {
                collectBulletList(bulletList, target, insideQuote);
            } else if (child instanceof OrderedList orderedList) {
                collectOrderedList(orderedList, target, insideQuote);
            } else if (child instanceof FencedCodeBlock codeBlock) {
                String literal = stripTrailingNewline(codeBlock.getLiteral());
                target.add(
                        new MarkdownBlock(
                                BlockType.CODE_BLOCK,
                                0,
                                0,
                                insideQuote,
                                List.of(new InlineSpan(literal, false, false, false, true))));
            } else if (child instanceof IndentedCodeBlock indentedCodeBlock) {
                String literal = stripTrailingNewline(indentedCodeBlock.getLiteral());
                target.add(
                        new MarkdownBlock(
                                BlockType.CODE_BLOCK,
                                0,
                                0,
                                insideQuote,
                                List.of(new InlineSpan(literal, false, false, false, true))));
            } else if (child instanceof ThematicBreak) {
                target.add(
                        new MarkdownBlock(
                                BlockType.THEMATIC_BREAK, 0, 0, insideQuote, List.of()));
            } else if (child instanceof HtmlBlock htmlBlock) {
                String literal = stripTrailingNewline(htmlBlock.getLiteral());
                target.add(
                        new MarkdownBlock(
                                BlockType.PARAGRAPH,
                                0,
                                0,
                                insideQuote,
                                List.of(new InlineSpan(literal, false, false, false, false))));
            } else {
                collectBlocks(child, target, insideQuote);
            }
        }
    }

    private void collectBulletList(
            BulletList bulletList, List<MarkdownBlock> target, boolean insideQuote) {
        for (Node item = bulletList.getFirstChild(); item != null; item = item.getNext()) {
            if (item instanceof ListItem listItem) {
                List<InlineSpan> spans = new ArrayList<>();
                collectListItemSpans(listItem, spans);
                target.add(new MarkdownBlock(BlockType.BULLET_ITEM, 0, 0, insideQuote, spans));
            }
        }
    }

    private void collectOrderedList(
            OrderedList orderedList, List<MarkdownBlock> target, boolean insideQuote) {
        int currentNumber =
                orderedList.getMarkerStartNumber() != null
                        ? orderedList.getMarkerStartNumber()
                        : 1;
        for (Node item = orderedList.getFirstChild(); item != null; item = item.getNext()) {
            if (item instanceof ListItem listItem) {
                List<InlineSpan> spans = new ArrayList<>();
                collectListItemSpans(listItem, spans);
                target.add(
                        new MarkdownBlock(
                                BlockType.ORDERED_ITEM, 0, currentNumber, insideQuote, spans));
                currentNumber++;
            }
        }
    }

    private void collectListItemSpans(ListItem listItem, List<InlineSpan> spans) {
        boolean firstBlock = true;
        for (Node child = listItem.getFirstChild(); child != null; child = child.getNext()) {
            if (!firstBlock) {
                spans.add(new InlineSpan("\n", false, false, false, false));
            }
            collectInlineSpans(child, spans, false, false, false, false);
            firstBlock = false;
        }
    }

    private void collectInlineSpans(
            Node parent,
            List<InlineSpan> spans,
            boolean bold,
            boolean italic,
            boolean strikethrough,
            boolean code) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Text textNode) {
                spans.add(
                        new InlineSpan(
                                textNode.getLiteral(), bold, italic, strikethrough, code));
            } else if (node instanceof StrongEmphasis) {
                collectInlineSpans(node, spans, true, italic, strikethrough, code);
            } else if (node instanceof Emphasis) {
                collectInlineSpans(node, spans, bold, true, strikethrough, code);
            } else if (node instanceof Strikethrough) {
                collectInlineSpans(node, spans, bold, italic, true, code);
            } else if (node instanceof Code codeNode) {
                spans.add(
                        new InlineSpan(
                                codeNode.getLiteral(), bold, italic, strikethrough, true));
            } else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) {
                spans.add(new InlineSpan("\n", bold, italic, strikethrough, code));
            } else if (node instanceof HtmlInline htmlInline) {
                spans.add(
                        new InlineSpan(
                                htmlInline.getLiteral(), bold, italic, strikethrough, code));
            } else {
                collectInlineSpans(node, spans, bold, italic, strikethrough, code);
            }
        }
    }

    private String stripTrailingNewline(String text) {
        if (text == null) {
            return "";
        }
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    public enum BlockType {
        HEADING,
        PARAGRAPH,
        BLOCK_QUOTE,
        BULLET_ITEM,
        ORDERED_ITEM,
        CODE_BLOCK,
        THEMATIC_BREAK
    }

    public record InlineSpan(
            String text, boolean bold, boolean italic, boolean strikethrough, boolean code) {}

    public record MarkdownBlock(
            BlockType type,
            int headingLevel,
            int itemNumber,
            boolean quoted,
            List<InlineSpan> spans) {
        public String plainText() {
            StringBuilder builder = new StringBuilder();
            for (InlineSpan span : spans) {
                builder.append(span.text());
            }
            return builder.toString();
        }
    }
}
