package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformContextProviderTest {

    @Test
    void resolvesMethodReturnAndParameterTypes() {
        var arrayType = PlatformContextType.builder()
            .name(new ContextName("Массив", "Array"))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(Collections.emptyList())
            .constructors(Collections.emptyList())
            .build();

        var paramSignature = PlatformContextSignatureParameter.builder()
            .name(new ContextName("Строка", ""))
            .isRequired(true)
            .rawTypes(List.of("Строка"))
            .description("")
            .build();

        var signature = PlatformContextMethodSignature.builder()
            .name(new ContextName("Основной", ""))
            .parameters(new ArrayList<>(List.of((ContextSignatureParameter) paramSignature)))
            .description("")
            .build();

        var method = PlatformContextMethod.builder()
            .name(new ContextName("СтрРазделить", "StrSplit"))
            .description("")
            .availabilities(List.of(Availability.SERVER))
            .rawReturnValues(List.of("Массив"))
            .signatures(new ArrayList<>(List.of((ContextMethodSignature) signature)))
            .build();

        var stringType = createSimpleType("Строка", "String");

        var globalContext = PlatformGlobalContext.builder()
            .methods(new ArrayList<>(List.of(method)))
            .properties(Collections.emptyList())
            .applicationEvents(Collections.emptyList())
            .ordinaryApplicationEvents(Collections.emptyList())
            .sessionModuleEvents(Collections.emptyList())
            .externalConnectionModuleEvents(Collections.emptyList())
            .build();

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(arrayType, stringType, globalContext)));

        var provider = new PlatformContextProvider(storage);

        var globalMethod = provider.getGlobalContext().methods().get(0);
        assertThat(globalMethod.hasReturnValue()).isTrue();
        assertThat(globalMethod.returnValues())
            .extracting(c -> c.name().getName())
            .containsExactly("Массив");
        assertThat(globalMethod.signatures().get(0).parameters().get(0).types())
            .extracting(c -> c.name().getName())
            .containsExactly("Строка");
    }

    @Test
    void resolvesPropertyTypes() {
        var stringType = createSimpleType("Строка", "String");
        var numberType = createSimpleType("Число", "Number");

        var property = PlatformContextProperty.builder()
            .name(new ContextName("ИнформацияОСистеме", "SystemInfo"))
            .accessMode(AccessMode.READ)
            .availabilities(List.of(Availability.SERVER))
            .description("")
            .rawTypes(List.of("Строка", "Число", "НетТакогоТипа"))
            .build();

        var owner = PlatformContextType.builder()
            .name(new ContextName("Системнаяинфа", "SysInfoOwner"))
            .methods(Collections.emptyList())
            .properties(List.of(property))
            .events(Collections.emptyList())
            .constructors(Collections.emptyList())
            .build();

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(stringType, numberType, owner)));

        new PlatformContextProvider(storage);

        assertThat(property.types())
            .extracting(c -> c.name().getName())
            .containsExactly("Строка", "Число");
    }

    @Test
    void resolvesConstructorParameterTypes() {
        var stringType = createSimpleType("Строка", "String");

        var paramSignature = PlatformContextSignatureParameter.builder()
            .name(new ContextName("Имя", ""))
            .isRequired(true)
            .rawTypes(List.of("Строка"))
            .description("Имя.")
            .build();

        var constructor = PlatformContextConstructor.builder()
            .name(new ContextName("Конструктор", ""))
            .description("")
            .parameters(new ArrayList<>(List.of((ContextSignatureParameter) paramSignature)))
            .build();

        var owner = PlatformContextType.builder()
            .name(new ContextName("СомеТайп", "SomeType"))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(Collections.emptyList())
            .constructors(List.of(constructor))
            .build();

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(stringType, owner)));

        new PlatformContextProvider(storage);

        assertThat(constructor.parameters().get(0).types())
            .extracting(c -> c.name().getName())
            .containsExactly("Строка");
    }

    @Test
    void resolvesEventParameterTypes() {
        var stringType = createSimpleType("Строка", "String");

        var paramSignature = PlatformContextSignatureParameter.builder()
            .name(new ContextName("Имя", ""))
            .isRequired(true)
            .rawTypes(List.of("Строка"))
            .description("")
            .build();

        var signature = PlatformContextMethodSignature.builder()
            .name(new ContextName("Основной", ""))
            .parameters(new ArrayList<>(List.of((ContextSignatureParameter) paramSignature)))
            .description("")
            .build();

        var event = PlatformContextEvent.builder()
            .name(new ContextName("ПриОткрытии", "OnOpen"))
            .description("")
            .availabilities(List.of(Availability.SERVER))
            .signatures(new ArrayList<>(List.of((ContextMethodSignature) signature)))
            .build();

        var owner = PlatformContextType.builder()
            .name(new ContextName("Форма", "Form"))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(List.of(event))
            .constructors(Collections.emptyList())
            .build();

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(stringType, owner)));

        new PlatformContextProvider(storage);

        assertThat(event.signatures().get(0).parameters().get(0).types())
            .extracting(c -> c.name().getName())
            .containsExactly("Строка");
    }

    @Test
    void platformPropertyWithAnglePlaceholderIsMarkedGeneric() {
        var genericProperty = PlatformContextProperty.builder()
            .name(new ContextName("<Имя справочника>", "<Catalog name>"))
            .accessMode(AccessMode.READ)
            .availabilities(List.of(Availability.SERVER))
            .description("")
            .rawTypes(Collections.emptyList())
            .build();
        var plainProperty = PlatformContextProperty.builder()
            .name(new ContextName("Имя", "Name"))
            .accessMode(AccessMode.READ)
            .availabilities(List.of(Availability.SERVER))
            .description("")
            .rawTypes(Collections.emptyList())
            .build();

        assertThat(genericProperty.isGeneric()).isTrue();
        assertThat(plainProperty.isGeneric()).isFalse();
    }

    @Test
    void platformTypeWithAnglePlaceholderIsMarkedGeneric() {
        var genericType = PlatformContextType.builder()
            .name(new ContextName("СправочникСсылка.<Имя справочника>", "CatalogRef.<Catalog name>"))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(Collections.emptyList())
            .constructors(Collections.emptyList())
            .build();
        assertThat(genericType.isGeneric()).isTrue();
    }

    @Test
    void globalContextIsRemovedFromContextsList() {
        var globalContext = PlatformGlobalContext.builder()
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .applicationEvents(Collections.emptyList())
            .ordinaryApplicationEvents(Collections.emptyList())
            .sessionModuleEvents(Collections.emptyList())
            .externalConnectionModuleEvents(Collections.emptyList())
            .build();

        var someType = createSimpleType("Тип", "Type");

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(globalContext, someType)));
        var provider = new PlatformContextProvider(storage);

        assertThat(provider.getContexts())
            .doesNotContain((Context) globalContext)
            .contains(someType);
        assertThat(provider.getGlobalContext()).isSameAs(globalContext);
    }

    private PlatformContextType createSimpleType(String name, String alias) {
        return PlatformContextType.builder()
            .name(new ContextName(name, alias))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(Collections.emptyList())
            .constructors(Collections.emptyList())
            .build();
    }
}
