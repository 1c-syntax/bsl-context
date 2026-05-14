package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class UndefinedType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Неопределено", "Undefined");

    @Override
    public ContextName name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME.toString();
    }
}
