package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.*;
import com.github._1c_syntax.bsl.context.platform.*;
import com.github._1c_syntax.bsl.context.platform.primitive.*;
import com.github.eightm.lib.Page;
import com.github.eightm.lib.TableOfContent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HbkTreeParser {
    private final List<Context> contexts = new ArrayList<>();
    private final HtmlParser htmlParser;

    public HbkTreeParser(Path pagesPath) {
        htmlParser = new HtmlParser(pagesPath);
    }

    public List<Context> parse(TableOfContent tree) {

        visitPagesFromTree(tree.getPages());

        contexts.add(new BooleanType());
        contexts.add(new DateType());
        contexts.add(new NullType());
        contexts.add(new NumberType());
        contexts.add(new StringType());
        contexts.add(new UndefinedType());
        contexts.add(new ArbitraryType());

        return contexts;
    }

    public void visitPagesFromTree(List<Page> pages) {
        for (var page : pages) {
            // страницы-заглушки не интересны в парсере
            if (page.htmlPath().isEmpty()) {
                continue;
            }

            if (isGlobalContextPage(page)) {
                visitGlobalContextPage(page);
            } else if (isCatalogPage(page)) {
                visitPagesFromTree(page.children());
            } else if (isEnumPage(page)) {
                visitEnumPage(page);
            } else {
                visitTypePage(page);
            }
        }
    }

    public void visitGlobalContextPage(Page page) {
        List<ContextProperty> properties = Collections.emptyList();
        List<ContextMethod> methods = new ArrayList<>();
        List<ContextEvent> externalConnectionModuleEvents = Collections.emptyList();
        List<ContextEvent> sessionModuleEvents = Collections.emptyList();
        List<ContextEvent> ordinaryApplicationEvents = Collections.emptyList();
        List<ContextEvent> applicationEvents = Collections.emptyList();

        for (var subPage : page.children()) {
            if (subPage.title().en().equals("Свойства")) {
                properties = getPropertiesFromPage(subPage);
            } else if (subPage.htmlPath().contains("methods")) {
                methods.addAll(getMethodsFromPage(subPage));
            } else if (subPage.title().en().equals("События внешнего соединения")) {
                externalConnectionModuleEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События модуля сеанса")) {
                sessionModuleEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События обычного приложения")) {
                ordinaryApplicationEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События приложения")) {
                applicationEvents = getEventsFromPage(subPage);
            }
        }

        contexts.add(
            PlatformGlobalContext.builder()
                .properties(properties)
                .methods(methods)
                .applicationEvents(applicationEvents)
                .ordinaryApplicationEvents(ordinaryApplicationEvents)
                .sessionModuleEvents(sessionModuleEvents)
                .externalConnectionModuleEvents(externalConnectionModuleEvents)
                .build()
        );
    }

    public void visitTypePage(Page page) {

        List<ContextProperty> properties = Collections.emptyList();
        List<ContextMethod> methods = Collections.emptyList();
        List<ContextEvent> events = Collections.emptyList();

        for (var subPage : page.children()) {
          switch (subPage.title().en()) {
            case "Свойства" -> properties = getPropertiesFromPage(subPage);
            case "Методы" -> methods = getMethodsFromPage(subPage);
            case "События" -> events = getEventsFromPage(subPage);
          }
        }

        var type = PlatformContextType.builder()
                .name(new ContextName(page.title().ru(), page.title().en()))
                .methods(methods)
                .properties(properties)
                .events(events)
                .build();

        contexts.add(type);
    }

    private void visitEnumPage(Page page) {
        var properties = getEnumValuesFromPage(page);
        var type = PlatformContextEnum.builder()
                .name(new ContextName(page.title().ru(), page.title().en()))
                .values(properties)
                .build();
        contexts.add(type);
    }

    private List<ContextEnumValue> getEnumValuesFromPage(Page page) {
        return page.children().stream()
                .filter(it -> it.htmlPath().contains("/properties/"))
                .map(it -> new PlatformContextEnumValue(new ContextName(it.title().ru(), it.title().en())))
                .collect(Collectors.toList());
    }

    private List<ContextMethod> getMethodsFromPage(Page page) {
        return page.children().stream()
                .map(it -> {

                    var methodDescription = htmlParser.parseMethodPage(it);

                    var availabilities = methodDescription.getAvailabilities()
                            .stream()
                            .map(Availability::findByName)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();

                    var signatures = methodDescription.getSignatures()
                            .stream()
                            .map(methodSignatureDescription ->
                                PlatformContextMethodSignature.builder()
                                    .description(methodSignatureDescription.getDescription())
                                    .name(new ContextName(methodSignatureDescription.getName(), ""))
                                    .parameters(
                                            methodSignatureDescription.getParameters()
                                                    .stream()
                                                    .map(methodSignatureParameterDescription ->
                                                        PlatformContextSignatureParameter.builder()
                                                            .description(methodSignatureParameterDescription.getDescription())
                                                            .name(new ContextName(methodSignatureParameterDescription.getName(), ""))
                                                            .isRequired(methodSignatureParameterDescription.isRequired())
                                                            .rawTypes(methodSignatureParameterDescription.getTypes())
                                                            .build())
                                                    .map(platformContextSignatureParameter -> (ContextSignatureParameter) platformContextSignatureParameter)
                                                    .toList()
                                    )
                                    .build())
                            .map(platformContextMethodSignature -> (ContextMethodSignature) platformContextMethodSignature)
                            .toList();

                    return PlatformContextMethod.builder()
                            .name(new ContextName(it.title().ru(), it.title().en()))
                            .description(methodDescription.getDescription())
                            .availabilities(availabilities)
                            .rawReturnValues(methodDescription.getReturnValues())
                            .signatures(signatures)
                            .build();

                })
                .collect(Collectors.toList());
    }

    private List<ContextEvent> getEventsFromPage(Page page) {
        return page.children().stream()
                .map(it -> {

                    var methodDescription = htmlParser.parseMethodPage(it);

                    var availabilities = methodDescription.getAvailabilities()
                            .stream()
                            .map(Availability::findByName)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();

                    var signatures = methodDescription.getSignatures()
                            .stream()
                            .map(methodSignatureDescription ->
                                PlatformContextMethodSignature.builder()
                                    .description(methodSignatureDescription.getDescription())
                                    .name(new ContextName(methodSignatureDescription.getName(), ""))
                                    .parameters(
                                            methodSignatureDescription.getParameters()
                                                    .stream()
                                                    .map(methodSignatureParameterDescription ->
                                                        PlatformContextSignatureParameter.builder()
                                                            .description(methodSignatureParameterDescription.getDescription())
                                                            .name(new ContextName(methodSignatureParameterDescription.getName(), ""))
                                                            .isRequired(methodSignatureParameterDescription.isRequired())
                                                            .rawTypes(methodSignatureParameterDescription.getTypes())
                                                            .build())
                                                    .map(platformContextSignatureParameter -> (ContextSignatureParameter) platformContextSignatureParameter)
                                                    .toList()
                                    )
                                    .build())
                            .map(platformContextMethodSignature -> (ContextMethodSignature) platformContextMethodSignature)
                            .toList();

                    return PlatformContextEvent.builder()
                            .name(new ContextName(it.title().ru(), it.title().en()))
                            .description(methodDescription.getDescription())
                            .availabilities(availabilities)
                            .signatures(signatures)
                            .build();

                })
                .collect(Collectors.toList());
    }

    private List<ContextProperty> getPropertiesFromPage(Page page) {
        return page.children().stream()
                .filter(it -> it.htmlPath().contains("/properties/")) // TODO проверить на обязательность
                .filter(it -> {
                    // TODO если имя начинается с < , то это расширение типа из конфигурации.
                    return !it.title().ru().startsWith("<");
                })
                .map(it -> {

                    var propertyDescription = htmlParser.parsePropertyPage(it);

                    var accessMode = AccessMode.findByName(propertyDescription.getAccessMode());
                    var description = propertyDescription.getDescription();
                    List<Availability> availabilities = propertyDescription.getAvailabilities()
                            .stream()
                            .map(Availability::findByName)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .toList();

                    return PlatformContextProperty.builder()
                            .name(new ContextName(it.title().ru(), it.title().en()))
                            .accessMode(accessMode.orElse(AccessMode.READ_WRITE))
                            .rawTypes(propertyDescription.getTypes())
                            .description(description)
                            .availabilities(availabilities)
                            .build();

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
