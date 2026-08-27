package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryElement;
import com.github._1c_syntax.bsl.context.api.ContextQueryParameter;
import com.github._1c_syntax.bsl.context.api.QueryElementCategory;
import com.github._1c_syntax.bsl.context.api.QueryFunctionGroup;
import lombok.Builder;

import java.util.List;

/**
 * Элемент языка запросов, разобранный из {@code shquery_*.hbk}.
 *
 * @see ContextQueryElement
 */
@Builder
public class PlatformQueryElement implements ContextQueryElement {

    private final ContextName name;
    private final QueryElementCategory category;
    @Builder.Default
    private final String description = "";
    @Builder.Default
    private final List<String> examples = List.of();
    /**
     * en-описание: {@code shquery_root.hbk} парсится тем же парсером, а
     * BilingualMerger к нему не применяется (у элементов языка запросов нет
     * страниц в shcntx), поэтому en-текст хранится прямо здесь.
     */
    @Builder.Default
    private final String descriptionEn = "";
    /** Примеры с той же en-страницы — запрос в них тоже английский. */
    @Builder.Default
    private final List<String> examplesEn = List.of();
    @Builder.Default
    private final List<String> seeAlso = List.of();
    @Builder.Default
    private final List<String> seeAlsoEn = List.of();
    @Builder.Default
    private final QueryFunctionGroup functionGroup = QueryFunctionGroup.NONE;
    /** Конструкция-владелец из ветки «Текст запроса»; {@code null} у верхнеуровневых. */
    private final ContextName parent;
    @Builder.Default
    private final List<ContextName> children = List.of();
    @Builder.Default
    private final String syntaxRule = "";
    @Builder.Default
    private final List<ContextQueryParameter> parameters = List.of();
    /** Имя записи страницы в контейнере — ключ сопоставления ru/en. */
    @Builder.Default
    private final String pagePath = "";

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public QueryElementCategory category() {
        return category;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<String> examples() {
        return List.copyOf(examples);
    }

    @Override
    public String descriptionEn() {
        return descriptionEn;
    }

    @Override
    public List<String> examplesEn() {
        return List.copyOf(examplesEn);
    }

    @Override
    public List<String> seeAlso() {
        return List.copyOf(seeAlso);
    }

    @Override
    public List<String> seeAlsoEn() {
        return List.copyOf(seeAlsoEn);
    }

    @Override
    public QueryFunctionGroup functionGroup() {
        return functionGroup;
    }

    @Override
    public ContextName parent() {
        return parent;
    }

    @Override
    public List<ContextName> children() {
        return List.copyOf(children);
    }

    @Override
    public String syntaxRule() {
        return syntaxRule;
    }

    @Override
    public List<ContextQueryParameter> parameters() {
        return List.copyOf(parameters);
    }

    /** Путь страницы в контейнере. */
    public String pagePath() {
        return pagePath;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
