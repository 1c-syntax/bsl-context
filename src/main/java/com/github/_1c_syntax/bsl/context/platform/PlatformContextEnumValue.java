package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import lombok.Builder;

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

    /**
     * Конструктор для обратной совместимости (имя без описания и версионных меток).
     */
    public PlatformContextEnumValue(ContextName name) {
        this(name, "", "", "");
    }

    public PlatformContextEnumValue(ContextName name, String description, String sinceVersion, String deprecatedSinceVersion) {
        this.name = name;
        this.description = description;
        this.sinceVersion = sinceVersion;
        this.deprecatedSinceVersion = deprecatedSinceVersion;
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
    public String toString() {
        return name.toString();
    }
}
