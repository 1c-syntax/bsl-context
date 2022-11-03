package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстный тип.
 */
public interface ContextType extends Context {
    /**
     * Контекстный тип включен в глобальный контекст.
     */
    default boolean includeGlobalContext() {
        return true;
    }

    /**
     * Методы контекста.
     */
    List<ContextMethod> methods();

    /**
     * Свойства контекста.
     */
    List<ContextProperty> properties();
}
