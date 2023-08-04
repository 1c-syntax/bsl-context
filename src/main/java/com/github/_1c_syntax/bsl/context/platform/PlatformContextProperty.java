package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Платформенное контекстное свойство.
 */
@Builder
public class PlatformContextProperty implements ContextProperty {
    private final ContextName name;
    private final AccessMode accessMode;
    private final List<Availability> availabilities;
    private final String description;
    private final List<Context> types = new ArrayList<>();
    private final List<String> rawTypes;

    @Override
    public List<Availability> availabilities() {
        return List.copyOf(availabilities);
    }

    @Override
    public List<Context> types() {
        return List.copyOf(types);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public AccessMode accessMode() {
        return accessMode;
    }

    @Override
    public String toString() {
        return name.toString();
    }

    protected void processRawTypes(List<Context> contexts) {

        types.addAll(
            rawTypes.stream()
                .map(t -> contexts.stream()
                        .filter(c -> c instanceof ContextType || c instanceof ContextEnum)
                        .filter(c -> c.name().getName().equals(t))
                        .findAny())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList()
        );

    }

}
