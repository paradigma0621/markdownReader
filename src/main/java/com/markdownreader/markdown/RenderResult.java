package com.markdownreader.markdown;

import java.util.List;

/**
 * Resultado da renderização de um documento Markdown.
 *
 * @param html     fragmento HTML do conteúdo
 * @param headings títulos encontrados, na ordem do documento (para o sumário)
 */
public record RenderResult(String html, List<Heading> headings) {
}
