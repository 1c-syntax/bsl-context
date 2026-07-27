package com.github._1c_syntax.bsl.context.api;

import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;

import java.util.List;
import java.util.Optional;

/**
 * Точка доступа к разобранному контексту платформы — все типы,
 * перечисления, примитивы и глобальный контекст.
 *
 * <p>Создаётся через {@code PlatformContextGrabber.parse()}.
 */
public interface ContextProvider {

    /**
     * Полный список контекстов (без {@link PlatformGlobalContext} — его
     * см. в {@link #getGlobalContext()}).
     */
    List<Context> getContexts();

    /**
     * Поиск контекста по имени (ru или en). Поиск регистронезависимый.
     * <p>
     * Имя в синтакс-помощнике не уникально: у платформы есть омонимы — разные
     * типы с совпадающими именами (например, «Расширение элементов управления,
     * расположенных в форме» существует и для обычных, и для управляемых форм,
     * причём и ru-, и en-имена у них одинаковые). В таком случае метод отдаёт
     * <b>какой-то один</b> из них, и какой именно — не определено. Если важны
     * все — {@link #getContextsByName(String)}.
     */
    Optional<Context> getContextByName(String name);

    /**
     * Все контексты с указанным именем (ru или en), регистронезависимо.
     * В отличие от {@link #getContextByName(String)} не теряет омонимов:
     * различить их можно, например, по {@link Context#sinceVersion()},
     * {@link Context#availabilities()} или составу членов.
     * <p>
     * Пустой список, если ничего не найдено.
     */
    default List<Context> getContextsByName(String name) {
        if (name == null || name.isBlank()) {
            return List.of();
        }
        return getContexts().stream()
            .filter(c -> name.equalsIgnoreCase(c.name().getName())
                || name.equalsIgnoreCase(c.name().getAlias()))
            .toList();
    }

    /**
     * Глобальный контекст платформы (его методы, свойства, события).
     * Может быть {@code null}, если в источнике он отсутствует.
     */
    PlatformGlobalContext getGlobalContext();

}
