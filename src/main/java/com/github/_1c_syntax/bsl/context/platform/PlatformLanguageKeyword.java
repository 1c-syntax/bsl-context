package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextLanguageKeyword;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import lombok.Builder;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /**
     * Контекстно-зависимые описания body-keyword'ов, заданных на нескольких
     * родительских страницах. Ключ — ru-имя родителя ({@code "Функция"},
     * {@code "Процедура"}, {@code "Если"}, и т.п.), значение — ru-описание
     * этого keyword'а в контексте этого родителя.
     * <p>
     * Например, у {@code Знач} описание различается между {@code Функция}
     * и {@code Процедура} (в первом случае «при выполнении функции…», во
     * втором «при выполнении процедуры…»). Потребитель ({@code LS})
     * обращается к нужной записи по AST-контексту вызова.
     * <p>
     * Если карта пуста или не содержит ключа — fallback на
     * {@link #description()}.
     */
    @Builder.Default
    private final Map<String, String> descriptionByParent = new LinkedHashMap<>();
    /** То же, что {@link #descriptionByParent}, но en-описания. */
    @Builder.Default
    private final Map<String, String> descriptionByParentEn = new LinkedHashMap<>();

    public PlatformLanguageKeyword(ContextName name, LanguageKeywordCategory category,
                                   String description, LanguageKeywordSnippet snippet,
                                   String descriptionEn,
                                   Map<String, String> descriptionByParent,
                                   Map<String, String> descriptionByParentEn) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.snippet = snippet == null ? LanguageKeywordSnippet.EMPTY : snippet;
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
        this.descriptionByParent = descriptionByParent == null ? new LinkedHashMap<>() : new LinkedHashMap<>(descriptionByParent);
        this.descriptionByParentEn = descriptionByParentEn == null ? new LinkedHashMap<>() : new LinkedHashMap<>(descriptionByParentEn);
    }

    public PlatformLanguageKeyword(ContextName name, LanguageKeywordCategory category,
                                   String description, LanguageKeywordSnippet snippet,
                                   String descriptionEn) {
        this(name, category, description, snippet, descriptionEn, null, null);
    }

    public PlatformLanguageKeyword(ContextName name, LanguageKeywordCategory category,
                                   String description, LanguageKeywordSnippet snippet) {
        this(name, category, description, snippet, "", null, null);
    }

    public String descriptionEn() {
        return descriptionEn;
    }

    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
    }

    /**
     * Зарегистрировать ru-описание keyword'а в контексте {@code parentName}.
     * Если ранее под этим ключом было другое описание — оно заменяется.
     */
    public void putDescriptionForParent(String parentName, String ruDescription) {
        if (parentName == null || parentName.isBlank() || ruDescription == null || ruDescription.isBlank()) {
            return;
        }
        descriptionByParent.put(parentName, ruDescription);
    }

    /** Аналог {@link #putDescriptionForParent} для en. */
    public void putDescriptionForParentEn(String parentName, String enDescription) {
        if (parentName == null || parentName.isBlank() || enDescription == null || enDescription.isBlank()) {
            return;
        }
        descriptionByParentEn.put(parentName, enDescription);
    }

    /**
     * @return неизменяемая карта {@code parentName → ru-description}. Ключи —
     *         ru-имена родителей. Если пусто — есть только generic
     *         {@link #description()}.
     */
    public Map<String, String> descriptionByParent() {
        return Collections.unmodifiableMap(descriptionByParent);
    }

    /** То же, что {@link #descriptionByParent()}, но en-описания. */
    public Map<String, String> descriptionByParentEn() {
        return Collections.unmodifiableMap(descriptionByParentEn);
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
