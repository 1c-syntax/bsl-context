package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстный тип.
 */
public interface ContextType extends Context {

    /**
     * Методы типа.
     */
    List<ContextMethod> methods();

    /**
     * Свойства типа.
     */
    List<ContextProperty> properties();

    /**
     * События типа.
     */
    List<ContextEvent> events();

}
