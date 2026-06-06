package com.markdownreader.markdown;

/**
 * A heading extracted from the document, used to build the navigable table of contents.
 *
 * @param level heading level (1 to 6)
 * @param text  visible heading text
 * @param id    corresponding HTML anchor id (for scrolling)
 */
public record Heading(int level, String text, String id) {
}
