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
     * Типы возвращаемых значений метода
     */
    List<Context> returnValues();

    /**
     * Сигнатуры метода.
     */
    List<ContextMethodSignature> signatures();

    /**
     * Описание метода.
     */
    String description();

    /**
     * Доступность метода.
     */
    List<Availability> availabilities();

}
