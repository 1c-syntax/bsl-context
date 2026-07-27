package com.github._1c_syntax.bsl.context.platform;

import java.util.Map;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import lombok.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Контекстное перечисление платформы.
 * <p>
 * «Страничные» метаданные (описание, доступность, версии, «Пример:»,
 * «См. также:») снимаются с главной страницы перечисления так же, как у
 * {@link PlatformContextType}.
 */
public class PlatformContextEnum implements ContextEnum {
    private final ContextName name;
    private final List<ContextEnumValue> values;
    private ContextName valueType;
    private final String description;
    private final String notes;
    private final List<Availability> availabilities;
    private final String sinceVersion;
    private final String deprecatedSinceVersion;
    private final List<String> examples;
    private final List<String> seeAlso;
    private final List<String> recommendedReplacements;
    /** Путь страницы в HBK — ключ сопоставления ru↔en, см. {@link PlatformContextType}. */
    private final String pagePath;

    /**
     * Полный конструктор; {@code null} у любого «страничного» поля означает
     * «на странице такого блока нет» и превращается в пустую строку / список.
     */
    @Builder
    PlatformContextEnum(ContextName name, List<ContextEnumValue> values, ContextName valueType,
                        String description, String notes, List<Availability> availabilities,
                        String sinceVersion, String deprecatedSinceVersion,
                        List<String> examples, List<String> seeAlso,
                        List<String> recommendedReplacements, String pagePath) {
        this.pagePath = pagePath == null ? "" : pagePath;
        this.name = name;
        this.values = values == null ? List.of() : values;
        this.valueType = valueType;
        this.description = description == null ? "" : description;
        this.notes = notes == null ? "" : notes;
        this.availabilities = availabilities == null ? List.of() : List.copyOf(availabilities);
        this.sinceVersion = sinceVersion == null ? "" : sinceVersion;
        this.deprecatedSinceVersion = deprecatedSinceVersion == null ? "" : deprecatedSinceVersion;
        this.examples = examples == null ? List.of() : List.copyOf(examples);
        this.seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
        this.recommendedReplacements = recommendedReplacements == null
            ? List.of() : List.copyOf(recommendedReplacements);
    }

    public PlatformContextEnum(ContextName name, List<ContextEnumValue> values, ContextName valueType) {
        this(name, values, valueType, "", "", List.of(), "", "", List.of(), List.of(), List.of(), "");
    }

    public PlatformContextEnum(ContextName name, List<ContextEnumValue> values) {
        this(name, values, null);
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public List<ContextEnumValue> values() {
        return List.copyOf(values);
    }

    @Override
    public Optional<ContextName> valueType() {
        return Optional.ofNullable(valueType);
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
    public List<Availability> availabilities() {
        return List.copyOf(availabilities);
    }

    @Override
    public String sinceVersion() {
        return sinceVersion;
    }

    @Override
    public String deprecatedSinceVersion() {
        return deprecatedSinceVersion;
    }

    @Override
    public List<String> examples() {
        return List.copyOf(examples);
    }

    @Override
    public List<String> seeAlso() {
        return List.copyOf(seeAlso);
    }

    @Override
    public List<String> recommendedReplacements() {
        return List.copyOf(recommendedReplacements);
    }

    /** @see PlatformContextType#pagePath() */
    public String pagePath() {
        return pagePath;
    }

    /**
     * Пост-резолв: достраивает en-сторону {@code valueType} по индексу типов.
     * HBK-парсер видит только ru-маркер «Значения этого набора имеют тип X»
     * на главной странице (en-страница парсится отдельно и сейчас не пробрасывает
     * имя сюда), поэтому здесь смотрим уже собранный индекс контекстов и берём
     * у одноимённого типа его {@code alias}. Вызывается из
     * {@link PlatformContextProvider} сразу после {@code buildTypeIndex}.
     * <p>
     * No-op, если {@code valueType} не задан, у него уже есть alias, или
     * type-index не содержит такого имени.
     */
    public void bindBilingualValueType(Map<String, Context> typeIndex) {
        if (valueType == null) {
            return;
        }
        if (!valueType.getAlias().isEmpty()) {
            return;
        }
        var resolved = typeIndex.get(valueType.getName());
        if (resolved == null) {
            return;
        }
        var resolvedName = resolved.name();
        if (resolvedName == null || resolvedName.getAlias().isEmpty()) {
            return;
        }
        valueType = new ContextName(valueType.getName(), resolvedName.getAlias());
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
