package com.github._1c_syntax.bsl.context.api;

import java.util.Collections;
import java.util.List;

/**
 * Системное перечисление платформы (например, {@code ВидДвиженияНакопления}).
 * Содержит набор именованных значений ({@link ContextEnumValue}).
 */
public interface ContextEnum extends Context {
    @Override
    default ContextKind kind() {
        return ContextKind.ENUM;
    }

    /**
     * Значения перечисления.
     */
    default List<ContextEnumValue> values() {
        return Collections.emptyList();
    }
}
