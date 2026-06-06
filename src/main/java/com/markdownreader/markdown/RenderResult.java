package com.markdownreader.markdown;

import java.util.List;

/**
 * Result of rendering a Markdown document.
 *
 * @param html     HTML fragment of the content
 * @param headings headings found, in document order (for the table of contents)
 */
public record RenderResult(String html, List<Heading> headings) {
}
