package com.github._1c_syntax.bsl.context.api;

/**
 * Значение перечисления (один из членов {@link ContextEnum}).
 * Например, для {@code ВидДвиженияНакопления} это {@code Приход} / {@code Расход}.
 */
public interface ContextEnumValue {

    /**
     * Имя значения (ru + en).
     */
    ContextName name();

    /**
     * Описание значения из синтакс-помощника. Пустая строка, если не задано.
     */
    default String description() {
        return "";
    }

    /**
     * Версия платформы, начиная с которой доступно значение.
     */
    default String sinceVersion() {
        return "";
    }

    /**
     * Версия платформы, начиная с которой значение не рекомендуется к использованию.
     */
    default String deprecatedSinceVersion() {
        return "";
    }
}
