package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.IContextEnumValue;

/**
 * Платнформенное контекстное значение перечисления.
 */
public class PlatformContextEnumValue implements IContextEnumValue {
    private final ContextName name;

    public PlatformContextEnumValue(ContextName name) {
        this.name = name;
    }

    @Override
    public ContextName name() {
        return name;
    }
}
