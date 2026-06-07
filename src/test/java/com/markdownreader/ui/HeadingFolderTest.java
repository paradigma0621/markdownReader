package com.markdownreader.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HeadingFolderTest {

    private HeadingFolder folder;

    @BeforeEach
    void setUp() {
        folder = new HeadingFolder();
    }

    /** Offset of the start of the given line (0-based) in the text. */
    private static int lineStart(String text, int line) {
        int offset = 0;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < line; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    @Test
    void caretNotOnHeadingDoesNothing() {
        String text = "# Title\nplain body";
        assertNull(folder.toggle(text, lineStart(text, 1) + 2)); // caret in "plain body"
        assertFalse(folder.hasFolds());
    }

    @Test
    void headingWithNoBodyCannotBeFolded() {
        String text = "# A\n# B"; // section A has no body lines
        assertNull(folder.toggle(text, 0));
        assertFalse(folder.hasFolds());
    }

    @Test
    void foldHidesBodyAndKeepsExpandedTextUnchanged() {
        String text = "# A\nbody1\nbody2\n# B";
        HeadingFolder.Result r = folder.toggle(text, 0);

        assertNotNull(r);
        assertTrue(folder.hasFolds());
        // The hidden body lines no longer appear in the editor view.
        assertFalse(r.text().contains("body1"));
        assertFalse(r.text().contains("body2"));
        // The heading carries the visible ellipsis marker.
        assertTrue(r.text().contains("# A " + HeadingFolder.ELLIPSIS));
        assertTrue(r.text().contains("# B"));
        // Expanding restores the original document exactly.
        assertEquals(text, folder.expand(r.text()));
    }

    @Test
    void foldStopsAtSameLevelHeading() {
        String text = "## A\nbody\n## B\nother";
        HeadingFolder.Result r = folder.toggle(text, 0);
        assertNotNull(r);
        assertFalse(r.text().contains("body"));
        assertTrue(r.text().contains("## B"));
        assertTrue(r.text().contains("other")); // B's body is untouched
        assertEquals(text, folder.expand(r.text()));
    }

    @Test
    void foldIncludesDeeperSubsections() {
        String text = "# A\nintro\n## A1\nsub\n# B";
        HeadingFolder.Result r = folder.toggle(text, 0);
        assertNotNull(r);
        assertFalse(r.text().contains("intro"));
        assertFalse(r.text().contains("## A1"));
        assertFalse(r.text().contains("sub"));
        assertTrue(r.text().contains("# B"));
        assertEquals(text, folder.expand(r.text()));
    }

    @Test
    void foldRunsToEndOfDocumentWhenNoFollowingHeading() {
        String text = "# A\nl1\nl2\nl3";
        HeadingFolder.Result r = folder.toggle(text, 0);
        assertNotNull(r);
        assertFalse(r.text().contains("l1"));
        assertEquals(text, folder.expand(r.text()));
    }

    @Test
    void toggleTwiceRestoresExactView() {
        String text = "# A\nbody1\nbody2\n# B";
        HeadingFolder.Result folded = folder.toggle(text, 0);
        // Caret is placed on the (still folded) heading line.
        HeadingFolder.Result unfolded = folder.toggle(folded.text(), folded.caret());
        assertNotNull(unfolded);
        assertEquals(text, unfolded.text());
        assertFalse(folder.hasFolds());
    }

    @Test
    void caretLandsOnHeadingLineAfterFold() {
        String text = "intro\n# A\nbody\n# B";
        int caret = lineStart(text, 1); // on "# A"
        HeadingFolder.Result r = folder.toggle(text, caret);
        assertNotNull(r);
        // Caret offset should be at the start of the heading line in the new text.
        int line = 0;
        for (int i = 0; i < r.caret(); i++) {
            if (r.text().charAt(i) == '\n') {
                line++;
            }
        }
        assertEquals(1, line);
    }

    @Test
    void nestedFoldsExpandCorrectly() {
        String text = "# A\nintro\n## A1\nsub1\nsub2\n# B\ntail";
        // Fold the inner section first.
        HeadingFolder.Result inner = folder.toggle(text, lineStart(text, 2)); // "## A1"
        assertNotNull(inner);
        assertFalse(inner.text().contains("sub1"));
        // Now fold the outer section, which contains the already-folded inner one.
        HeadingFolder.Result outer = folder.toggle(inner.text(), 0); // "# A"
        assertNotNull(outer);
        assertFalse(outer.text().contains("## A1"));
        assertFalse(outer.text().contains("intro"));
        assertTrue(outer.text().contains("# B"));
        // Fully expanding the doubly-folded view restores the original document.
        assertEquals(text, folder.expand(outer.text()));
    }

    @Test
    void resetClearsState() {
        folder.toggle("# A\nbody", 0);
        assertTrue(folder.hasFolds());
        folder.reset();
        assertFalse(folder.hasFolds());
    }

    @Test
    void expandHandlesNullAndEmpty() {
        assertEquals("", folder.expand(null));
        assertEquals("", folder.expand(""));
        assertEquals("no markers here", folder.expand("no markers here"));
    }

    @Test
    void foldMarkerIsAPlainEllipsisWithNoHiddenCharacters() {
        String text = "# A\nbody\n# B";
        HeadingFolder.Result r = folder.toggle(text, 0);
        // The folded heading carries exactly " ⋯" — no zero-width or stray characters,
        // so the editor never shows boxes.
        assertEquals("# A " + HeadingFolder.ELLIPSIS + "\n# B", r.text());
        // The preview is fed the view text verbatim (the ellipsis is a real glyph).
        assertEquals(r.text(), folder.displayText(r.text()));
    }

    @Test
    void collapseAllFoldsEveryFoldableSection() {
        String text = "# A\nintro\n## A1\nsub\n# B\ntail";
        HeadingFolder.Result r = folder.collapseAll(text, 0);
        // No body text remains visible.
        assertFalse(r.text().contains("intro"));
        assertFalse(r.text().contains("sub"));
        assertFalse(r.text().contains("tail"));
        // Headings themselves remain (folded).
        assertTrue(folder.displayText(r.text()).contains("# A " + HeadingFolder.ELLIPSIS));
        // Everything still expands back to the original document.
        assertEquals(text, folder.expand(r.text()));
    }

    @Test
    void expandAllRestoresFullDocumentAndClearsState() {
        String text = "# A\nintro\n## A1\nsub\n# B\ntail";
        HeadingFolder.Result collapsed = folder.collapseAll(text, 0);
        assertTrue(folder.hasFolds());
        HeadingFolder.Result expanded = folder.expandAll(collapsed.text(), 0);
        assertEquals(text, expanded.text());
        assertFalse(folder.hasFolds());
    }

    @Test
    void toggleNthTargetsHeadingByDocumentOrder() {
        String text = "# A\na-body\n# B\nb-body\n# C";
        // The 2nd heading (index 1) is "# B".
        HeadingFolder.Result r = folder.toggleNth(text, 1);
        assertNotNull(r);
        assertFalse(r.text().contains("b-body"));
        assertTrue(r.text().contains("a-body")); // A untouched
        assertEquals(text, folder.expand(r.text()));
    }

    // -------------------------------------------------------- expandedLineOf(...)
    // Maps a (visible) line of the folded preview view back to its line in the fully
    // expanded document, used when a double-click leaves a folded read-mode view.

    /** Overwrites the k-th stored hidden body (reflection: exercises defensive null/empty). */
    @SuppressWarnings("unchecked")
    private static void setHiddenBody(HeadingFolder f, int k, String value) {
        try {
            Field field = HeadingFolder.class.getDeclaredField("hiddenBodies");
            field.setAccessible(true);
            ((List<String>) field.get(f)).set(k, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void expandedLineOfNullViewTextReturnsTheClampedRequestedLine() {
        // Null view: nothing to expand, so the requested line is returned (clamped at 0).
        assertEquals(5, folder.expandedLineOf(null, 5));
        assertEquals(0, folder.expandedLineOf(null, -3));
    }

    @Test
    void expandedLineOfFirstAndNegativeLineIsZero() {
        assertEquals(0, folder.expandedLineOf("# A\nbody", 0));
        assertEquals(0, folder.expandedLineOf("# A\nbody", -2));
    }

    @Test
    void expandedLineOfUnfoldedViewIsTheIdentityMapping() {
        // No folds: every visible line keeps its index in the expanded document.
        String view = "# A\nbody1\nbody2";
        assertEquals(2, folder.expandedLineOf(view, 2));
    }

    @Test
    void expandedLineOfShiftsVisibleLinesPastReinsertedHiddenBodies() {
        HeadingFolder.Result r = folder.toggle("# A\nbody1\nbody2\n# B\ntail", 0);
        String view = r.text(); // "# A ⋯\n# B\ntail"
        // View line 1 ("# B") follows the two hidden body lines -> expanded line 3.
        assertEquals(3, folder.expandedLineOf(view, 1));
        // View line 2 ("tail") -> expanded line 4.
        assertEquals(4, folder.expandedLineOf(view, 2));
    }

    @Test
    void expandedLineOfBeyondTheLastLineWalksTheWholeView() {
        HeadingFolder.Result r = folder.toggle("# A\nbody1\nbody2\n# B\ntail", 0);
        // A view line past the end maps to the end of the expanded document (5 lines total).
        assertEquals(5, folder.expandedLineOf(r.text(), 99));
    }

    @Test
    void expandedLineOfIgnoresFoldedMarkersWithoutAStoredBody() {
        // A folded heading marker but no corresponding hidden body (fresh folder state):
        // the marker contributes only its own line, never a body offset.
        assertEquals(2, folder.expandedLineOf("# A " + HeadingFolder.ELLIPSIS + "\nx", 2));
    }

    @Test
    void expandedLineOfTreatsAnEmptyHiddenBodyAsNoExtraLines() {
        // Folding a section whose body is a single blank line stores an empty body string.
        HeadingFolder f = new HeadingFolder();
        HeadingFolder.Result r = f.toggle("# A\n\n# B", 0);
        assertTrue(f.bodies().get(0).isEmpty(), "the stored body is empty");
        // The empty body adds no offset, so "# B" maps straight through.
        assertEquals(1, f.expandedLineOf(r.text(), 1));
    }

    @Test
    void expandedLineOfTreatsANullHiddenBodyAsNoExtraLines() {
        HeadingFolder f = new HeadingFolder();
        HeadingFolder.Result r = f.toggle("# A\nbody\n# B", 0);
        setHiddenBody(f, 0, null); // defensive null body must be skipped, not throw
        assertEquals(2, f.expandedLineOf(r.text(), 2));
    }

    @Test
    void hashesInsideCodeFencesAreNotHeadings() {
        String text = "# Real\nbefore\n```\n# not a heading\n```\nafter";
        HeadingFolder.Result r = folder.toggle(text, 0);
        assertNotNull(r);
        // The whole section (including the code fence) is folded as one body.
        assertFalse(r.text().contains("not a heading"));
        assertEquals(text, folder.expand(r.text()));
        // A caret on the fenced "# not a heading" line must not fold anything.
        int fencedLine = lineStart(text, 3);
        assertNull(folder.toggle(text, fencedLine));
    }
}
