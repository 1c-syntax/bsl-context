package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстный тип.
 */
public interface IContextType extends IContext {
    /**
     * Контекстный тип включен в глобальный контекст.
     */
    default boolean includeGlobalContext() {
        return true;
    }

    /**
     * Методы контекста.
     */
    List<IContextMethod> methods();

    /**
     * Свойства контекста.
     */
    List<IContextProperty> properties();
}
