package com.markdownreader.markdown;

/**
 * Um título extraído do documento, usado para montar o sumário navegável.
 *
 * @param level texto/nível (1 a 6) do título
 * @param text  texto visível do título
 * @param id    id da âncora HTML correspondente (para rolagem)
 */
public record Heading(int level, String text, String id) {
}
