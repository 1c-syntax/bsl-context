package com.github._1c_syntax.bsl.context.api;

/**
 * Вид функции языка запросов — подраздел ветки «Функции» в оглавлении
 * {@code shquery_*.hbk}. У элементов с категорией, отличной от
 * {@link QueryElementCategory#FUNCTION}, — {@link #NONE}.
 *
 * @see ContextQueryElement#functionGroup()
 */
public enum QueryFunctionGroup {
    /** {@code ПОДСТРОКА}, {@code ЛЕВ}, {@code СТРДЛИНА}, {@code СОКРЛП}. */
    STRING,
    /** {@code ГОД}, {@code РАЗНОСТЬДАТ}, {@code НАЧАЛОПЕРИОДА}, {@code ДОБАВИТЬКДАТЕ}. */
    DATE,
    /** {@code ОКР}, {@code ЦЕЛ}, {@code SIN}, {@code LOG}. */
    MATH,
    /** {@code СУММА}, {@code СРЕДНЕЕ}, {@code КОЛИЧЕСТВО}, {@code МИНИМУМ}, {@code МАКСИМУМ}. */
    AGGREGATE,
    /** {@code ЕСТЬNULL}, {@code ПРЕДСТАВЛЕНИЕ}, {@code ТИПЗНАЧЕНИЯ}, {@code АВТОНОМЕРЗАПИСИ}. */
    OTHER,
    /** Элемент не является функцией. */
    NONE
}
