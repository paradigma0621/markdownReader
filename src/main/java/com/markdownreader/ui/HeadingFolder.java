package com.markdownreader.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Org-mode style folding of Markdown heading sections, shared by the plain-text
 * editor and the rendered preview.
 *
 * <p>Folding is a view concern: the document is shown with some heading sections
 * collapsed, but the underlying text is never altered. When a heading is folded,
 * the lines of its section (everything up to the next heading of the same or higher
 * level) are removed from the visible text and stored here, and a single visible
 * ellipsis is appended to the heading so the user can tell it is collapsed.</p>
 *
 * <p>The only thing added to the text is the visible {@code ⋯} glyph — no hidden or
 * zero-width characters — so the editor never shows stray boxes. Stored bodies are
 * kept in document order and matched to the ellipsis markers by position. Folding a
 * heading that already contains folded sub-sections absorbs them (their bodies are
 * merged into the parent's), which keeps the order mapping unambiguous.</p>
 *
 * <p>This class holds no JavaFX state; it operates purely on strings so it can be
 * unit-tested without a UI.</p>
 */
public final class HeadingFolder {

    /** Visible glyph appended to a folded heading (U+22EF, midline horizontal ellipsis). */
    static final String ELLIPSIS = "⋯";

    /** Matches a Markdown ATX heading line, capturing the leading hashes. */
    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s.*$");
    /** Matches the trailing fold marker on a folded heading line. */
    private static final Pattern MARKER = Pattern.compile("\\s*" + ELLIPSIS + "$");

    /** Hidden section bodies, in document order; the k-th body matches the k-th marker. */
    private final List<String> hiddenBodies = new ArrayList<>();

    /** Outcome of a fold/unfold operation: the new view text and caret position. */
    public record Result(String text, int caret) {
    }

    /** Discards all folding state; call when the editor is loaded with fresh text. */
    public void reset() {
        hiddenBodies.clear();
    }

    /** @return {@code true} if at least one section is currently folded. */
    public boolean hasFolds() {
        return !hiddenBodies.isEmpty();
    }

    /**
     * Folds the section under the caret's heading, or unfolds it if already folded.
     *
     * @return the new view text and caret position, or {@code null} if the caret is
     *         not on a heading (or the section is empty and cannot be folded).
     */
    public Result toggle(String text, int caret) {
        if (text == null) {
            return null;
        }
        String[] lines = splitLines(text);
        int line = lineIndexOf(text, caret);
        if (line >= lines.length) {
            return null;
        }
        boolean[] mask = headingMask(lines);
        if (!mask[line]) {
            return null;
        }
        return isFolded(lines[line]) ? unfold(lines, line, mask) : fold(lines, line, mask);
    }

    /**
     * Folds/unfolds the {@code nth} heading (0-based, in document order) of the view.
     * Used by the preview, where headings are identified by their position.
     */
    public Result toggleNth(String text, int nth) {
        if (text == null || nth < 0) {
            return null;
        }
        String[] lines = splitLines(text);
        boolean[] mask = headingMask(lines);
        int count = 0;
        for (int i = 0; i < lines.length; i++) {
            if (mask[i]) {
                if (count == nth) {
                    return toggle(text, offsetOfLine(Arrays.asList(lines), i));
                }
                count++;
            }
        }
        return null;
    }

    /** Folds every foldable section (outermost first; nested sections are absorbed). */
    public Result collapseAll(String text, int caret) {
        String cur = text == null ? "" : text;
        while (true) {
            String[] lines = splitLines(cur);
            boolean[] mask = headingMask(lines);
            int target = -1;
            for (int i = 0; i < lines.length; i++) {
                if (mask[i] && !isFolded(lines[i]) && sectionEnd(lines, mask, i) > i + 1) {
                    target = i;
                    break;
                }
            }
            if (target < 0) {
                break;
            }
            cur = fold(lines, target, mask).text();
        }
        return new Result(cur, clamp(caret, cur.length()));
    }

    /** Unfolds everything, returning the fully expanded text. */
    public Result expandAll(String text, int caret) {
        String full = expand(text);
        reset();
        return new Result(full, clamp(caret, full.length()));
    }

    /**
     * Reconstructs the full, unfolded document from a (possibly folded) view by
     * re-inserting every stored body in place of its ellipsis marker. Stored bodies
     * are already flat (nested folds were absorbed on fold), so no recursion is needed.
     */
    public String expand(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        String[] lines = splitLines(text);
        boolean[] mask = headingMask(lines);
        List<String> out = new ArrayList<>();
        int k = 0;
        for (int i = 0; i < lines.length; i++) {
            if (mask[i] && isFolded(lines[i])) {
                out.add(stripMarker(lines[i]));
                if (k < hiddenBodies.size()) {
                    String body = hiddenBodies.get(k++);
                    if (body != null && !body.isEmpty()) {
                        out.addAll(Arrays.asList(splitLines(body)));
                    }
                }
            } else {
                out.add(lines[i]);
            }
        }
        return String.join("\n", out);
    }

    /**
     * Maps a line index in the current (possibly folded) view to its line index in the
     * fully expanded document. Visible view lines keep their content but shift down by
     * the number of hidden body lines re-inserted before them when the document is
     * expanded. Used by the preview→editor sync when leaving a folded read-mode view.
     *
     * @param viewText the folded view text the preview was rendered from
     * @param viewLine 0-based line index of a (visible) line in that view
     * @return the 0-based line index of the same line in the expanded document
     */
    public int expandedLineOf(String viewText, int viewLine) {
        if (viewText == null || viewLine <= 0) {
            return Math.max(0, viewLine);
        }
        String[] lines = splitLines(viewText);
        boolean[] mask = headingMask(lines);
        int expanded = 0;
        int k = 0;
        for (int i = 0; i < lines.length && i < viewLine; i++) {
            expanded++; // the visible line itself
            if (mask[i] && isFolded(lines[i]) && k < hiddenBodies.size()) {
                String body = hiddenBodies.get(k++);
                if (body != null && !body.isEmpty()) {
                    expanded += splitLines(body).length;
                }
            }
        }
        return expanded;
    }

    /**
     * The text to feed the Markdown renderer for the preview. The marker is an ordinary
     * visible glyph, so the folded view renders as-is (collapsed headings show "...").
     */
    public String displayText(String text) {
        return text == null ? "" : text;
    }

    // ------------------------------------------------------------- internals

    private Result fold(String[] lines, int idx, boolean[] mask) {
        int end = sectionEnd(lines, mask, idx);
        if (end == idx + 1) {
            return null; // nothing to collapse
        }
        int before = markersBefore(lines, mask, idx);

        // Build the body, absorbing any already-folded sub-sections so the stored body
        // is fully expanded and contains no markers of its own.
        List<String> body = new ArrayList<>();
        int absorbed = 0;
        for (int i = idx + 1; i < end; i++) {
            if (mask[i] && isFolded(lines[i])) {
                body.add(stripMarker(lines[i]));
                String childBody = hiddenBodies.get(before + absorbed);
                absorbed++;
                if (childBody != null && !childBody.isEmpty()) {
                    body.addAll(Arrays.asList(splitLines(childBody)));
                }
            } else {
                body.add(lines[i]);
            }
        }
        for (int c = 0; c < absorbed; c++) {
            hiddenBodies.remove(before);
        }
        hiddenBodies.add(before, String.join("\n", body));

        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i == idx) {
                out.add(lines[i] + " " + ELLIPSIS);
            } else if (i <= idx || i >= end) {
                out.add(lines[i]);
            }
            // lines strictly inside (idx, end) are hidden
        }
        return new Result(String.join("\n", out), offsetOfLine(out, idx));
    }

    private Result unfold(String[] lines, int idx, boolean[] mask) {
        int before = markersBefore(lines, mask, idx);
        String body = before < hiddenBodies.size() ? hiddenBodies.remove(before) : null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (i == idx) {
                out.add(stripMarker(lines[i]));
                if (body != null && !body.isEmpty()) {
                    out.addAll(Arrays.asList(splitLines(body)));
                }
            } else {
                out.add(lines[i]);
            }
        }
        return new Result(String.join("\n", out), offsetOfLine(out, idx));
    }

    /** Number of folded headings before line {@code idx} (their marker index). */
    private static int markersBefore(String[] lines, boolean[] mask, int idx) {
        int count = 0;
        for (int i = 0; i < idx; i++) {
            if (mask[i] && isFolded(lines[i])) {
                count++;
            }
        }
        return count;
    }

    /** Index of the first line past the section that starts at {@code idx}. */
    private int sectionEnd(String[] lines, boolean[] mask, int idx) {
        int level = headingLevel(lines[idx]);
        int j = idx + 1;
        while (j < lines.length && !(mask[j] && headingLevel(lines[j]) <= level)) {
            j++;
        }
        return j;
    }

    /** Per-line flag marking real ATX headings, ignoring fenced code blocks. */
    private static boolean[] headingMask(String[] lines) {
        boolean[] mask = new boolean[lines.length];
        boolean inCode = false;
        for (int i = 0; i < lines.length; i++) {
            String s = stripMarker(lines[i]);
            String trimmed = s.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inCode = !inCode;
                continue;
            }
            mask[i] = !inCode && HEADING.matcher(s).matches();
        }
        return mask;
    }

    private static boolean isFolded(String line) {
        return MARKER.matcher(line).find();
    }

    private static String stripMarker(String line) {
        return MARKER.matcher(line).replaceAll("");
    }

    private static int headingLevel(String line) {
        Matcher m = HEADING.matcher(stripMarker(line));
        return m.matches() ? m.group(1).length() : 0;
    }

    /** Splits preserving empty trailing lines (unlike the default {@link String#split}). */
    private static String[] splitLines(String text) {
        return text.split("\n", -1);
    }

    /** Index of the line that contains the given caret offset. */
    private static int lineIndexOf(String text, int caret) {
        int c = clamp(caret, text.length());
        int line = 0;
        for (int i = 0; i < c; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** Character offset where the line at {@code idx} starts in the joined text. */
    private static int offsetOfLine(List<String> lines, int idx) {
        int offset = 0;
        for (int i = 0; i < idx && i < lines.size(); i++) {
            offset += lines.get(i).length() + 1; // +1 for the '\n'
        }
        return offset;
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    /** Exposed for tests: the stored bodies, in document order. */
    List<String> bodies() {
        return List.copyOf(hiddenBodies);
    }
}
