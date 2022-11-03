package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;

import java.util.List;
import java.util.Optional;

/**
 * Поставщик контекста платформы.
 */
public class PlatformContextProvider implements ContextProvider {
    private final PlatformContextStorage storage;

    public PlatformContextProvider(PlatformContextStorage storage) {
        this.storage = storage;
    }

    @Override
    public List<Context> getContexts() {
        return storage.getContexts();
    }

    @Override
    public Optional<Context> getContextByName(String name) {
        return storage.getContextByName(name);
    }
}
