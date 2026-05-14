package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Платформенный тип с членами: методы, свойства, события, конструкторы.
 *
 * <p>Признак {@link #isGeneric()} помечает «шаблоны» вроде
 * {@code СправочникСсылка.<Имя справочника>}, конкретизация которых
 * приходит из конфигурации (парсится отдельным проектом {@code MDClasses}).
 */
public interface ContextType extends Context {

    /**
     * Методы типа.
     */
    List<ContextMethod> methods();

    /**
     * Свойства типа.
     */
    List<ContextProperty> properties();

    /**
     * События типа.
     */
    List<ContextEvent> events();

    /**
     * Конструкторы объекта
     */
    List<ContextConstructor> constructors();

}
