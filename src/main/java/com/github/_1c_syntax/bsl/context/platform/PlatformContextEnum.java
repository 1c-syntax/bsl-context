package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import lombok.Builder;

import java.util.List;

/**
 * Контекстное перечисление платформы.
 */
@Builder
public class PlatformContextEnum implements ContextEnum {
    private final ContextName name;
    private final List<ContextEnumValue> values;

    public PlatformContextEnum(ContextName name, List<ContextEnumValue> values) {
        this.name = name;
        this.values = values;
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
    public String toString() {
        return name.toString();
    }
}
