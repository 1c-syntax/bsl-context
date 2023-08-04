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

}
