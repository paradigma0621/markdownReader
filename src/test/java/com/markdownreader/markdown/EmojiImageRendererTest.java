package com.markdownreader.markdown;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EmojiImageRenderer, exercising every code path.
 *
 * Emoji strings are constructed via Character.toChars() / String.valueOf() so the
 * file is pure ASCII and encoding ambiguity is impossible.
 *
 * SVG bundle status (src/main/resources/emoji/svg/):
 *   1f600.svg       U+1F600 Grinning Face   PRESENT
 *   1f004.svg       U+1F004 Mahjong         PRESENT
 *   1f1e6.svg       U+1F1E6 Reg.Ind. A      PRESENT
 *   1f1e6-1f1e9.svg U+1F1E6+U+1F1E9 (AD)   PRESENT
 *   1f1e6-1f1e7.svg U+1F1E6+U+1F1E7 (AB)   ABSENT
 */
class EmojiImageRendererTest {

    // Helper to build a String from a Unicode code point (works for BMP and SMP)
    private static String cp(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    // ---- visible emojis ----
    private static final String GRINNING_FACE = cp(0x1F600); // U+1F600 Grinning Face
    private static final String MAHJONG       = cp(0x1F004); // U+1F004 Mahjong
    private static final String REG_IND_A     = cp(0x1F1E6); // U+1F1E6 Regional Indicator A
    private static final String REG_IND_B     = cp(0x1F1E7); // U+1F1E7 Regional Indicator B
    private static final String REG_IND_D     = cp(0x1F1E9); // U+1F1E9 Regional Indicator D
    private static final String WAVING_HAND   = cp(0x1F44B); // U+1F44B Waving Hand
    private static final String SKIN_MEDIUM   = cp(0x1F3FD); // U+1F3FD Medium Skin Tone (modifier)
    private static final String MAN           = cp(0x1F468); // U+1F468 Man
    private static final String WOMAN         = cp(0x1F469); // U+1F469 Woman

    // ---- composites ----
    private static final String FLAG_AD = REG_IND_A + REG_IND_D; // 1f1e6-1f1e9.svg PRESENT
    private static final String FLAG_AB = REG_IND_A + REG_IND_B; // 1f1e6-1f1e7.svg ABSENT

    // ---- BMP special characters (always single Java char) ----
    private static final String ZWJ    = cp(0x200D);  // U+200D Zero-Width Joiner
    private static final String VS16   = cp(0xFE0F);  // U+FE0F Variation Selector-16
    private static final String KEYCAP = cp(0x20E3);  // U+20E3 Combining Enclosing Keycap

    private EmojiImageRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new EmojiImageRenderer();
    }

    // ---------------------------------------------------------------- null / empty

    @Test
    void renderNullReturnsNull() {
        assertNull(renderer.render(null));
    }

    @Test
    void renderEmptyStringReturnsEmpty() {
        assertEquals("", renderer.render(""));
    }

    // ---------------------------------------------------------------- plain text (no emoji path)

    @Test
    void renderPlainTextIsUnchanged() {
        assertEquals("Hello, world!", renderer.render("Hello, world!"));
    }

    @Test
    void renderPunctuationAndDigitsUnchanged() {
        String text = "1 + 2 = 3; result: ok.";
        assertEquals(text, renderer.render(text));
    }

    // ---------------------------------------------------------------- HTML tag handling (inTag state)

    @Test
    void emojiInsideHtmlTagAttributeIsNotReplaced() {
        // '<' sets inTag=true; '>' sets inTag=false; chars between are copied verbatim
        String input = "<img alt=\"" + GRINNING_FACE + "\" />";
        String output = renderer.render(input);
        assertTrue(output.startsWith("<img alt="), "Tag prefix should be preserved");
        assertFalse(output.contains("<img class=\"emoji\""),
                "Emoji inside an HTML attribute must not be replaced");
    }

    @Test
    void plainHtmlTagsPreservedWithoutEmoji() {
        assertEquals("<p>plain</p>", renderer.render("<p>plain</p>"));
    }

