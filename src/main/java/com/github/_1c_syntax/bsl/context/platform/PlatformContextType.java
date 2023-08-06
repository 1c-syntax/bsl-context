package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.List;

/**
 * Контекстный тип платформы.
 */
@Builder
public class PlatformContextType implements ContextType {
    private final ContextName name;
    private final List<ContextMethod> methods;
    private final List<ContextEvent> events;
    private final List<ContextProperty> properties;

    public PlatformContextType(ContextName name, List<ContextMethod> methods, List<ContextEvent> events,
                               List<ContextProperty> properties) {
        this.name = name;
        this.methods = methods;
        this.events = events;
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
    public List<ContextMethod> methods() {
        return methods;
    }

    public List<ContextEvent> events() { return events; }

    @Override
    public List<ContextProperty> properties() {
        return properties;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
