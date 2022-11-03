package com.github._1c_syntax.bsl.context.api;

/**
 * Контекстное свойство.
 */
public interface ContextProperty {
    /**
     * Имя свойства.
     */
    ContextName name();
    /**
     * Режим доступа к свойству.
     */
    AccessMode accessMode();
    // TODO: типы
}
