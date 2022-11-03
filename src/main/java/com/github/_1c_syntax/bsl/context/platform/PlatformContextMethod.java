package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;

import java.util.Collections;
import java.util.List;

/**
 * Платформанный контестный метод.
 */
public class PlatformContextMethod implements ContextMethod {
    private final ContextName name;
    private final boolean hasReturnValue;

    public PlatformContextMethod(ContextName name, boolean hasReturnValue) {
        this.name = name;
        this.hasReturnValue = hasReturnValue;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public boolean hasReturnValue() {
        return hasReturnValue;
    }

    @Override
    public List<ContextMethodSignature> signatures() {
        return Collections.emptyList(); // TODO: реализовать
    }
}
