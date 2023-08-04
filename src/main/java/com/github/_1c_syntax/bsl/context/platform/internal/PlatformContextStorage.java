package com.github._1c_syntax.bsl.context.platform.internal;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Хранилище контекста платформы.
 */
public class PlatformContextStorage {
    private final List<Context> contexts;
    private final Map<String, Context> contextsByNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public PlatformContextStorage(List<Context> contexts) {
        this.contexts = contexts;

        contexts.forEach(context -> {
            contextsByNames.put(context.name().getName(), context);
            contextsByNames.put(context.name().getAlias(), context);
        });
    }

    public Optional<Context> getContextByName(String name) {
        return Optional.ofNullable(contextsByNames.getOrDefault(name, null));
    }

    public Optional<Context> getContextByName(ContextName name) {
        var context = contextsByNames.getOrDefault(name.getName(),
                contextsByNames.getOrDefault(name.getAlias(), null));

        return Optional.ofNullable(context);
    }

    public List<Context> getContexts() {
        return List.copyOf(contexts);
    }
}
