package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.IContextEnum;
import com.github._1c_syntax.bsl.context.api.IContextEnumValue;

import java.util.List;

/**
 * Контекстное перечисление платформы.
 */
public class PlatformContextEnum implements IContextEnum {
    private final ContextName name;
    private final ContextKind kind;
    private final List<IContextEnumValue> values;

    public PlatformContextEnum(ContextName name, ContextKind kind, List<IContextEnumValue> values) {
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
    public List<IContextEnumValue> values() {
        return values;
    }
}
