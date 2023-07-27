package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextType;
import lombok.Builder;

import java.util.List;

/**
 * Контекстный тип платформы.
 */
@Builder
public class PlatformContextType implements ContextType {
    private final ContextName name;
    private final List<ContextMethod> methods;
    private final List<ContextProperty> properties;
    private final boolean includeGlobalContext;

    public PlatformContextType(ContextName name, List<ContextMethod> methods, List<ContextProperty> properties,
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
    public List<ContextMethod> methods() {
        return methods;
    }

    @Override
    public List<ContextProperty> properties() {
        return properties;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
