package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextQueryParameter;
import lombok.Builder;

import java.util.List;

/**
 * Параметр функции языка запросов. Собирается из прозы на странице справки —
 * см. {@link com.github._1c_syntax.bsl.context.platform.hbk.ShqueryParser}.
 */
@Builder
public class PlatformQueryParameter implements ContextQueryParameter {

    private final int position;
    @Builder.Default
    private final String description = "";
    /** Заполняется парсером с парной en-страницы. */
    private String descriptionEn;
    @Builder.Default
    private final List<String> types = List.of();
    private final boolean optional;

    @Override
    public int position() {
        return position;
    }

    @Override
    public String description() {
        return description;
    }

    /** Проставляет en-описание после разбора парной страницы. */
    public void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn;
    }

    @Override
    public String descriptionEn() {
        return descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public List<String> types() {
        return List.copyOf(types);
    }

    @Override
    public boolean isOptional() {
        return optional;
    }

    @Override
    public String toString() {
        return position + ": " + description;
    }
}
