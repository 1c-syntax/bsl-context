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
    @lombok.Builder.Default
    private final String sinceVersion = "";
    @lombok.Builder.Default
    private final String deprecatedSinceVersion = "";
    @lombok.Builder.Default
    private final String returnValueDescription = "";
    @lombok.Builder.Default
    private final String notes = "";
    @lombok.Builder.Default
    private final List<String> examples = List.of();
    @lombok.Builder.Default
    private final List<String> seeAlso = List.of();
    @lombok.Builder.Default
    private final List<String> recommendedReplacements = List.of();

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
    public String sinceVersion() {
        return sinceVersion;
    }

    @Override
    public String deprecatedSinceVersion() {
        return deprecatedSinceVersion;
    }

    @Override
    public String returnValueDescription() {
        return returnValueDescription;
    }

    @Override
    public List<String> examples() {
        return List.copyOf(examples);
    }

    @Override
    public List<String> seeAlso() {
        return List.copyOf(seeAlso);
    }

    @Override
    public String notes() {
        return notes;
    }

    @Override
    public List<String> recommendedReplacements() {
        return List.copyOf(recommendedReplacements);
    }

    @Override
    public String toString() {
        return name.toString();
    }

    protected void processRawTypes(java.util.Map<String, Context> typeIndex) {
        for (var raw : rawReturnValues) {
            var resolved = typeIndex.get(raw);
            if (resolved != null) {
                returnValues.add(resolved);
            }
        }
        for (var sig : signatures) {
            ((PlatformContextMethodSignature) sig).processRawTypes(typeIndex);
        }
    }

}
