package com.github._1c_syntax.bsl.context.api;

import java.util.List;
import java.util.Optional;

/**
 * Точка доступа к контексту <b>языка запросов</b> — таблицам и элементам
 * языка ({@code ВЫБРАТЬ}, {@code ПОДСТРОКА}, {@code СРЕДНЕЕ}).
 * <p>
 * Держится отдельно от {@link ContextProvider} намеренно: текст запроса —
 * самостоятельный строковый литерал внутри кода, и внутри него действует
 * только контекст языка запросов, а контекст встроенного языка — нет.
 * Смешивать их в одном списке нельзя ещё и потому, что имена пересекаются:
 * {@code СТРОКА} и {@code ДАТА} есть и там, и там, но означают разное —
 * литерал языка запросов против примитивного типа.
 *
 * <p>Создаётся вместе с {@link ContextProvider} через
 * {@code PlatformContextGrabber.parse()}, забирается методом
 * {@code getQueryProvider()}.
 */
public interface QueryContextProvider {

    /**
     * Таблицы языка запросов: основные ({@code Справочник.<Имя справочника>})
     * и виртуальные ({@code РегистрНакопления.<Имя регистра>.Остатки}).
     */
    List<ContextQueryTable> getTables();

    /**
     * Поиск таблицы по имени (ru или en), регистронезависимо.
     * <p>
     * Имя таблицы не уникально: таблицы регистра бухгалтерии описаны в
     * синтакс-помощнике дважды — с поддержкой корреспонденции и без, — под
     * одним именем и с разными наборами полей. В таком случае метод отдаёт
     * <b>какую-то одну</b> из них, и какую именно — не определено. Если важны
     * обе — {@link #getTablesByName(String)}, различать их
     * по {@link ContextQueryTable#correspondence()}.
     */
    Optional<ContextQueryTable> getTableByName(String name);

    /**
     * Все таблицы с указанным именем (ru или en), регистронезависимо.
     * В отличие от {@link #getTableByName(String)} не теряет пару таблиц
     * регистра бухгалтерии.
     * <p>
     * Пустой список, если ничего не найдено.
     */
    default List<ContextQueryTable> getTablesByName(String name) {
        return getTableByName(name).map(List::of).orElseGet(List::of);
    }

    /**
     * Элементы языка запросов: функции, ключевые слова, предложения,
     * операторы, литералы и обзорные статьи.
     */
    List<ContextQueryElement> getElements();

    /**
     * Поиск элемента языка запросов по имени (ru или en), регистронезависимо.
     */
    Optional<ContextQueryElement> getElementByName(String name);

    /**
     * Элементы указанной категории — например, только функции для
     * автодополнения внутри текста запроса.
     */
    default List<ContextQueryElement> getElements(QueryElementCategory category) {
        return getElements().stream()
            .filter(e -> e.category() == category)
            .toList();
    }
}
