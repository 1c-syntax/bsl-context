package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Контекстный метод.
 */
public interface IContextMethod {
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
    List<IContextMethodSignature> signatures();
}