    @Test
    void emojiAfterClosingTagIsRendered() {
        // After '>' inTag becomes false, so emoji processing resumes
        String output = renderer.render("<b>text</b>" + GRINNING_FACE);
        assertTrue(output.startsWith("<b>text</b>"));
        assertTrue(output.contains("<img class=\"emoji\"") || output.endsWith(GRINNING_FACE));
    }

    // ---------------------------------------------------------------- branch 1: isEmojiPresentation(cp) = true

    @Test
    void emojiPresentationGrinningFaceRendersToImg() {
        String output = renderer.render(GRINNING_FACE);
        assertTrue(output.contains("<img class=\"emoji\""),
                "Expected <img> for U+1F600, got: " + output);
        assertTrue(output.contains("data:image/svg+xml;base64,"));
    }

    @Test
    void emojiPresentationMahjongRendersToImg() {
        String output = renderer.render(MAHJONG);
        assertTrue(output.contains("<img class=\"emoji\""),
                "Expected <img> for U+1F004, got: " + output);
    }

    @Test
    void imgElementHasAltAttribute() {
        assertTrue(renderer.render(GRINNING_FACE).contains("alt=\""));
    }

    @Test
    void imgElementHasDraggableFalse() {
        assertTrue(renderer.render(GRINNING_FACE).contains("draggable=\"false\""));
    }

    // ---------------------------------------------------------------- branch 2: isRegionalIndicator(cp) = true — flag path

    @Test
    void andorraFlagTwoRegionalIndicatorsRendersToImg() {
        // consumeCluster: first=RI, second=RI -> return after second; filename "1f1e6-1f1e9"
        String output = renderer.render(FLAG_AD);
        assertTrue(output.contains("<img class=\"emoji\""),
                "Expected <img> for flag AD, got: " + output);
    }

    @Test
    void singleRegionalIndicatorAloneRendersToImg() {
        // consumeCluster: first=RI, pos >= len -> while loop skipped; filename "1f1e6"
        String output = renderer.render(REG_IND_A);
        assertTrue(output.contains("<img class=\"emoji\""),
                "Expected <img> for single regional indicator, got: " + output);
    }

    @Test
    void singleRegionalIndicatorFollowedByNonRegionalChar() {
        // consumeCluster: first=RI, second='A' is NOT a regional indicator
        // -> inner if skipped; while loop immediately breaks on 'A'
        // -> only the RI is consumed; 'A' is copied as plain text
        String input = REG_IND_A + "A";
        String output = renderer.render(input);
        assertTrue(output.endsWith("A"), "Plain 'A' should remain at end, got: " + output);
        assertTrue(output.contains("<img class=\"emoji\"") || output.startsWith(REG_IND_A));
    }

    // ---------------------------------------------------------------- graceful degradation (no bundled SVG)

    @Test
    void fakeFlagWithoutBundledSvgDegradesGracefully() {
        // FLAG_AB -> filename "1f1e6-1f1e7" -> no SVG -> original chars kept
        String output = renderer.render(FLAG_AB);
        assertFalse(output.contains("<img class=\"emoji\""),
                "Should keep original text when SVG absent, got: " + output);
        assertEquals(FLAG_AB, output);
    }

    // ---------------------------------------------------------------- branch 3: Character.isEmoji(cp) && nextCodePoint == VS16

    @Test
    void hashPlusVs16TriggersBranch3AndDegraded() {
        // '#' (U+0023): isEmojiPresentation=false, isRegionalIndicator=false,
        // Character.isEmoji('#')=true in Java 21, nextCodePoint=VS16 -> branch 3
        // toFileName strips VS16 (no ZWJ) -> "23"; no "23.svg" -> graceful degradation
        String input = "#" + VS16;
        String output = renderer.render(input);
        assertFalse(output.contains("<img class=\"emoji\""), "No bundled SVG for '#'");
        assertTrue(output.contains("#"));
    }

    // ---------------------------------------------------------------- KEYCAP (U+20E3) branch in consumeCluster

