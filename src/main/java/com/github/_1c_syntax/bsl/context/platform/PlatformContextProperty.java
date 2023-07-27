package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;

/**
 * Платформенное контекстное свойство.
 */
public class PlatformContextProperty implements ContextProperty {
    private final ContextName name;
    private final AccessMode accessMode;

    public PlatformContextProperty(ContextName name, AccessMode accessMode) {
        this.name = name;
        this.accessMode = accessMode;
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
}
