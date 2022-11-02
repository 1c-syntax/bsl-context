package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.IContextMethod;
import com.github._1c_syntax.bsl.context.api.IContextMethodSignature;

import java.util.Collections;
import java.util.List;

/**
 * Платформанный контестный метод.
 */
public class PlatformContextMethod implements IContextMethod {
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
    public List<IContextMethodSignature> signatures() {
        return Collections.emptyList(); // TODO: реализовать
    }
}
