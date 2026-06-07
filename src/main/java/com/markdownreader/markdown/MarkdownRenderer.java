package com.markdownreader.markdown;

import com.vladsch.flexmark.ext.anchorlink.AnchorLinkExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.emoji.EmojiExtension;
import com.vladsch.flexmark.ext.footnotes.FootnoteExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.toc.TocExtension;
import com.vladsch.flexmark.ext.typographic.TypographicExtension;
import com.vladsch.flexmark.html.AttributeProvider;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.html.IndependentAttributeProviderFactory;
import com.vladsch.flexmark.html.renderer.AttributablePart;
import com.vladsch.flexmark.html.renderer.LinkResolverContext;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Block;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.util.html.MutableAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Markdown text (GitHub Flavored Markdown) to HTML.
 *
 * <p>Enables: tables, task lists, strikethrough, autolinks,
 * footnotes, heading anchors, emojis, and smart typography.
 */
public final class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        MutableDataSet options = new MutableDataSet();

        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                TaskListExtension.create(),
                StrikethroughExtension.create(),
                AutolinkExtension.create(),
                FootnoteExtension.create(),
                AnchorLinkExtension.create(),
                EmojiExtension.create(),
                TypographicExtension.create(),
                TocExtension.create()
        ));

        // Single line break becomes <br> (GitHub-friendly behavior).
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        // Allows inline HTML in markdown.
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        options.set(HtmlRenderer.RENDER_HEADER_ID, true);

        this.parser = Parser.builder(options).build();
        // Stamp every rendered block element with its source line so the preview can
        // map a clicked/visible element back to the matching line in the editor.
        this.renderer = HtmlRenderer.builder(options)
                .attributeProviderFactory(new SourceLineAttributeProviderFactory())
                .build();
    }

    /**
     * Renders the given markdown, producing the HTML fragment and the list of
     * headings for the table of contents.
     *
     * @param markdown markdown content; {@code null} is treated as empty
     * @return the render result
     */
    public RenderResult render(String markdown) {
        Document document = parser.parse(markdown == null ? "" : markdown);
        String html = renderer.render(document);
        return new RenderResult(html, extractHeadings(document));
    }

    private List<Heading> extractHeadings(Node node) {
        List<Heading> headings = new ArrayList<>();
        collectHeadings(node, headings);
        return headings;
    }

    /**
     * Adds a {@code data-source-line} attribute (0-based start line of the node in the
     * Markdown source) to every block-level element, so the WebView's JavaScript can
     * synchronize the preview with the plain-text editor.
     */
    private static final class SourceLineAttributeProvider implements AttributeProvider {
        @Override
        public void setAttributes(Node node, AttributablePart part, MutableAttributes attributes) {
            if (part == AttributablePart.NODE && node instanceof Block) {
                attributes.replaceValue("data-source-line", String.valueOf(node.getStartLineNumber()));
            }
        }
    }

    /** Factory wiring the {@link SourceLineAttributeProvider} into the {@link HtmlRenderer}. */
    private static final class SourceLineAttributeProviderFactory extends IndependentAttributeProviderFactory {
        @Override
        public AttributeProvider apply(LinkResolverContext context) {
            return new SourceLineAttributeProvider();
        }
    }

    private void collectHeadings(Node node, List<Heading> out) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof com.vladsch.flexmark.ast.Heading h) {
                String text = h.getText().normalizeEOL().trim();
                String id = h.getAnchorRefId();
                if (!text.isEmpty()) {
                    out.add(new Heading(h.getLevel(), text, id == null ? "" : id));
                }
            }
            collectHeadings(child, out);
        }
    }
}
