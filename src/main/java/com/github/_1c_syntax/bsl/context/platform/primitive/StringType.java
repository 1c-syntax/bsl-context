package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class StringType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Строка", "String");

    @Override
    public ContextName name() {
        return NAME;
    }

}
