package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextType;
import lombok.Builder;

import java.util.List;

/**
 * Контекстный тип платформы.
 * <p>
 * «Страничные» метаданные ({@code availabilities}, {@code sinceVersion},
 * {@code deprecatedSinceVersion}, {@code notes}, {@code examples},
 * {@code seeAlso}, {@code recommendedReplacements}) снимаются с главной
 * HTML-страницы типа; у типов без соответствующих блоков они пустые.
 * <p>
 * {@code pagePath} — путь исходной страницы внутри HBK. Он одинаков в
 * {@code shcntx_ru.hbk} и {@code shcntx_root.hbk} (пути совпадают файл-в-файл),
 * поэтому {@link BilingualMerger} сопоставляет ru- и en-контексты именно по
 * нему: имена для этого не годятся — ru-страница может указывать в скобках
 * устаревший английский вариант.
 */
@Builder
public record PlatformContextType(ContextName name, List<ContextMethod> methods, List<ContextConstructor> constructors,
                                  List<ContextEvent> events,
                                  List<ContextProperty> properties,
                                  List<ContextFormParameter> formParameters,
                                  String description,
                                  String notes,
                                  List<Availability> availabilities,
                                  String sinceVersion,
                                  String deprecatedSinceVersion,
                                  List<String> examples,
                                  List<String> seeAlso,
                                  List<String> recommendedReplacements,
                                  String pagePath) implements ContextType {

    public PlatformContextType {
        // Билдер заполняет только то, что реально есть на странице типа,
        // остальное Lombok передаёт сюда как null.
        if (description == null) description = "";
        if (notes == null) notes = "";
        if (sinceVersion == null) sinceVersion = "";
        if (deprecatedSinceVersion == null) deprecatedSinceVersion = "";
        if (formParameters == null) formParameters = List.of();
        if (availabilities == null) availabilities = List.of();
        if (examples == null) examples = List.of();
        if (seeAlso == null) seeAlso = List.of();
        if (recommendedReplacements == null) recommendedReplacements = List.of();
        if (pagePath == null) pagePath = "";
    }

    @Override
    public ContextKind kind() {
        return ContextKind.TYPE;
    }
    @Override
    public String toString() {
        return name.toString();
    }
}
