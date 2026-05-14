package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import lombok.Builder;

import java.util.List;

/**
 * Платформенное значение перечисления.
 */
@Builder
public class PlatformContextEnumValue implements ContextEnumValue {
    private final ContextName name;
    @lombok.Builder.Default
    private final String description = "";
    @lombok.Builder.Default
    private final String sinceVersion = "";
    @lombok.Builder.Default
    private final String deprecatedSinceVersion = "";
    @lombok.Builder.Default
    private final List<String> recommendedReplacements = List.of();

    /**
     * Конструктор для обратной совместимости (имя без описания и версионных меток).
     */
    public PlatformContextEnumValue(ContextName name) {
        this(name, "", "", "", List.of());
    }

    public PlatformContextEnumValue(ContextName name, String description,
                                    String sinceVersion, String deprecatedSinceVersion) {
        this(name, description, sinceVersion, deprecatedSinceVersion, List.of());
    }

    public PlatformContextEnumValue(ContextName name, String description,
                                    String sinceVersion, String deprecatedSinceVersion,
                                    List<String> recommendedReplacements) {
        this.name = name;
        this.description = description;
        this.sinceVersion = sinceVersion;
        this.deprecatedSinceVersion = deprecatedSinceVersion;
        this.recommendedReplacements = recommendedReplacements;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public String description() {
        return description;
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
    public String toString() {
        return name.toString();
    }
}
