package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextLanguageKeyword;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import lombok.Builder;

/**
 * Реализация {@link ContextLanguageKeyword} для языковых конструкций
 * встроенного языка из {@code shlang_*.hbk}.
 */
@Builder
public class PlatformLanguageKeyword implements ContextLanguageKeyword {

    private final ContextName name;
    private final LanguageKeywordCategory category;
    private final String description;
    @Builder.Default
    private final LanguageKeywordSnippet snippet = LanguageKeywordSnippet.EMPTY;
    /**
     * Английское описание ключевого слова. Заполняется в
     * {@code ShlangParser.parsePages} при наличии парного en-HTML на ту же
     * страницу (shlang_root.hbk). Пустая строка — en-страница не подгружена
     * или описание у неё пусто.
     */
    @Builder.Default
    private String descriptionEn = "";

    public PlatformLanguageKeyword(ContextName name, LanguageKeywordCategory category,
                                   String description, LanguageKeywordSnippet snippet,
                                   String descriptionEn) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.snippet = snippet == null ? LanguageKeywordSnippet.EMPTY : snippet;
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
    }

    public PlatformLanguageKeyword(ContextName name, LanguageKeywordCategory category,
                                   String description, LanguageKeywordSnippet snippet) {
        this(name, category, description, snippet, "");
    }

    public String descriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public LanguageKeywordCategory category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public LanguageKeywordSnippet snippet() {
        return snippet;
    }

    @Override
    public String toString() {
        return name + " [" + category + "]";
    }
}