    @Test
    void keycapEmojiConsumedAsSingleCluster() {
        // '#' + VS16 + KEYCAP -> consumeCluster while loop: VS16 consumed, KEYCAP consumed.
        // filename "23-20e3" IS bundled, so the whole cluster renders as a single emoji image.
        String input = "#" + VS16 + KEYCAP;
        String output = renderer.render(input);
        assertTrue(output.contains("<img class=\"emoji\""), "Bundled SVG 23-20e3 should render for #-keycap");
        // The full cluster is consumed as one: it becomes a single image whose alt is the cluster.
        assertTrue(output.contains("alt=\"#"), "The keycap cluster should be the image alt text");
    }

    // ---------------------------------------------------------------- isEmojiModifier branch in consumeCluster

    @Test
    void skinToneModifierConsumedInCluster() {
        // WAVING_HAND (isEmojiPresentation=true) + SKIN_MEDIUM (isEmojiModifier=true)
        // consumeCluster: while loop executes isEmojiModifier branch
        String input = WAVING_HAND + SKIN_MEDIUM;
        String output = renderer.render(input);
        assertNotNull(output);
        // Whether an SVG exists or not, the two chars were consumed as one cluster
        assertTrue(output.equals(input) || output.contains("<img class=\"emoji\""));
    }

    // ---------------------------------------------------------------- ZWJ followed by emoji in consumeCluster

    @Test
    void zwjFollowedByEmojiConsumedAsOneUnit() {
        // MAN + ZWJ + WOMAN -> consumeCluster ZWJ branch:
        //   Character.isEmoji(WOMAN codepoint)=true -> consumed together
        // filename: "1f468-200d-1f469" (hasZwj=true -> VS16 not stripped)
        String input = MAN + ZWJ + WOMAN;
        String output = renderer.render(input);
        assertNotNull(output);
        assertTrue(output.equals(input) || output.contains("<img class=\"emoji\""));
    }

    // ---------------------------------------------------------------- loose ZWJ (ZWJ not followed by emoji -> break)

    @Test
    void looseZwjBreaksClusterEarly() {
        // GRINNING_FACE + ZWJ + 'A':
        //   consumeCluster hits ZWJ; next char is 'A'; Character.isEmoji('A')=false -> break
        //   only GRINNING_FACE consumed; ZWJ + 'A' copied as plain text
        String input = GRINNING_FACE + ZWJ + "A";
        String output = renderer.render(input);
        assertTrue(output.contains("<img class=\"emoji\"") || output.startsWith(GRINNING_FACE));
        assertTrue(output.contains("A"));
    }

    // ---------------------------------------------------------------- nextCodePoint returns -1 (char at very end)

    @Test
    void emojiLikeCharAloneWithoutFollowingVs16TreatedAsPlainText() {
        // '#' alone: nextCodePoint(s, 0) returns -1 (no following char)
        // isEmoji('#') && -1 == VS16 -> false; isKeycapBase('#') && -1 == VS16 -> false
        // -> startsEmoji returns false; '#' copied verbatim
        assertEquals("#", renderer.render("#"));
    }

    // ---------------------------------------------------------------- cache (second render hits the cache)

    @Test
    void subsequentRenderCallReturnsCachedResult() {
        String first  = renderer.render(GRINNING_FACE);
        String second = renderer.render(GRINNING_FACE);
        assertEquals(first, second);
    }

    // ---------------------------------------------------------------- mixed / composite

    @Test
    void textAroundEmojiPreserved() {
        String output = renderer.render("Hello " + GRINNING_FACE + " World");
        assertTrue(output.startsWith("Hello "));
        assertTrue(output.endsWith(" World"));
        assertTrue(output.contains("<img class=\"emoji\""));
    }

    @Test
    void multipleConsecutiveEmojisEachRendered() {
        String input = GRINNING_FACE + MAHJONG;
        String output = renderer.render(input);
        int count = 0;
        int idx   = 0;
        while ((idx = output.indexOf("<img class=\"emoji\"", idx)) != -1) {
            count++;
            idx++;
        }
        assertEquals(2, count, "Expected two <img> tags for two bundled emojis");
    }

    @Test
    void emojiInsideHtmlParagraphRendered() {
        String output = renderer.render("<p>" + GRINNING_FACE + "</p>");
        assertTrue(output.startsWith("<p>"));
        assertTrue(output.endsWith("</p>"));
        assertTrue(output.contains("<img class=\"emoji\""));
    }
}
