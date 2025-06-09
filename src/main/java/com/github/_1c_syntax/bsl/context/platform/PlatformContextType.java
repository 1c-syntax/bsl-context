package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextType;
import lombok.Builder;

import java.util.List;

/**
 * Контекстный тип платформы.
 */
@Builder
public record PlatformContextType(ContextName name, List<ContextMethod> methods, List<ContextConstructor> constructors,
                                  List<ContextEvent> events,
                                  List<ContextProperty> properties) implements ContextType {

    @Override
    public ContextKind kind() {
        return ContextKind.TYPE;
    }
    @Override
    public String toString() {
        return name.toString();
    }
}
