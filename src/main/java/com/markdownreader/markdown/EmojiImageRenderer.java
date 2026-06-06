package com.markdownreader.markdown;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Substitui emojis Unicode no HTML por imagens SVG coloridas (estilo twemoji),
 * embutidas inline como {@code data:} URI a partir de recursos empacotados no
 * próprio aplicativo — portanto <strong>sem dependência de internet</strong>.
 *
 * <p>O motivo de fazer isso em Java — e não com o {@code twemoji.js} dentro do
 * {@code WebView} — é que o motor WebKit/JavaScriptCore embutido no JavaFX não
 * lida de forma confiável com emojis do plano astral (codepoints {@code >= U+10000},
 * que em UTF-16 são pares substitutos): o regex do twemoji simplesmente não os
 * casa. Aqui a String Java está íntegra e iteramos por <em>code points</em>, de
 * modo que astrais e BMP recebem o mesmo tratamento.
 *
 * <p>Os SVGs ficam em {@code /emoji/svg/<codepoints>.svg} no classpath, seguindo
 * a convenção de nomes do twemoji: as sequências são unidas pelos codepoints em
 * hexadecimal separados por {@code '-'}, removendo o seletor de variação
 * {@code U+FE0F} quando não há ZWJ ({@code U+200D}) na sequência. Emojis sem SVG
 * empacotado degradam graciosamente para o próprio caractere de texto.
 */
public final class EmojiImageRenderer {

    private static final int ZWJ = 0x200D;       // zero-width joiner
    private static final int VS16 = 0xFE0F;      // variation selector-16 (apresentação emoji)
    private static final int KEYCAP = 0x20E3;    // combining enclosing keycap

    private static final String RESOURCE_DIR = "/emoji/svg/";

    /** Cache: nome do arquivo (sem extensão) -> data URI, ou {@code ""} se ausente. */
    private final Map<String, String> dataUriCache = new ConcurrentHashMap<>();

    /**
     * Percorre o fragmento HTML e troca emojis por {@code <img class="emoji">},
     * ignorando o conteúdo dentro de tags ({@code <...>}) para não tocar em
     * atributos nem em nomes de elementos.
     *
     * @param html fragmento HTML (pode ser {@code null})
     * @return HTML com emojis convertidos em imagens
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
     * Decide se a sequência iniciada em {@code i} deve ser tratada como emoji.
     * Aceita: emojis com apresentação padrão emoji; emojis "texto" qualificados
     * por {@code U+FE0F}; indicadores regionais (bandeiras); e bases de keycap
     * ({@code 0-9 # *}) seguidas de {@code U+FE0F}.
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
     * Consome uma sequência completa de emoji (base + seletor de variação,
     * modificadores de tom de pele, keycap, par de bandeira e sequências ZWJ).
     *
     * @return índice (em char) imediatamente após a sequência
     */
    private static int consumeCluster(String s, int i) {
        int len = s.length();
        int pos = i;
        int firstCp = s.codePointAt(pos);
        pos += Character.charCount(firstCp);

        // Bandeira: dois indicadores regionais consecutivos.
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
                    break; // ZWJ solto: não engole
                }
            } else {
                break;
            }
        }
        return pos;
    }

    /**
     * Monta o {@code <img>} com o SVG embutido. Se o SVG não estiver empacotado,
     * mantém o caractere de emoji original (degradação graciosa).
     */
    private void appendImage(StringBuilder out, String s, int start, int end) {
        String raw = s.substring(start, end);
        String dataUri = dataUriFor(toFileName(raw));
        if (dataUri.isEmpty()) {
            out.append(raw); // sem asset: mantém o texto
            return;
        }
        out.append("<img class=\"emoji\" draggable=\"false\" alt=\"")
           .append(escapeAttr(raw))
           .append("\" src=\"")
           .append(dataUri)
           .append("\" />");
    }

    /** Carrega o SVG do classpath e devolve um {@code data:} URI base64 (cacheado). */
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
     * Nome de arquivo no estilo twemoji: codepoints em hex unidos por {@code '-'};
     * remove {@code U+FE0F} se a sequência não contiver ZWJ.
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
