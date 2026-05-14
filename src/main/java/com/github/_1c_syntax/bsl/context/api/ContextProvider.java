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
     */
    Optional<Context> getContextByName(String name);

    /**
     * Глобальный контекст платформы (его методы, свойства, события).
     * Может быть {@code null}, если в источнике он отсутствует.
     */
    PlatformGlobalContext getGlobalContext();

}
