package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class BooleanType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Булево", "Boolean");

    @Override
    public ContextName name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME.toString();
    }
}
