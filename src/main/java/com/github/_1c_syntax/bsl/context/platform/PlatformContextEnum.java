package com.github._1c_syntax.bsl.context.platform;

import java.util.Map;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import lombok.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Контекстное перечисление платформы.
 */
@Builder
public class PlatformContextEnum implements ContextEnum {
    private final ContextName name;
    private final List<ContextEnumValue> values;
    private ContextName valueType;

    public PlatformContextEnum(ContextName name, List<ContextEnumValue> values, ContextName valueType) {
        this.name = name;
        this.values = values;
        this.valueType = valueType;
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
