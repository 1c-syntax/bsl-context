package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
import com.github._1c_syntax.bsl.context.api.ContextName;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Платформенный параметр формы.
 * <p>
 * Сырые имена типов из блока «Тип:» страницы параметра хранятся в
 * {@link #rawTypes} и резолвятся в {@link Context}-объекты через
 * {@link #processRawTypes(Map)} один раз в конструкторе
 * {@link PlatformContextProvider}.
 */
@Builder
public class PlatformContextFormParameter implements ContextFormParameter {

    private final ContextName name;
    private final String description;
    private final boolean key;
    @Builder.Default
    private final String sinceVersion = "";
    @Builder.Default
    private final String deprecatedSinceVersion = "";
    @Builder.Default
    private final List<String> recommendedReplacements = List.of();
    @Builder.Default
    private final List<String> rawTypes = List.of();
    @Builder.Default
    private final List<String> seeAlso = List.of();
    private final List<Context> types = new ArrayList<>();

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public List<Context> types() {
        return List.copyOf(types);
    }

    @Override
    public String description() {
        return description == null ? "" : description;
    }

    @Override
    public boolean isKey() {
        return key;
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
    public List<String> recommendedReplacements() {
        return List.copyOf(recommendedReplacements);
    }

    @Override
    public List<String> seeAlso() {
        return List.copyOf(seeAlso);
    }

    @Override
    public String toString() {
        return name.toString();
    }

    /**
     * Резолвит сырые имена типов через переданный индекс. Неизвестные имена
     * игнорируются. Сигнатура совпадает с {@code processRawTypes} у прочих
     * {@code Platform*}-классов — вызывается единообразно в
     * {@link PlatformContextProvider}.
     */
    protected void processRawTypes(Map<String, Context> typeIndex) {
        for (var raw : rawTypes) {
            var resolved = typeIndex.get(raw);
            if (resolved != null) {
                types.add(resolved);
            }
        }
    }
}
