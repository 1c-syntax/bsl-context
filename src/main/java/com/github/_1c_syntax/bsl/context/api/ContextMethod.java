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

    /**
     * Версия платформы, начиная с которой доступен метод (например {@code "8.3.27"}).
     * Пустая строка, если информация о версии отсутствует.
     */
    default String sinceVersion() {
        return "";
    }

    /**
     * Версия платформы, начиная с которой метод помечен как не рекомендуемый
     * к использованию. Пустая строка, если метод актуален.
     */
    default String deprecatedSinceVersion() {
        return "";
    }

    /**
     * Признак generic-метода (имя содержит {@code <…>}-плейсхолдер).
     */
    default boolean isGeneric() {
        return ContextNames.isGeneric(name());
    }

    /**
     * Дополнительное описание возвращаемого значения (текст после строки
     * с типом в секции «Возвращаемое значение:»). Пусто, если такого
     * описания нет.
     */
    default String returnValueDescription() {
        return "";
    }

    /**
     * Примеры использования метода (содержимое блоков «Пример:» в синтакс-помощнике)
     * как plaintext, без HTML-разметки. Пустой список, если примеров нет.
     */
    default List<String> examples() {
        return List.of();
    }

    /**
     * Список имён связанных типов/методов из секции «См. также:».
     * Пустой список, если ссылок нет.
     */
    default List<String> seeAlso() {
        return List.of();
    }

    /**
     * Текст секции «Замечание:» — дополнительные предупреждения о поведении
     * метода (например, про безопасный режим). Пусто, если секции нет.
     */
    default String notes() {
        return "";
    }

    /**
     * Имена, рекомендованные в качестве замены устаревшего метода
     * (содержимое блока {@code <div class="__DEPRECATED_SHOW_STYLE__">} →
     * «Рекомендуется использовать:» + список ссылок). Пустой, если метод
     * не deprecated или замена не указана.
     */
    default List<String> recommendedReplacements() {
        return List.of();
    }

}
