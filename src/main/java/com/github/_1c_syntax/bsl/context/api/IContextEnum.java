package com.github._1c_syntax.bsl.context.api;

import java.util.Collections;
import java.util.List;

/**
 * Контекстное перечисление.
 */
public interface IContextEnum extends IContext {
    @Override
    default ContextKind kind() {
        return ContextKind.ENUM;
    }

    default List<IContextEnumValue> values() {
        return Collections.emptyList();
    }
}
