package com.github._1c_syntax.bsl.context.api;

import java.util.List;
import java.util.Optional;

/**
 * Поставщик контекста.
 */
public interface IContextProvider {
    List<IContext> getContexts();
    Optional<IContext> getContextByName(String name);

    // TODO: события и свойства глобального контекста
}
