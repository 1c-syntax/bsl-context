package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;

import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Поставщик контекста платформы.
 */
public class PlatformContextProvider implements ContextProvider {
    private final PlatformContextStorage storage;

    /**
     * Английские attachments к контексту, индексированные по идентичности
     * объекта (record'ы api-классов immutable, поэтому второй язык кладём
     * здесь). Ключ — сам {@link Context}/{@code ContextMethod}/{@code ContextProperty}/etc.,
     * значение — {@link EnAttachments} с en-описанием и сопутствующими
     * текстами, спарсенными из англоязычного HBK через {@link BilingualMerger}.
     * Если en-HBK не подгружен — карта пуста, и {@link #getEnAttachments(Object)}
     * вернёт {@link EnAttachments#EMPTY}.
     */
    private final Map<Object, EnAttachments> enAttachments = new IdentityHashMap<>();

    public PlatformContextProvider(PlatformContextStorage storage) {

        this.storage = storage;

        var contexts = getContexts();
        // Индекс «ru-имя → Context» для O(1)-резолва сырых типов вместо
        // линейного поиска по всем контекстам на каждое имя.
        // Только ContextType / ContextEnum — другие как тип не появляются.
        var typeIndex = buildTypeIndex(contexts);

        // Достраиваем en-сторону valueType у enum-«библиотек»: HBK-парсер видит
        // маркер на ru-странице («Значения этого набора имеют тип X.»), но имя
        // X на этой стадии безязычное. Здесь по индексу резолвим en-alias.
        contexts.stream()
            .filter(c -> c instanceof PlatformContextEnum)
            .map(c -> (PlatformContextEnum) c)
            .forEach(e -> e.bindBilingualValueType(typeIndex));

        contexts.stream()
                .parallel()
                .filter(context -> context instanceof PlatformContextType
                    || context instanceof PlatformContextCollection)
                .flatMap(context -> {
                    var c = (ContextType) context;

                    // Сам тип-коллекция тоже резолвим вместе с его членами —
                    // имена элементов коллекции живут в самом ContextCollection,
                    // а не в его properties/methods.
                    Stream<?> selfStream = c instanceof PlatformContextCollection
                        ? Stream.of(c)
                        : Stream.empty();

                    return Stream.of(
                        selfStream,
                        c.properties().stream(),
                        c.methods().stream(),
                        c.events().stream(),
                        c.constructors().stream(),
                        c.formParameters().stream()
                    );
                })
            .flatMap(Function.identity())
                .forEach(context -> {
                    if (context instanceof PlatformContextProperty c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextMethod c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextEvent c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextConstructor c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextCollection c) {
                        c.processRawTypes(typeIndex);
                    } else if (context instanceof PlatformContextFormParameter c) {
                        c.processRawTypes(typeIndex);
                    }
                });

        var globalContext = getGlobalContext();
        if (globalContext == null) {
            return;
        }

        Stream.of(globalContext.properties(), globalContext.methods(),
                globalContext.applicationEvents(), globalContext.externalConnectionModuleEvents(),
                globalContext.sessionModuleEvents(), globalContext.ordinaryApplicationEvents())
                .parallel()
                .flatMap(Collection::stream)
                .forEach(o -> {
                  if (o instanceof PlatformContextProperty c) {
                    c.processRawTypes(typeIndex);
                  } else if (o instanceof PlatformContextMethod c) {
                    c.processRawTypes(typeIndex);
                  } else if (o instanceof PlatformContextEvent c) {
                    c.processRawTypes(typeIndex);
                  }
                });
    }

    private static Map<String, Context> buildTypeIndex(List<Context> contexts) {
        var map = new HashMap<String, Context>(contexts.size() * 2);
        for (Context c : contexts) {
            if (c instanceof ContextType || c instanceof ContextEnum) {
                map.putIfAbsent(c.name().getName(), c);
                map.putIfAbsent(c.name().getAlias(), c);
            }
        }
        return map;
    }

    @Override
    public List<Context> getContexts() {
        return storage.getContexts();
    }

    @Override
    public Optional<Context> getContextByName(String name) {
        return storage.getContextByName(name);
    }

    @Override
    public PlatformGlobalContext getGlobalContext() {
      return storage.getGlobalContext();
    }

    /**
     * Зарегистрировать английские attachments для контекста. Используется
     * {@link BilingualMerger} при загрузке shcntx_en.hbk. Для контекстов,
     * у которых attachments уже зарегистрированы или равны
     * {@link EnAttachments#EMPTY}, повторно ничего не сохраняет.
     */
    public void putEnAttachments(Object context, EnAttachments attachments) {
        if (context == null || attachments == null || attachments.isEmpty()) {
            return;
        }
        enAttachments.putIfAbsent(context, attachments);
    }

    /**
     * Compat shortcut: регистрирует только en-description, остальные
     * поля {@link EnAttachments} пусты.
     */
    public void putDescriptionEn(Object context, String descriptionEn) {
        if (context == null || descriptionEn == null || descriptionEn.isEmpty()) {
            return;
        }
        enAttachments.putIfAbsent(context, EnAttachments.ofDescription(descriptionEn));
    }

    /**
     * Английские attachments для указанного контекста. Возвращает
     * {@link EnAttachments#EMPTY}, если en-HBK не подгружен или не нашлось
     * сопоставление при bilingual-merge.
     *
     * @param context экземпляр {@link Context}/{@code ContextMethod}/{@code ContextProperty}/
     *                {@code ContextSignatureParameter}/{@code ContextEnumValue}/
     *                {@code ContextConstructor}/{@code ContextMethodSignature}/
     *                {@code ContextCollection}.
     */
    public EnAttachments getEnAttachments(Object context) {
        if (context == null) {
            return EnAttachments.EMPTY;
        }
        return enAttachments.getOrDefault(context, EnAttachments.EMPTY);
    }

    /** Compat shortcut: только en-description. */
    public String getDescriptionEn(Object context) {
        return getEnAttachments(context).description();
    }

}
