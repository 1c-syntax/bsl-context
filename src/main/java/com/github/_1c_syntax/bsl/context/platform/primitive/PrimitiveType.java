package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.IContextMethod;
import com.github._1c_syntax.bsl.context.api.IContextProperty;
import com.github._1c_syntax.bsl.context.api.IContextType;

import java.util.Collections;
import java.util.List;

public abstract class PrimitiveType implements IContextType {
    @Override
    public ContextKind kind() {
        return ContextKind.PRIMITIVE_TYPE;
    }

    @Override
    public List<IContextMethod> methods() {
        return Collections.emptyList();
    }

    @Override
    public List<IContextProperty> properties() {
        return Collections.emptyList();
    }
}
