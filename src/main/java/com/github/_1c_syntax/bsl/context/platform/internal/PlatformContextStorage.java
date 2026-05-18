package com.github._1c_syntax.bsl.context.platform.internal;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Хранилище контекста платформы.
 */
public class PlatformContextStorage {
    private final List<Context> contexts;

    @Getter
    private final PlatformGlobalContext globalContext;
    private final Map<String, Context> contextsByNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    public PlatformContextStorage(List<Context> contexts) {

        var platformGlobalContext = contexts.stream()
                .filter(c -> c instanceof PlatformGlobalContext)
                .findFirst()
                .map(context -> (PlatformGlobalContext) context);

        if (platformGlobalContext.isPresent()) {
            globalContext = platformGlobalContext.get();
            contexts.remove(globalContext);
        } else {
            globalContext = null;
        }

        this.contexts = contexts;

        // В HBK встречаются разные типы с одинаковым русским или английским
        // именем (например, «ЭлементыФормы»: FormItems — управляемые формы,
        // коллекция; Controls — устаревшие обычные формы, обычный тип).
        // putIfAbsent: первый встретившийся побеждает, не затираем —
        // поведение более предсказуемо, чем тихая перезапись. Какой именно
        // окажется первым, недетерминированно (HbkTreeParser парсит дерево
        // через parallelStream); если нужен конкретный — обращайтесь по
        // уникальному en-алиасу (FormItems, Controls).
        contexts.forEach(context -> {
            var name = context.name().getName();
            var alias = context.name().getAlias();
            if (!name.isBlank()) {
                contextsByNames.putIfAbsent(name, context);
            }
            if (!alias.isBlank()) {
                contextsByNames.putIfAbsent(alias, context);
            }
        });
    }

    public Optional<Context> getContextByName(String name) {
        return Optional.ofNullable(contextsByNames.getOrDefault(name, null));
    }

    public Optional<Context> getContextByName(ContextName name) {
        var context = contextsByNames.getOrDefault(name.getName(),
                contextsByNames.getOrDefault(name.getAlias(), null));

        return Optional.ofNullable(context);
    }

    public List<Context> getContexts() {
        return List.copyOf(contexts);
    }

}
