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

    /**
     * Параметры формы — содержимое секции «Параметры формы:» страницы типа
     * в синтакс-помощнике. Непустой список только у типов-форм
     * ({@code ФормаКлиентскогоПриложения}, расширения формы для справочника /
     * документа / отчёта / динамического списка, системные формы сохранения
     * и загрузки настроек); у всех остальных типов — пустой.
     *
     * @see ContextFormParameter
     */
    default List<ContextFormParameter> formParameters() {
        return List.of();
    }

    /**
     * Описание типа из синтакс-помощника — содержимое блока «Описание:»
     * на главной HTML-странице типа. Для платформенных типов источник —
     * {@code shcntx_*.hbk}, для примитивов — {@code shlang_*.hbk}
     * (страница {@code def_*}). Если блока «Описание:» на странице нет
     * (редкий случай) — пустая строка.
     * <p>
     * Прочие «страничные» метаданные типа — {@link #availabilities()},
     * {@link #sinceVersion()}, {@link #deprecatedSinceVersion()},
     * {@link #notes()}, {@link #examples()}, {@link #seeAlso()} — объявлены
     * в {@link Context}.
     */
    @Override
    default String description() {
        return "";
    }

}
