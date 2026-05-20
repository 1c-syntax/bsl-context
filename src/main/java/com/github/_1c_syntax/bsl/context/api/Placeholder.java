package com.github._1c_syntax.bsl.context.api;

/**
 * Структурное описание одного generic-плейсхолдера в имени контекста.
 * <p>
 * Для строки {@code "СправочникСсылка.<Имя справочника>"} получается
 * {@link Placeholder} с {@code name = "Имя справочника"}, {@code start = 17}
 * (позиция символа {@code <}), {@code end = 34} (позиция СЛЕДУЮЩАЯ ПОСЛЕ
 * {@code >}, то есть half-open interval {@code [start, end)} покрывает
 * текст {@code "<Имя справочника>"}).
 * <p>
 * Структурное представление позволяет потребителям (например, LS-серверам)
 * выполнять подмену placeholder'ов по позиции, без повторного парсинга
 * угловых скобок.
 *
 * @param name  имя placeholder'а без угловых скобок
 * @param start позиция {@code <} в имени (включительно)
 * @param end   позиция СЛЕДУЮЩАЯ ПОСЛЕ {@code >} (исключительно)
 */
public record Placeholder(String name, int start, int end) {
}
