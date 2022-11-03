package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстный метод.
 */
public interface ContextMethod {
    /**
     * Имя метода.
     */
    ContextName name();
    /**
     * Признак того, что метод возвращает значение.
     */
    boolean hasReturnValue();
    /**
     * Сигнатуры метода.
     */
    List<ContextMethodSignature> signatures();
}
