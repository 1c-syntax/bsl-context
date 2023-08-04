package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.*;

import java.util.Collections;
import java.util.List;

public abstract class PrimitiveType implements ContextType {
    @Override
    public ContextKind kind() {
        return ContextKind.PRIMITIVE_TYPE;
    }

    @Override
    public List<ContextMethod> methods() {
        return Collections.emptyList();
    }

    @Override
    public List<ContextProperty> properties() {
        return Collections.emptyList();
    }

    @Override
    public List<ContextEvent> events() {
        return Collections.emptyList();
    }
}
