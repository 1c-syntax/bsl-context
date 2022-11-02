package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.IContext;
import com.github._1c_syntax.bsl.context.api.IContextProvider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Поставщик контекста платформы.
 */
public class PlatformContextProvider implements IContextProvider {
    @Override
    public List<IContext> getContexts() {
        return Collections.emptyList(); // TODO
    }

    @Override
    public Optional<IContext> getContextByName(String name) {
        return Optional.empty();
    }
}
