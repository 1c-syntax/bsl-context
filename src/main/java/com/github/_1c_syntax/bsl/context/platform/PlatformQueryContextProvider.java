package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextQueryElement;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.QueryContextProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Контекст языка запросов: таблицы из ветки {@code tables/} и элементы языка
 * из {@code shquery_*.hbk}.
 * <p>
 * Хранится отдельно от {@link PlatformContextProvider} — в общий список
 * контекстов встроенного языка эти элементы не попадают
 * (см. {@link QueryContextProvider}).
 * <p>
 * Типы полей и параметров таблиц ссылаются на платформенные типы
 * ({@code ДокументСсылка.<Имя документа>}, {@code Дата}), поэтому при
 * создании провайдер резолвит их через переданный контекст встроенного языка.
 */
public class PlatformQueryContextProvider implements QueryContextProvider {

    private final List<ContextQueryTable> tables;
    private final List<ContextQueryElement> elements;
    private final Map<String, ContextQueryTable> tablesByName =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    /**
     * Все таблицы с данным именем. Отдельно от {@link #tablesByName}, потому
     * что имя не уникально: таблицы регистра бухгалтерии описаны дважды — с
     * поддержкой корреспонденции и без.
     */
    private final Map<String, List<ContextQueryTable>> tablesByNameAll =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, ContextQueryElement> elementsByName =
        new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /**
     * @param platformContexts контекст встроенного языка — источник типов для
     *                         полей и параметров таблиц; может быть
     *                         {@code null}, тогда типы останутся неразрешёнными
     * @param tables           таблицы языка запросов
     * @param elements         элементы языка запросов
     */
    public PlatformQueryContextProvider(ContextProvider platformContexts,
                                        List<ContextQueryTable> tables,
                                        List<ContextQueryElement> elements) {
        this.tables = tables == null ? List.of() : List.copyOf(tables);
        this.elements = elements == null ? List.of() : List.copyOf(elements);

        if (platformContexts != null) {
            resolveTypes(platformContexts);
        }

        // Индексы имён — свои: пространство имён языка запросов не пересекается
        // с именами встроенного языка. putIfAbsent, как и в основном хранилище.
        for (var table : this.tables) {
            index(tablesByName, table.name().getName(), table);
            index(tablesByName, table.name().getAlias(), table);
            indexAll(tablesByNameAll, table.name().getName(), table);
            indexAll(tablesByNameAll, table.name().getAlias(), table);
        }
        for (var element : this.elements) {
            index(elementsByName, element.name().getName(), element);
            index(elementsByName, element.name().getAlias(), element);
        }
    }

    /**
     * Резолвит сырые имена типов у полей и параметров через контекст
     * встроенного языка. Кэш нужен затем, что одни и те же имена
     * ({@code Дата}, {@code Булево}, {@code Строка}) встречаются в сотнях полей.
     */
    private void resolveTypes(ContextProvider platformContexts) {
        Map<String, Context> cache = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        UnaryTypeResolver resolver = raw ->
            cache.computeIfAbsent(raw, k -> platformContexts.getContextByName(k).orElse(null));

        for (var table : tables) {
            for (var field : table.fields()) {
                if (field instanceof PlatformQueryTableField f) {
                    f.processRawTypes(resolver);
                }
            }
            for (var parameter : table.parameters()) {
                if (parameter instanceof PlatformQueryTableParameter p) {
                    p.processRawTypes(resolver);
                }
            }
        }
    }

    /** Резолвер «сырое имя типа → контекст»; {@code null}, если тип неизвестен. */
    @FunctionalInterface
    public interface UnaryTypeResolver {
        Context resolve(String rawTypeName);
    }

    private static <T> void index(Map<String, T> index, String name, T value) {
        if (name != null && !name.isBlank()) {
            index.putIfAbsent(name, value);
        }
    }

    /**
     * Копит все значения с данным именем. Проверка на уже добавленное нужна
     * потому, что имя и alias могут совпадать (у таблиц без en-стороны), и
     * тогда одна таблица попала бы в список дважды.
     */
    private static <T> void indexAll(Map<String, List<T>> index, String name, T value) {
        if (name == null || name.isBlank()) {
            return;
        }
        var bucket = index.computeIfAbsent(name, k -> new ArrayList<>());
        if (!bucket.contains(value)) {
            bucket.add(value);
        }
    }

    @Override
    public List<ContextQueryTable> getTables() {
        return tables;
    }

    @Override
    public Optional<ContextQueryTable> getTableByName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(tablesByName.get(name));
    }

    @Override
    public List<ContextQueryTable> getTablesByName(String name) {
        if (name == null) {
            return List.of();
        }
        return List.copyOf(tablesByNameAll.getOrDefault(name, List.of()));
    }

    @Override
    public List<ContextQueryElement> getElements() {
        return elements;
    }

    @Override
    public Optional<ContextQueryElement> getElementByName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(elementsByName.get(name));
    }
}
