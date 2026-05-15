package com.github._1c_syntax.bsl.context.api;

/**
 * Двуязычный шаблон автодополнения для языковой конструкции
 * (плейсхолдеры обозначены как {@code <?>}). Источник — парный
 * {@code .st}-файл в {@code shlang_*.hbk}.
 *
 * @param ru шаблон на русском (например, {@code "Если <?> Тогда\nИначеЕсли\nКонецЕсли;"})
 * @param en шаблон на английском (например, {@code "If <?> Then\nElsIf\nEndIf;"})
 */
public record LanguageKeywordSnippet(String ru, String en) {

    public static final LanguageKeywordSnippet EMPTY = new LanguageKeywordSnippet("", "");

    public LanguageKeywordSnippet {
        if (ru == null) ru = "";
        if (en == null) en = "";
    }

    public boolean isEmpty() {
        return ru.isEmpty() && en.isEmpty();
    }
}
