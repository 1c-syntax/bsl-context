package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

public final class DateType extends PrimitiveType {
    private static final ContextName NAME = new ContextName("Дата", "Date");

    @Override
    public ContextName name() {
        return NAME;
    }

    @Override
    public String toString() {
        return NAME.toString();
    }
}
