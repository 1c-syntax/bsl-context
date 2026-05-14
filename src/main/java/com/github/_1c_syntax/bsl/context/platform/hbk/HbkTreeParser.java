package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextConstructor;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEvent;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethodSignature;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.context.platform.primitive.ArbitraryType;
import com.github._1c_syntax.bsl.context.platform.primitive.BooleanType;
import com.github._1c_syntax.bsl.context.platform.primitive.DateType;
import com.github._1c_syntax.bsl.context.platform.primitive.NullType;
import com.github._1c_syntax.bsl.context.platform.primitive.NumberType;
import com.github._1c_syntax.bsl.context.platform.primitive.StringType;
import com.github._1c_syntax.bsl.context.platform.primitive.UndefinedType;
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

        contexts.add(new BooleanType());
        contexts.add(new DateType());
        contexts.add(new NullType());
        contexts.add(new NumberType());
        contexts.add(new StringType());
        contexts.add(new UndefinedType());
        contexts.add(new ArbitraryType());

        visitPagesFromTree(tree.getPages());

        return contexts;
    }

    public void visitPagesFromTree(List<Page> pages) {

        pages.parallelStream()
            // страницы-заглушки не интересны в парсере
            .filter(page -> !page.htmlPath().isEmpty())
            .forEach(page -> {
                if (isGlobalContextPage(page)) {
                    visitGlobalContextPage(page);
                } else if (isCatalogPage(page)) {
                    visitPagesFromTree(page.children());
                } else if (isEnumPage(page)) {
                    visitEnumPage(page);
                } else {
                    visitTypePage(page);
                }
            });

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
        List<ContextConstructor> constructors = Collections.emptyList();

        for (var subPage : page.children()) {
            switch (subPage.title().en()) {
                case "Свойства" -> properties = getPropertiesFromPage(subPage);
                case "Методы" -> methods = getMethodsFromPage(subPage);
                case "События" -> events = getEventsFromPage(subPage);
                case "Конструкторы" -> constructors = getConstructors(subPage);
            }
        }

        var type = PlatformContextType.builder()
            .name(new ContextName(page.title().ru(), page.title().en()))
            .methods(methods)
            .properties(properties)
            .events(events)
            .constructors(constructors)
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
            .map(it -> {
                var description = htmlParser.parseEnumValuePage(it);
                return new PlatformContextEnumValue(
                    new ContextName(it.title().ru(), it.title().en()),
                    description.getDescription(),
                    description.getSinceVersion(),
                    description.getDeprecatedSinceVersion()
                );
            })
            .collect(Collectors.toList());
    }

    private List<ContextMethod> getMethodsFromPage(Page page) {
        return page.children().stream()
            .map(it -> {

                var methodDescription = htmlParser.parseMethodPage(it);

                return PlatformContextMethod.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .description(methodDescription.getDescription())
                    .availabilities(mapAvailabilities(methodDescription.getAvailabilities()))
                    .rawReturnValues(methodDescription.getReturnValues())
                    .signatures(buildSignatures(methodDescription.getSignatures()))
                    .sinceVersion(methodDescription.getSinceVersion())
                    .deprecatedSinceVersion(methodDescription.getDeprecatedSinceVersion())
                    .returnValueDescription(methodDescription.getReturnValueDescription())
                    .examples(List.copyOf(methodDescription.getExamples()))
                    .seeAlso(List.copyOf(methodDescription.getSeeAlso()))
                    .build();

            })
            .collect(Collectors.toList());
    }

    private List<ContextConstructor> getConstructors(Page page) {
        return page.children().stream()
            .map(this::getConstructor)
            .collect(Collectors.toList());
    }

    private ContextConstructor getConstructor(Page page) {

        var constructorDescription = htmlParser.parseConstructorPage(page);

        return PlatformContextConstructor.builder()
            .name(new ContextName(page.title().ru(), page.title().en()))
            .description(constructorDescription.getDescription())
            .parameters(constructorDescription.getParameters().stream()
                .map(HbkTreeParser::buildParameter)
                .map(p -> (ContextSignatureParameter) p)
                .toList())
            .sinceVersion(constructorDescription.getSinceVersion())
            .deprecatedSinceVersion(constructorDescription.getDeprecatedSinceVersion())
            .syntaxText(constructorDescription.getSyntaxText())
            .build();
    }

    private List<ContextEvent> getEventsFromPage(Page page) {
        return page.children().stream()
            .map(it -> {

                var methodDescription = htmlParser.parseMethodPage(it);

                return PlatformContextEvent.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .description(methodDescription.getDescription())
                    .availabilities(mapAvailabilities(methodDescription.getAvailabilities()))
                    .signatures(buildSignatures(methodDescription.getSignatures()))
                    .sinceVersion(methodDescription.getSinceVersion())
                    .deprecatedSinceVersion(methodDescription.getDeprecatedSinceVersion())
                    .build();

            })
            .collect(Collectors.toList());
    }

    private static List<Availability> mapAvailabilities(List<String> raw) {
        return raw.stream()
            .map(Availability::findByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    private static List<ContextMethodSignature> buildSignatures(
        List<HtmlParser.MethodSignatureDescription> raw) {
        return raw.stream()
            .map(sigDesc -> PlatformContextMethodSignature.builder()
                .description(sigDesc.getDescription())
                .name(new ContextName(sigDesc.getName(), ""))
                .parameters(sigDesc.getParameters().stream()
                    .map(HbkTreeParser::buildParameter)
                    .map(p -> (ContextSignatureParameter) p)
                    .toList())
                .syntaxText(sigDesc.getSyntaxText())
                .build())
            .map(s -> (ContextMethodSignature) s)
            .toList();
    }

    private static PlatformContextSignatureParameter buildParameter(
        HtmlParser.MethodSignatureParameterDescription paramDesc) {
        return PlatformContextSignatureParameter.builder()
            .description(paramDesc.getDescription())
            .name(new ContextName(paramDesc.getName(), ""))
            .isRequired(paramDesc.isRequired())
            .rawTypes(paramDesc.getTypes())
            .defaultValue(paramDesc.getDefaultValue())
            .build();
    }

    private List<ContextProperty> getPropertiesFromPage(Page page) {
        // Свойства с именем, начинающимся с «<» (например, «<Имя справочника>»),
        // — generic-плейсхолдеры, заполняемые из конфигурации. Не отбрасываем,
        // а помечаем флагом isGeneric() (см. ContextProperty / ContextNames).
        return page.children().stream()
            .filter(it -> it.htmlPath().contains("/properties/"))
            .map(it -> {

                var propertyDescription = htmlParser.parsePropertyPage(it);

                var accessMode = AccessMode.findByName(propertyDescription.getAccessMode());

                return PlatformContextProperty.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .accessMode(accessMode.orElse(AccessMode.READ_WRITE))
                    .rawTypes(propertyDescription.getTypes())
                    .description(propertyDescription.getDescription())
                    .availabilities(mapAvailabilities(propertyDescription.getAvailabilities()))
                    .sinceVersion(propertyDescription.getSinceVersion())
                    .deprecatedSinceVersion(propertyDescription.getDeprecatedSinceVersion())
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
