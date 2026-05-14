package com.github._1c_syntax.bsl.context.api;

/**
 * Корневой элемент модели — любая именованная сущность синтакс-помощника:
 * платформенный тип ({@link ContextType}), перечисление ({@link ContextEnum}),
 * глобальный контекст или примитив.
 *
 * <p>Получить набор контекстов можно через {@link ContextProvider}.
 */
public interface Context {
    /**
     * Двухъязычное имя (ru + en).
     */
    ContextName name();

    /**
     * Категория контекста (примитив / тип / перечисление / глобальный контекст).
     */
    ContextKind kind();

    /**
     * Признак generic-типа платформы (имя содержит {@code <…>}-плейсхолдер,
     * который заполняется конкретным именем из конфигурации, например
     * {@code СправочникСсылка.<Имя справочника>}). Конкретные типы
     * приходят отдельно из проекта {@code MDClasses}.
     */
    default boolean isGeneric() {
        return ContextNames.isGeneric(name());
    }
}
