package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;

import java.util.Collection;
import java.util.List;
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
                        c.processRawTypes(contexts);
                    } else if (context instanceof PlatformContextMethod c) {
                        c.processRawTypes(contexts);
                    } else if (context instanceof PlatformContextEvent c) {
                        c.processRawTypes(contexts);
                    } else if (context instanceof PlatformContextConstructor c) {
                        c.processRawTypes(contexts);
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
                    c.processRawTypes(contexts);
                  } else if (o instanceof PlatformContextMethod c) {
                    c.processRawTypes(contexts);
                  } else if (o instanceof PlatformContextEvent c) {
                    c.processRawTypes(contexts);
                  }
                });
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
