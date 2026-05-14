package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.internal.PlatformContextStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BilingualMerger подмешивает en-имена сигнатур/параметров из en-провайдера
 * в ru-провайдер. Имена самих сущностей (метод/свойство/тип) уже двуязычные
 * через {@link com.github.eightm.lib.Page#title()}, мерджу не подлежат.
 */
class BilingualMergerTest {

    @Test
    void mergesSignatureAndParameterAliases() {
        var ruProvider = providerWithMethod(
            new ContextName("Скопировать", "Copy"),
            new ContextName("Основной", ""),
            new ContextName("Значение", "")
        );
        var enProvider = providerWithMethod(
            new ContextName("Скопировать", "Copy"),
            new ContextName("Main", ""),
            new ContextName("Value", "")
        );

        BilingualMerger.merge(ruProvider, enProvider);

        var ruMethod = ruProvider.getGlobalContext().methods().get(0);
        var sig = ruMethod.signatures().get(0);
        assertThat(sig.name()).isEqualTo(new ContextName("Основной", "Main"));
        var param = sig.parameters().get(0);
        assertThat(param.name()).isEqualTo(new ContextName("Значение", "Value"));
    }

    @Test
    void leavesExistingAliasAlone() {
        var ruProvider = providerWithMethod(
            new ContextName("Скопировать", "Copy"),
            new ContextName("Основной", "AlreadySet"),
            new ContextName("Значение", "")
        );
        var enProvider = providerWithMethod(
            new ContextName("Скопировать", "Copy"),
            new ContextName("Main", ""),
            new ContextName("Value", "")
        );

        BilingualMerger.merge(ruProvider, enProvider);

        var sig = ruProvider.getGlobalContext().methods().get(0).signatures().get(0);
        assertThat(sig.name().getAlias()).isEqualTo("AlreadySet");
    }

    @Test
    void skipsWhenParameterCountDiffers() {
        var ruProvider = providerWithMethod(
            new ContextName("СомеМетод", "SomeMethod"),
            new ContextName("Основной", ""),
            new ContextName("Знач", "")
        );
        // en-провайдер с двумя параметрами вместо одного — не мержим.
        var enMethod = buildMethod(
            new ContextName("СомеМетод", "SomeMethod"),
            new ContextName("Main", ""),
            List.of(
                new ContextName("Value1", ""),
                new ContextName("Value2", "")
            )
        );
        var enProvider = new PlatformContextProvider(new PlatformContextStorage(
            new ArrayList<>(List.of(PlatformGlobalContext.builder()
                .methods(List.of(enMethod))
                .properties(Collections.emptyList())
                .applicationEvents(Collections.emptyList())
                .ordinaryApplicationEvents(Collections.emptyList())
                .sessionModuleEvents(Collections.emptyList())
                .externalConnectionModuleEvents(Collections.emptyList())
                .build()))
        ));

        BilingualMerger.merge(ruProvider, enProvider);

        var param = ruProvider.getGlobalContext().methods().get(0).signatures().get(0).parameters().get(0);
        assertThat(param.name().getAlias()).isEmpty();
    }

    private static PlatformContextProvider providerWithMethod(ContextName method,
                                                              ContextName signature,
                                                              ContextName parameter) {
        var m = buildMethod(method, signature, List.of(parameter));
        var global = PlatformGlobalContext.builder()
            .methods(List.of(m))
            .properties(Collections.emptyList())
            .applicationEvents(Collections.emptyList())
            .ordinaryApplicationEvents(Collections.emptyList())
            .sessionModuleEvents(Collections.emptyList())
            .externalConnectionModuleEvents(Collections.emptyList())
            .build();
        return new PlatformContextProvider(new PlatformContextStorage(new ArrayList<>(List.of(global))));
    }

    private static PlatformContextMethod buildMethod(ContextName methodName,
                                                     ContextName signatureName,
                                                     List<ContextName> parameterNames) {
        var params = new ArrayList<ContextSignatureParameter>();
        for (var pn : parameterNames) {
            params.add(PlatformContextSignatureParameter.builder()
                .name(pn)
                .isRequired(true)
                .rawTypes(Collections.emptyList())
                .description("")
                .build());
        }
        var sig = PlatformContextMethodSignature.builder()
            .name(signatureName)
            .parameters(params)
            .description("")
            .build();
        return PlatformContextMethod.builder()
            .name(methodName)
            .description("")
            .availabilities(List.of(Availability.SERVER))
            .rawReturnValues(Collections.emptyList())
            .signatures(new ArrayList<>(List.of((ContextMethodSignature) sig)))
            .build();
    }
}
