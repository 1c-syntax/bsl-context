package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;

/**
 * Поле таблицы языка запросов. Сырые имена типов из блока «Тип:» резолвятся
 * в {@link Context} через {@code processRawTypes} в конструкторе
 * {@link PlatformQueryContextProvider}.
 */
@Builder
public class PlatformQueryTableField implements ContextQueryTableField {

    private ContextName name;
    @Builder.Default
    private final String description = "";
    /** Заполняется {@link BilingualMerger} с парной en-страницы. */
    private String descriptionEn;
    @Builder.Default
    private final String notes = "";
    @Builder.Default
    private final List<String> rawTypes = List.of();
    private final List<Context> types = new ArrayList<>();
    /**
     * Путь страницы поля в HBK — по нему {@link BilingualMerger} находит
     * парное en-поле (в en-HBK путь тот же, а имя английское).
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
    public String description() {
        return description;
    }

    @Override
    public String notes() {
        return notes;
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
