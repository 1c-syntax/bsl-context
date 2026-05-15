package com.github._1c_syntax.bsl.context.api;

/**
 * Категория контекста.
 *
 * <ul>
 *   <li>{@link #PRIMITIVE_TYPE} — встроенные примитивы языка
 *       (Строка, Число, Дата, Булево, Тип, Произвольный, Неопределено, Null).</li>
 *   <li>{@link #TYPE} — платформенный тип со свойствами/методами/событиями/
 *       конструкторами.</li>
 *   <li>{@link #ENUM} — системное перечисление платформы и его значения.</li>
 *   <li>{@link #GLOBAL_CONTEXT} — глобальный контекст: top-level методы,
 *       свойства, события.</li>
 *   <li>{@link #LANGUAGE_KEYWORD} — языковая конструкция встроенного языка:
 *       литерал ({@code Истина}, {@code Ложь}), оператор ({@code Если},
 *       {@code Для}), объявление ({@code Процедура}, {@code Функция}),
 *       директива компиляции ({@code &НаКлиенте}), аннотация
 *       ({@code &Перед}), инструкция препроцессора ({@code #Если}).
 *       Приходит из {@code shlang_*.hbk}.</li>
 * </ul>
 */
public enum ContextKind {
    PRIMITIVE_TYPE, TYPE, ENUM, GLOBAL_CONTEXT, LANGUAGE_KEYWORD
}
