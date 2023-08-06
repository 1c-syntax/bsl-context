package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.*;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Платформанный контестный метод.
 */
@Builder
public class PlatformContextMethod implements ContextMethod {
    private final ContextName name;
    private final List<ContextMethodSignature> signatures;
    private final List<Context> returnValues = new ArrayList<>();
    private final String description;
    private final List<Availability> availabilities;

    private final List<String> rawReturnValues;

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public boolean hasReturnValue() {
        return !returnValues.isEmpty();
    }

    @Override
    public List<ContextMethodSignature> signatures() {
        return List.copyOf(signatures);
    }

    @Override
    public List<Context> returnValues() {
        return List.copyOf(returnValues);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<Availability> availabilities() {
        return List.copyOf(availabilities);
    }

    @Override
    public String toString() {
        return name.toString();
    }

    protected void processRawTypes(List<Context> contexts) {

        returnValues.addAll(
                rawReturnValues.stream()
                        .map(t -> contexts.stream()
                                .filter(c -> c instanceof ContextType || c instanceof ContextEnum)
                                .filter(c -> c.name().getName().equals(t))
                                .findAny())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList()
        );

        signatures.forEach(contextMethodSignature -> ((PlatformContextMethodSignature) contextMethodSignature).processRawTypes(contexts));

    }

}
