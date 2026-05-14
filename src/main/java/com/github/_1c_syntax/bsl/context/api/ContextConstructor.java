package com.github._1c_syntax.bsl.context.api;

import java.util.List;

public interface ContextConstructor {
    /**
     * Имя сигнатуры метода
     */
    ContextName name();

    /**
     * Параметры сигнатуры метода
     */
    List<ContextSignatureParameter> parameters();

    /**
     * Описание сигнатуры метода
     */
    String description();

    /**
     * Версия платформы, начиная с которой доступен конструктор.
     */
    default String sinceVersion() {
        return "";
    }

    /**
     * Версия платформы, начиная с которой конструктор помечен как не рекомендуемый.
     */
    default String deprecatedSinceVersion() {
        return "";
    }

    /**
     * Сырая строка синтаксиса (например, {@code Новый Виджет(<Имя>)}).
     */
    default String syntaxText() {
        return "";
    }

    /**
     * Имена, рекомендованные в качестве замены устаревшего конструктора.
     */
    default List<String> recommendedReplacements() {
        return List.of();
    }
}
