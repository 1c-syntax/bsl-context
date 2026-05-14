package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class NumberType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Число", "Number");

    @Override
    public ContextName name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME.toString();
    }
}
