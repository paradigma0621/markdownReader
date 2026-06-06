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
 * <p>Tudo é embutido inline a partir de recursos empacotados no aplicativo, de
 * modo que a renderização funciona <strong>100% offline</strong>: o CSS do tema
 * (claro/escuro), o realce de sintaxe de código via highlight.js (script + tema)
 * e os emojis convertidos em imagens SVG coloridas (twemoji) pelo
 * {@link EmojiImageRenderer}.
 *
 * <p>O highlight.js é embutido em vez de carregado por CDN porque o WebView do
 * JavaFX pode estar offline; os emojis são convertidos no lado Java porque o
 * WebKit embutido não renderiza fontes de emoji coloridas nem casa emojis
 * astrais via JS.
 */
public final class HtmlPageBuilder {

    // highlight.js v11.9.0 — script e temas empacotados em /web/hljs/.

    private final String baseCss;
    private final String hljsJs;
    private final String hlThemeLight;
    private final String hlThemeDark;
    private final EmojiImageRenderer emojiRenderer = new EmojiImageRenderer();

    public HtmlPageBuilder() {
        this.baseCss = readResource("/web/markdown.css");
        this.hljsJs = readResource("/web/hljs/highlight.min.js");
        this.hlThemeLight = readResource("/web/hljs/styles/github.min.css");
        this.hlThemeDark = readResource("/web/hljs/styles/github-dark.min.css");
    }

    /**
     * @param bodyHtml  fragmento HTML do conteúdo
     * @param theme     tema atual
     * @param fontScale fator de zoom da fonte (1.0 = 100%)
     * @return documento HTML completo
     */
    public String build(String bodyHtml, Theme theme, double fontScale) {
        String themeClass = theme == Theme.DARK ? "theme-dark" : "theme-light";
        String hlTheme = theme == Theme.DARK ? hlThemeDark : hlThemeLight;
        long fontPercent = Math.round(fontScale * 100);
        String body = emojiRenderer.render(bodyHtml);

        return """
                <!DOCTYPE html>
                <html lang="pt-br" class="%s">
                <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <style>%s</style>
                    <style>%s</style>
                    <style>:root { font-size: %d%%; }</style>
                </head>
                <body>
                    <article class="markdown-body">
                        %s
                    </article>
                    <button id="top-btn" title="Voltar ao topo" aria-label="Voltar ao topo">&#8593;</button>
                    <script>%s</script>
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
                """.formatted(themeClass, hlTheme, baseCss, fontPercent, body, hljsJs);
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
