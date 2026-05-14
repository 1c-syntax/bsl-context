package com.github._1c_syntax.bsl.context.api;

import java.util.List;

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

    /**
     * Доступность свойства
     */
    List<Availability> availabilities();

    /**
     * Типы, которые может принимать свойство
     */
    List<Context> types();

    /**
     * Описание свойства
     */
    String description();

    /**
     * Версия платформы, начиная с которой доступно свойство (например {@code "8.3.27"}).
     * Пустая строка, если информация о версии отсутствует.
     */
    default String sinceVersion() {
        return "";
    }

    /**
     * Версия платформы, начиная с которой свойство помечено как не рекомендуемое
     * к использованию. Пустая строка, если свойство актуально.
     */
    default String deprecatedSinceVersion() {
        return "";
    }

    /**
     * Признак generic-свойства: имя содержит {@code <…>}-плейсхолдер,
     * который соответствует имени объекта из конфигурации (например,
     * свойство {@code <Имя справочника>} на типе {@code СправочникиМенеджер}).
     */
    default boolean isGeneric() {
        return ContextNames.isGeneric(name());
    }

}
