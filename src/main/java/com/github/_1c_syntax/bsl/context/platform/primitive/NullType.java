package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class NullType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Null", "Null");

    @Override
    public ContextName name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME.toString();
    }
}
