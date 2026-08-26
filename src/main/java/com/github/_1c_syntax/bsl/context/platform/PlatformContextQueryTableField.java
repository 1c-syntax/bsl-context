package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import lombok.Builder;

/**
 * Платформенное поле таблицы языка запросов.
 */
public class PlatformContextQueryTableField implements ContextQueryTableField {
    private final ContextName name;
    private final String rawValueType;
    private final String description;
    private final String notes;

    /**
     * {@code null} у любого «страничного» поля означает «на странице такого
     * блока нет» и превращается в пустую строку.
     */
    @Builder
    PlatformContextQueryTableField(ContextName name, String rawValueType, String description, String notes) {
        this.name = name;
        this.rawValueType = rawValueType == null ? "" : rawValueType;
        this.description = description == null ? "" : description;
        this.notes = notes == null ? "" : notes;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public String rawValueType() {
        return rawValueType;
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
}
