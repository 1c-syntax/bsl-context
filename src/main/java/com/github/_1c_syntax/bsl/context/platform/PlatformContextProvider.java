package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Поставщик контекста платформы.
 */
public class PlatformContextProvider implements ContextProvider {
    private final PlatformContextStorage storage;

    public PlatformContextProvider(PlatformContextStorage storage) {

        this.storage = storage;

        var contexts = getContexts();
        // Индекс «ru-имя → Context» для O(1)-резолва сырых типов вместо
        // линейного поиска по всем контекстам на каждое имя.
        // Только ContextType / ContextEnum — другие как тип не появляются.
        var typeIndex = buildTypeIndex(contexts);

        contexts.stream()
                .parallel()
                .filter(context -> context instanceof PlatformContextType)
                .flatMap(context -> {
                    var c = (PlatformContextType) context;

                    return
                        Stream.of(
                            c.properties().stream(),
                            c.methods().stream(),
                            c.events().stream(),
                            c.constructors().stream()
                            );
                })
            .flatMap(Function.identity())
                .forEach(context -> {
                    if (context instanceof PlatformContextProperty c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextMethod c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextEvent c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextConstructor c) {
                        c.processRawTypes(typeIndex);
                    }
                });

        var globalContext = getGlobalContext();
        if (globalContext == null) {
            return;
        }

        Stream.of(globalContext.properties(), globalContext.methods(),
                globalContext.applicationEvents(), globalContext.externalConnectionModuleEvents(),
                globalContext.sessionModuleEvents(), globalContext.ordinaryApplicationEvents())
                .parallel()
                .flatMap(Collection::stream)
                .forEach(o -> {
                  if (o instanceof PlatformContextProperty c) {
                    c.processRawTypes(typeIndex);
                  } else if (o instanceof PlatformContextMethod c) {
                    c.processRawTypes(typeIndex);
                  } else if (o instanceof PlatformContextEvent c) {
                    c.processRawTypes(typeIndex);
                  }
                });
    }

    private static Map<String, Context> buildTypeIndex(List<Context> contexts) {
        var map = new HashMap<String, Context>(contexts.size() * 2);
        for (Context c : contexts) {
            if (c instanceof ContextType || c instanceof ContextEnum) {
                map.putIfAbsent(c.name().getName(), c);
                map.putIfAbsent(c.name().getAlias(), c);
            }
        }
        return map;
    }

    @Override
    public List<Context> getContexts() {
        return storage.getContexts();
    }

    @Override
    public Optional<Context> getContextByName(String name) {
        return storage.getContextByName(name);
    }

    @Override
    public PlatformGlobalContext getGlobalContext() {
      return storage.getGlobalContext();
    }

}
