package com.markdownreader.markdown;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Replaces Unicode emojis in HTML with colored SVG images (twemoji style),
 * inlined as {@code data:} URIs from resources bundled in the application —
 * therefore <strong>without any internet dependency</strong>.
 *
 * <p>The reason this is done in Java — rather than with {@code twemoji.js} inside
 * the {@code WebView} — is that the WebKit/JavaScriptCore engine embedded in JavaFX
 * does not reliably handle astral-plane emojis (codepoints {@code >= U+10000},
 * which in UTF-16 are surrogate pairs): twemoji's regex simply does not match them.
 * Here the Java String is intact and we iterate by <em>code points</em>, so
 * astral and BMP characters receive the same treatment.
 *
 * <p>SVGs are located at {@code /emoji/svg/<codepoints>.svg} on the classpath,
 * following twemoji's naming convention: sequences are joined by codepoints in
 * hexadecimal separated by {@code '-'}, removing the variation selector
 * {@code U+FE0F} when there is no ZWJ ({@code U+200D}) in the sequence. Emojis
 * without a bundled SVG degrade gracefully to the original text character.
 */
public final class EmojiImageRenderer {

    private static final int ZWJ = 0x200D;       // zero-width joiner
    private static final int VS16 = 0xFE0F;      // variation selector-16 (emoji presentation)
    private static final int KEYCAP = 0x20E3;    // combining enclosing keycap

    private static final String RESOURCE_DIR = "/emoji/svg/";

    /** Cache: file name (without extension) -> data URI, or {@code ""} if absent. */
    private final Map<String, String> dataUriCache = new ConcurrentHashMap<>();

    /**
     * Walks the HTML fragment and replaces emojis with {@code <img class="emoji">},
     * ignoring content inside tags ({@code <...>}) to avoid touching
     * attributes or element names.
     *
     * @param html HTML fragment (may be {@code null})
     * @return HTML with emojis converted to images
     */
    public String render(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        StringBuilder out = new StringBuilder(html.length() + 64);
        boolean inTag = false;
        int i = 0;
        int len = html.length();
        while (i < len) {
            int cp = html.codePointAt(i);
            int cc = Character.charCount(cp);

            if (inTag) {
                out.appendCodePoint(cp);
                if (cp == '>') {
                    inTag = false;
                }
                i += cc;
                continue;
            }

            if (cp == '<') {
                inTag = true;
                out.append('<');
                i += cc;
                continue;
            }

            if (startsEmoji(html, i, cp)) {
                int end = consumeCluster(html, i);
                appendImage(out, html, i, end);
                i = end;
            } else {
                out.appendCodePoint(cp);
                i += cc;
            }
        }
        return out.toString();
    }

    /**
     * Decides whether the sequence starting at {@code i} should be treated as an emoji.
     * Accepts: emojis with default emoji presentation; "text" emojis qualified
     * by {@code U+FE0F}; regional indicators (flags); and keycap bases
     * ({@code 0-9 # *}) followed by {@code U+FE0F}.
     */
    private static boolean startsEmoji(String s, int i, int cp) {
        if (Character.isEmojiPresentation(cp) || isRegionalIndicator(cp)) {
            return true;
        }
        int next = nextCodePoint(s, i);
        if (Character.isEmoji(cp) && next == VS16) {
            return true;
        }
        // keycap: dígito/#/* + FE0F + 20E3
        return isKeycapBase(cp) && next == VS16;
    }

    /**
     * Consumes a complete emoji sequence (base + variation selector,
     * skin tone modifiers, keycap, flag pair, and ZWJ sequences).
     *
     * @return index (in chars) immediately after the sequence
     */
    private static int consumeCluster(String s, int i) {
        int len = s.length();
        int pos = i;
        int firstCp = s.codePointAt(pos);
        pos += Character.charCount(firstCp);

        // Flag: two consecutive regional indicators.
        if (isRegionalIndicator(firstCp) && pos < len) {
            int second = s.codePointAt(pos);
            if (isRegionalIndicator(second)) {
                return pos + Character.charCount(second);
            }
        }

        while (pos < len) {
            int cp = s.codePointAt(pos);
            if (cp == VS16 || cp == KEYCAP || Character.isEmojiModifier(cp)) {
                pos += Character.charCount(cp);
            } else if (cp == ZWJ) {
                int after = pos + Character.charCount(cp);
                if (after < len && Character.isEmoji(s.codePointAt(after))) {
                    pos = after + Character.charCount(s.codePointAt(after));
                } else {
                    break; // loose ZWJ: don't consume
                }
            } else {
                break;
            }
        }
        return pos;
    }

    /**
     * Builds the {@code <img>} with the embedded SVG. If the SVG is not bundled,
     * keeps the original emoji character (graceful degradation).
     */
    private void appendImage(StringBuilder out, String s, int start, int end) {
        String raw = s.substring(start, end);
        String dataUri = dataUriFor(toFileName(raw));
        if (dataUri.isEmpty()) {
            out.append(raw); // no asset: keep as text
            return;
        }
        out.append("<img class=\"emoji\" draggable=\"false\" alt=\"")
           .append(escapeAttr(raw))
           .append("\" src=\"")
           .append(dataUri)
           .append("\" />");
    }

    /** Loads the SVG from the classpath and returns a base64 {@code data:} URI (cached). */
    private String dataUriFor(String fileName) {
        return dataUriCache.computeIfAbsent(fileName, name -> {
            byte[] svg = readResource(RESOURCE_DIR + name + ".svg");
            if (svg == null) {
                return "";
            }
            return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg);
        });
    }

    private static byte[] readResource(String path) {
        try (InputStream in = EmojiImageRenderer.class.getResourceAsStream(path)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * twemoji-style file name: codepoints in hex joined by {@code '-'};
     * removes {@code U+FE0F} if the sequence does not contain ZWJ.
     */
    private static String toFileName(String raw) {
        boolean hasZwj = raw.indexOf(ZWJ) >= 0;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < raw.length()) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == VS16 && !hasZwj) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('-');
            }
            sb.append(Integer.toHexString(cp));
        }
        return sb.toString();
    }

    private static int nextCodePoint(String s, int i) {
        int next = i + Character.charCount(s.codePointAt(i));
        return next < s.length() ? s.codePointAt(next) : -1;
    }

    private static boolean isRegionalIndicator(int cp) {
        return cp >= 0x1F1E6 && cp <= 0x1F1FF;
    }

    private static boolean isKeycapBase(int cp) {
        return cp == '#' || cp == '*' || (cp >= '0' && cp <= '9');
    }

    private static String escapeAttr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }
}
