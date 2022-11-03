package com.github._1c_syntax.bsl.context.api;

import java.util.Collections;
import java.util.List;

/**
 * Контекстное перечисление.
 */
public interface ContextEnum extends Context {
    @Override
    default ContextKind kind() {
        return ContextKind.ENUM;
    }

    default List<ContextEnumValue> values() {
        return Collections.emptyList();
    }
}
