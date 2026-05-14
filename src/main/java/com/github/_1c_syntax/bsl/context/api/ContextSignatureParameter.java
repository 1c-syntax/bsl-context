package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Параметр контекстной сигнатуры метода.
 */
public interface ContextSignatureParameter {
    /**
     * Имя параметра сигнатуры метода.
     */
    ContextName name();
    /**
     * Обязательное.
     */
    boolean isRequired();
    /**
     * Допустимые типы.
     */
    List<Context> types();

    /**
     * Описание параметра сигнатуры
     */
    String description();

    /**
     * Значение по умолчанию для необязательного параметра, извлечённое
     * из описания (например, {@code Истина}). Пустая строка, если в HBK
     * не указано.
     */
    default String defaultValue() {
        return "";
    }
}
