package com.github._1c_syntax.bsl.context.platform;

import java.util.List;

/**
 * Набор английских (вторичных) текст-полей, прикреплённых к ru-контексту
 * после bilingual-парса (двух HBK — ru и en). Поля заполняются
 * {@link BilingualMerger} и кладутся в {@link PlatformContextProvider} по
 * идентичности соответствующего ru-объекта.
 * <p>
 * Все поля строго опциональны: если en-HBK не подгружен или конкретное
 * поле в нём отсутствует — возвращается пустая строка / пустой список
 * (см. {@link #EMPTY}). Использующая сторона (например, LS) сама решает,
 * показывать ли en-вариант пользователю в зависимости от текущей локали.
 *
 * @param description            «Описание:» в синтакс-помощнике
 * @param returnValueDescription текстовое продолжение блока «Возвращаемое значение:»
 *                               (после имени типа)
 * @param notes                  «Замечание:»
 * @param examples               блоки «Пример:» (по одной строке на пример)
 * @param seeAlso                «См. также:» (имена связанных контекстов)
 * @param forEachDescription     описание обхода {@code Для Каждого} на коллекции
 * @param indexAccessDescription описание индексатора {@code coll[…]} на коллекции
 */
public record EnAttachments(
    String description,
    String returnValueDescription,
    String notes,
    List<String> examples,
    List<String> seeAlso,
    String forEachDescription,
    String indexAccessDescription
) {

    public static final EnAttachments EMPTY = new EnAttachments(
        "", "", "", List.of(), List.of(), "", ""
    );

    public EnAttachments {
        description = description == null ? "" : description;
        returnValueDescription = returnValueDescription == null ? "" : returnValueDescription;
        notes = notes == null ? "" : notes;
        examples = examples == null ? List.of() : List.copyOf(examples);
        seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
        forEachDescription = forEachDescription == null ? "" : forEachDescription;
        indexAccessDescription = indexAccessDescription == null ? "" : indexAccessDescription;
    }

    public boolean isEmpty() {
        return description.isEmpty()
            && returnValueDescription.isEmpty()
            && notes.isEmpty()
            && examples.isEmpty()
            && seeAlso.isEmpty()
            && forEachDescription.isEmpty()
            && indexAccessDescription.isEmpty();
    }

    /**
     * Convenience-конструктор: только en-description, остальное пусто.
     * Используется для members без расширенных метаданных
     * (свойства, параметры, конструкторы).
     */
    public static EnAttachments ofDescription(String description) {
        if (description == null || description.isEmpty()) {
            return EMPTY;
        }
        return new EnAttachments(description, "", "", List.of(), List.of(), "", "");
    }
}
