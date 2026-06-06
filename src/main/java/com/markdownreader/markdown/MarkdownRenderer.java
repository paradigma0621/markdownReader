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
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

import java.util.ArrayList;
import java.util.List;

/**
 * Converte texto Markdown (GitHub Flavored Markdown) em HTML.
 *
 * <p>Habilita: tabelas, listas de tarefas, strikethrough, autolinks,
 * notas de rodapé, âncoras em títulos, emojis e tipografia inteligente.
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

        // Quebra simples de linha vira <br> (comportamento estilo GitHub-ish amigável).
        options.set(HtmlRenderer.SOFT_BREAK, "<br />\n");
        // Permite HTML embutido no markdown.
        options.set(HtmlRenderer.GENERATE_HEADER_ID, true);
        options.set(HtmlRenderer.RENDER_HEADER_ID, true);

        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
    }

    /**
     * Renderiza o markdown informado, produzindo o fragmento HTML e a lista de
     * títulos para o sumário.
     *
     * @param markdown conteúdo markdown; {@code null} é tratado como vazio
     * @return resultado da renderização
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
