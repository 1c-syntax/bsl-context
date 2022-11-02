package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.IContextMethod;
import com.github._1c_syntax.bsl.context.api.IContextProperty;
import com.github._1c_syntax.bsl.context.api.IContextType;

import java.util.List;

/**
 * Контекстный тип платформы.
 */
public class PlatformContextType implements IContextType {
    private final ContextName name;
    private final List<IContextMethod> methods;
    private final List<IContextProperty> properties;
    private final boolean includeGlobalContext;

    public PlatformContextType(ContextName name, List<IContextMethod> methods, List<IContextProperty> properties,
                               boolean includeGlobalContext) {
        this.name = name;
        this.includeGlobalContext = includeGlobalContext;
        this.methods = methods;
        this.properties = properties;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public ContextKind kind() {
        return ContextKind.TYPE;
    }

    @Override
    public boolean includeGlobalContext() {
        return includeGlobalContext;
    }

    @Override
    public List<IContextMethod> methods() {
        return methods;
    }

    @Override
    public List<IContextProperty> properties() {
        return properties;
    }
}
