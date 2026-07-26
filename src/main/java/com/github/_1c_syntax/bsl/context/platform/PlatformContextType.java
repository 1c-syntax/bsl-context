package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
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
                                  List<ContextProperty> properties,
                                  List<ContextFormParameter> formParameters,
                                  String description) implements ContextType {

    public PlatformContextType {
        if (description == null) description = "";
        // Секция «Параметры формы:» есть только у типов-форм — у остальных
        // билдер это поле не заполняет, и Lombok передаёт сюда null.
        if (formParameters == null) formParameters = List.of();
    }

    @Override
    public ContextKind kind() {
        return ContextKind.TYPE;
    }
    @Override
    public String toString() {
        return name.toString();
    }
}
