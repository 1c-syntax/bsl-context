package com.github._1c_syntax.bsl.context.api;

/**
 * Языковая конструкция встроенного языка 1С: литерал, оператор,
 * управляющая конструкция, директива компиляции, аннотация или
 * инструкция препроцессора. Соответствует {@link ContextKind#LANGUAGE_KEYWORD}.
 *
 * <p>Источник — {@code shlang_*.hbk}. Имя ru/en извлекается из заголовка
 * страницы СП («Истина (True)» → name=«Истина», alias=«True»).
 */
public interface ContextLanguageKeyword extends Context {

    /**
     * Подкатегория конструкции (литерал / оператор / директива и т.п.).
     */
    LanguageKeywordCategory category();

    /**
     * Описание из синтакс-помощника. Для литералов — обычно
     * «Литерал для указания значения типа Булево»; для операторов —
     * подробное описание с синтаксисом.
     */
    String description();

    /**
     * Двуязычный сниппет автодополнения с плейсхолдерами {@code <?>}.
     * Источник — парный {@code .st}-файл из FileStorage. Если шаблона нет
     * — возвращает {@link LanguageKeywordSnippet#EMPTY}.
     */
    default LanguageKeywordSnippet snippet() {
        return LanguageKeywordSnippet.EMPTY;
    }

    @Override
    default ContextKind kind() {
        return ContextKind.LANGUAGE_KEYWORD;
    }
}
