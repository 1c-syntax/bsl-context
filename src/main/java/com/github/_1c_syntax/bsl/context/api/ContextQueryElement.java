package com.github._1c_syntax.bsl.context.api;

import java.util.List;

/**
 * Элемент языка запросов — функция ({@code СРЕДНЕЕ}, {@code ПОДСТРОКА}),
 * ключевое слово ({@code ВЫБРАТЬ}), предложение ({@code ИЗ}), оператор или
 * литерал.
 * <p>
 * Источник — {@code shquery_*.hbk}, раздел справки «Язык запросов». Страницы
 * там размечены свободным HTML (заголовок, абзацы, блоки «Пример:»), без
 * структурных {@code V8SH_*}-классов, поэтому типизированных параметров у
 * функций нет: доступны имя, категория, описание и примеры.
 * <p>
 * Намеренно <b>не</b> наследует {@link Context}: это конструкция другого
 * языка, живущая внутри строкового литерала запроса. Ни доступности по
 * клиентам, ни версий, ни «См. также» у неё нет, а имена пересекаются с
 * платформенными ({@code СТРОКА}, {@code ДАТА}) и означают другое.
 *
 * @see QueryElementCategory
 * @see QueryContextProvider#getElements()
 */
public interface ContextQueryElement {

    /**
     * Имя элемента: {@code ПОДСТРОКА} / {@code SUBSTRING}. У обзорных статей
     * ({@link QueryElementCategory#ARTICLE}) — заголовок страницы целиком.
     */
    ContextName name();

    /**
     * Категория элемента: функция, ключевое слово, предложение, оператор,
     * литерал или обзорная статья.
     */
    QueryElementCategory category();

    /**
     * Вид функции — строковая, датная, математическая, агрегатная или прочая.
     * У не-функций {@link QueryFunctionGroup#NONE}.
     */
    default QueryFunctionGroup functionGroup() {
        return QueryFunctionGroup.NONE;
    }

    /**
     * Конструкция, внутри которой применяется эта: у {@code ПЕРВЫЕ} —
     * {@code ВЫБРАТЬ}, у {@code ИЕРАРХИЯ} — {@code ИТОГИ … ПО}. {@code null}
     * у верхнеуровневых. Нужна для контекстного автодополнения — предлагать
     * элемент только там, где он допустим.
     */
    default ContextName parent() {
        return null;
    }

    /**
     * Конструкции, допустимые внутри этой: у {@code ВЫБРАТЬ} —
     * {@code РАЗРЕШЕННЫЕ}, {@code РАЗЛИЧНЫЕ}, {@code ПЕРВЫЕ}, {@code КАК},
     * {@code ПУСТАЯТАБЛИЦА}.
     */
    default List<ContextName> children() {
        return List.of();
    }

    /**
     * Синтаксическое правило со страницы:
     * {@code ВЫБРАТЬ [РАЗРЕШЕННЫЕ] [РАЗЛИЧНЫЕ] [ПЕРВЫЕ <Количество>] <Список полей выборки>}.
     * Необязательные части в квадратных скобках, вложенные конструкции —
     * в угловых. Пусто, если правила на странице нет.
     */
    default String syntaxRule() {
        return "";
    }

    /**
     * Параметры функции. В справке они описаны прозой («Первый параметр – …»),
     * поэтому имён у них нет — только позиция, описание и типы.
     */
    default List<ContextQueryParameter> parameters() {
        return List.of();
    }

    /**
     * Описание из синтакс-помощника.
     */
    String description();

    /**
     * Английское описание — с парной страницы {@code shquery_root.hbk}.
     * Пусто, если en-HBK не подгружен или парной страницы нет.
     */
    default String descriptionEn() {
        return "";
    }

    /**
     * Примеры запросов со страницы (блоки после «Пример:»).
     */
    default List<String> examples() {
        return List.of();
    }

    /**
     * Те же примеры с парной en-страницы — в них и текст, и сам запрос
     * записаны на английском ({@code SELECT … RECORDAUTONUMBER () AS Key}).
     */
    default List<String> examplesEn() {
        return List.of();
    }

    /**
     * Смежные статьи из блока «см. также:» — имена страниц, на которые он
     * ссылается ({@code Функции языка запросов}). Ищутся по имени через
     * {@link QueryContextProvider#getElementByName(String)}.
     */
    default List<String> seeAlso() {
        return List.of();
    }

    /** То же с парной en-страницы (блок «See also:»). */
    default List<String> seeAlsoEn() {
        return List.of();
    }
}
