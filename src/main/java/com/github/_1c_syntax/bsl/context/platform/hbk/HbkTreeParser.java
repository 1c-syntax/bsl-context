package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github.eightm.lib.Page;
import com.github.eightm.lib.TableOfContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class HbkTreeParser {
    private final List<Context> contexts = new ArrayList<>();

    public HbkTreeParser() {

    }

    public List<Context> parse(TableOfContent tree) {
        visitPagesFromTree(tree.getPages());

        return contexts;
    }

    public void visitPagesFromTree(List<Page> pages) {
        for (var page : pages) {
            // страницы-заглушки не интересны в парсере
            if (page.htmlPath().isEmpty()) {
                continue;
            }

            if (isGlobalContextPage(page)) {
                // TODO: реализовать чтение глобального контекста
            } else if (isCatalogPage(page)) {
                visitPagesFromTree(page.children());
            } else {
                visitTypePage(page);
            }
        }
    }

    public void visitTypePage(Page page) {
        if (isEnumPage(page)) {
            var properties = getEnumValuesFromPage(page);
            var type = PlatformContextEnum.builder()
                    .name(new ContextName(page.title().ru(), page.title().en()))
                    .values(properties)
                    .build();
            contexts.add(type);
        } else {
            List<ContextProperty> properties = Collections.emptyList();
            List<ContextMethod> methods = Collections.emptyList();

            for (var subPage : page.children()) {
                if (subPage.title().en().equals("Свойства")) {
                    properties = getPropertiesFromPage(subPage);
                } else if (subPage.title().en().equals("Методы")) {
                    methods = getMethodsFromPage(subPage);
                }
            }

            var type = PlatformContextType.builder()
                    .name(new ContextName(page.title().ru(), page.title().en()))
                    .methods(methods)
                    .properties(properties)
                    .build();

            contexts.add(type);
        }
    }

    private List<ContextEnumValue> getEnumValuesFromPage(Page page) {
        return page.children().stream()
                .filter(it -> it.htmlPath().contains("/properties/"))
                .map(it -> new PlatformContextEnumValue(new ContextName(it.title().ru(), it.title().en())))
                .collect(Collectors.toList());
    }

    private List<ContextMethod> getMethodsFromPage(Page page) {
        return page.children().stream()
                .filter(it -> true)
                .map(it -> {
                    var hasReturnValue = false; // TODO реализовать вычисление

                    return new PlatformContextMethod(new ContextName(it.title().ru(), it.title().en()), hasReturnValue);
                })
                .collect(Collectors.toList());
    }

    private List<ContextProperty> getPropertiesFromPage(Page page) {
        return page.children().stream()
                .filter(it -> it.htmlPath().contains("/properties/")) // TODO проверить на обязальность
                .filter(it -> {
                    // TODO если имя начинается с < , то это расширение типа из конфигурации.
                    return !it.title().ru().startsWith("<");
                })
                .map(it -> {
                    var accessMode = AccessMode.READ_WRITE; // FIXME
                    // TODO реализовать чтение страницы html и получение информации о типах
                    return new PlatformContextProperty(new ContextName(it.title().ru(), it.title().en()), accessMode);
                })
                .collect(Collectors.toList());
    }

    private boolean isCatalogPage(Page page) {
        var elements = page.htmlPath().split("/");
        String endElement;
        if (elements.length > 1) {
            endElement = elements[elements.length - 1];
        } else {
            endElement = elements[0];
        }

        return endElement.contains("catalog");
    }

    private boolean isEnumPage(Page page) {
        // FIXME нужна проверка более точная
        return page.children().stream().anyMatch(it -> it.htmlPath().contains("/properties/"));
    }

    private boolean isGlobalContextPage(Page page) {
        return page.htmlPath().contains("Global context.html");
    }
}
