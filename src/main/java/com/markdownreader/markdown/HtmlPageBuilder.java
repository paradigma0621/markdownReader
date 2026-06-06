package com.markdownreader.markdown;

import com.markdownreader.ui.Theme;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Embrulha o fragmento HTML gerado pelo {@link MarkdownRenderer} em um
 * documento HTML completo e estilizado, pronto para o {@code WebView}.
 *
 * <p>Inclui o CSS do tema (claro/escuro), realce de sintaxe de código via
 * highlight.js (carregado por CDN — degrada graciosamente para código
 * estilizado, porém monocromático, quando offline) e um botão "voltar ao topo".
 */
public final class HtmlPageBuilder {

    private static final String HLJS_VERSION = "11.9.0";
    private static final String HLJS_BASE =
            "https://cdn.jsdelivr.net/gh/highlightjs/cdn-release@" + HLJS_VERSION + "/build";

    private final String baseCss;

    public HtmlPageBuilder() {
        this.baseCss = readResource("/web/markdown.css");
    }

    /**
     * @param bodyHtml  fragmento HTML do conteúdo
     * @param theme     tema atual
     * @param fontScale fator de zoom da fonte (1.0 = 100%)
     * @return documento HTML completo
     */
    public String build(String bodyHtml, Theme theme, double fontScale) {
        String themeClass = theme == Theme.DARK ? "theme-dark" : "theme-light";
        String hlStyle = theme == Theme.DARK ? "github-dark" : "github";
        long fontPercent = Math.round(fontScale * 100);

        return """
                <!DOCTYPE html>
                <html lang="pt-br" class="%s">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <link rel="stylesheet" href="%s/styles/%s.min.css" />
                    <style>%s</style>
                    <style>:root { font-size: %d%%; }</style>
                </head>
                <body>
                    <article class="markdown-body">
                        %s
                    </article>
                    <button id="top-btn" title="Voltar ao topo" aria-label="Voltar ao topo">&#8593;</button>
                    <script src="%s/highlight.min.js"></script>
                    <script>
                        document.addEventListener('DOMContentLoaded', function () {
                            if (window.hljs) {
                                document.querySelectorAll('pre code').forEach(function (block) {
                                    try { hljs.highlightElement(block); } catch (e) {}
                                });
                            }
                            var btn = document.getElementById('top-btn');
                            window.addEventListener('scroll', function () {
                                btn.classList.toggle('visible', window.scrollY > 320);
                            });
                            btn.addEventListener('click', function () {
                                window.scrollTo({ top: 0, behavior: 'smooth' });
                            });
                        });
                    </script>
                </body>
                </html>
                """.formatted(themeClass, HLJS_BASE, hlStyle, baseCss, fontPercent, bodyHtml, HLJS_BASE);
    }

    private static String readResource(String path) {
        try (InputStream in = HtmlPageBuilder.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Recurso não encontrado no classpath: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler recurso: " + path, e);
        }
    }
}
