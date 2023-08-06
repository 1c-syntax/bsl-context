package com.github._1c_syntax.bsl.context.api;

import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;

import java.util.List;
import java.util.Optional;

/**
 * Поставщик контекста.
 */
public interface ContextProvider {
    List<Context> getContexts();
    Optional<Context> getContextByName(String name);

    PlatformGlobalContext getGlobalContext();

}
