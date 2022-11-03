package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;

import java.util.List;

/**
 * Контекстное перечисление платформы.
 */
public class PlatformContextEnum implements ContextEnum {
    private final ContextName name;
    private final ContextKind kind;
    private final List<ContextEnumValue> values;

    public PlatformContextEnum(ContextName name, ContextKind kind, List<ContextEnumValue> values) {
        this.name = name;
        this.kind = kind;
        this.values = values;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public ContextKind kind() {
        return kind;
    }

    @Override
    public List<ContextEnumValue> values() {
        return values;
    }
}
