package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Поле таблицы языка запросов ({@code Ссылка}, {@code ПометкаУдаления},
 * {@code <Имя реквизита>} …).
 * <p>
 * Поля-плейсхолдеры ({@code <Имя измерения>}, {@code <Имя реквизита>})
 * описывают целый набор полей, имена которых заданы в конфигурации —
 * у них взведён {@link #isGeneric()}.
 *
 * @see ContextQueryTable#fields()
 */
public interface ContextQueryTableField {

    /**
     * Имя поля.
     */
    ContextName name();

    /**
     * Типы значения поля (блок «Тип:» страницы поля).
     */
    List<Context> types();

    /**
     * Описание поля.
     */
    String description();

    /**
     * Английское описание поля — со страницы того же пути в en-HBK.
     * Пусто, если en-HBK не подгружен или парной страницы нет.
     */
    default String descriptionEn() {
        return "";
    }

    /**
     * Заметка со страницы поля — блок «Примечание:».
     */
    default String notes() {
        return "";
    }

    /**
     * Признак поля-плейсхолдера: имя содержит {@code <…>}, конкретные имена
     * приходят из конфигурации.
     */
    default boolean isGeneric() {
        return ContextNames.isGeneric(name());
    }
}
