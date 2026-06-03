package com.github._1c_syntax.bsl.context.api;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

    /**
     * Общий тип элементов набора, если он задан на главной странице enum'а
     * фразой «Значения этого набора имеют тип X» / «Items of the set have
     * type X» (характерно для библиотек-перечислений: {@code БиблиотекаКартинок}
     * → {@code Картинка}, {@code БиблиотекаСтилей} → {@code Стиль},
     * {@code ЦветаСтиля} → {@code Цвет} и т.п.). Для обычных системных
     * перечислений ({@code ВидДвиженияНакопления} и т.п.) этой подсказки на
     * странице нет — значения имеют тип самого перечисления; в этом случае
     * возвращается {@link Optional#empty()}.
     */
    default Optional<ContextName> valueType() {
        return Optional.empty();
    }
}
