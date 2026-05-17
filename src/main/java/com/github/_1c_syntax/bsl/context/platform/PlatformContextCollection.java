package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import lombok.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Платформенная коллекция значений ({@code Массив}, {@code Соответствие},
 * {@code Структура}, {@code ТаблицаЗначений} и т.п.) — расширение
 * {@link PlatformContextType} с типами элементов коллекции.
 * <p>
 * Сырые имена типов-элементов из блока «Элементы коллекции:» страницы СП
 * хранятся в {@link #rawCollectionElementTypes} и резолвятся в
 * {@link Context}-объекты через {@link #processRawCollectionElementTypes(Map)}
 * один раз в конструкторе {@link PlatformContextProvider}.
 */
@Builder
public class PlatformContextCollection implements ContextCollection {

    private final ContextName name;
    private final List<ContextMethod> methods;
    private final List<ContextConstructor> constructors;
    private final List<ContextEvent> events;
    private final List<ContextProperty> properties;
    @lombok.Builder.Default
    private final String description = "";
    private final List<String> rawCollectionElementTypes;
    private final List<Context> collectionElementTypes = new ArrayList<>();
    private final boolean supportsForEach;
    @lombok.Builder.Default
    private final String forEachDescription = "";
    private final boolean supportsIndexAccess;
    @lombok.Builder.Default
    private final String indexAccessDescription = "";

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public ContextKind kind() {
        return ContextKind.COLLECTION;
    }

    @Override
    public List<ContextMethod> methods() {
        return List.copyOf(methods);
    }

    @Override
    public List<ContextProperty> properties() {
        return List.copyOf(properties);
    }

    @Override
    public List<ContextEvent> events() {
        return List.copyOf(events);
    }

    @Override
    public List<ContextConstructor> constructors() {
        return List.copyOf(constructors);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public List<Context> collectionElementTypes() {
        return List.copyOf(collectionElementTypes);
    }

    @Override
    public boolean supportsForEach() {
        return supportsForEach;
    }

    @Override
    public String forEachDescription() {
        return forEachDescription;
    }

    @Override
    public boolean supportsIndexAccess() {
        return supportsIndexAccess;
    }

    @Override
    public String indexAccessDescription() {
        return indexAccessDescription;
    }

    @Override
    public String toString() {
        return name.toString();
    }

    /**
     * Резолвит сырые имена типов-элементов через переданный индекс.
     * Безымянные / неизвестные имена игнорируются. Имя метода и сигнатура
     * совпадают с {@code processRawTypes} у других {@code Platform*}-классов
     * (Property, Method, Event, Constructor), что позволяет единообразно
     * вызывать его в {@link PlatformContextProvider}.
     */
    protected void processRawTypes(Map<String, Context> typeIndex) {
        if (rawCollectionElementTypes == null) {
            return;
        }
        for (var raw : rawCollectionElementTypes) {
            var resolved = typeIndex.get(raw);
            if (resolved != null) {
                collectionElementTypes.add(resolved);
            }
        }
    }
}
