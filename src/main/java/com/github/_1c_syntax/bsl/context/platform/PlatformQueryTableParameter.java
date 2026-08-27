package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableParameter;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Параметр виртуальной таблицы языка запросов. Сырые имена типов из блока
 * «Тип параметра:» резолвятся через {@code processRawTypes} в
 * конструкторе {@link PlatformQueryContextProvider}.
 */
@Builder
public class PlatformQueryTableParameter implements ContextQueryTableParameter {

    private ContextName name;
    @Builder.Default
    private final String description = "";
    /** Заполняется {@link BilingualMerger} с парной en-страницы. */
    private String descriptionEn;
    private final boolean required;
    @Builder.Default
    private final List<String> rawTypes = List.of();
    private final List<Context> types = new ArrayList<>();
    /**
     * Путь страницы параметра в HBK — по нему {@link BilingualMerger} находит
     * парный en-параметр.
     */
    @Builder.Default
    private final String pagePath = "";

    @Override
    public ContextName name() {
        return name;
    }

    /** @see PlatformContextType#pagePath() */
    public String pagePath() {
        return pagePath;
    }

    /** Проставляет en-алиас после bilingual-мерджа. */
    void setName(ContextName name) {
        this.name = name;
    }

    /** Проставляет en-описание после bilingual-мерджа. */
    void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public String descriptionEn() {
        return descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public List<Context> types() {
        return List.copyOf(types);
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return name.toString();
    }

    /**
     * Резолвит сырые имена типов через контекст встроенного языка —
     * вызывается один раз из {@link PlatformQueryContextProvider}.
     */
    void processRawTypes(PlatformQueryContextProvider.UnaryTypeResolver resolver) {
        for (var raw : rawTypes) {
            var resolved = resolver.resolve(raw);
            if (resolved != null) {
                types.add(resolved);
            }
        }
    }
}
