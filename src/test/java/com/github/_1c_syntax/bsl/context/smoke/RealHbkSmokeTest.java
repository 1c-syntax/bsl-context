package com.github._1c_syntax.bsl.context.smoke;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke-тест: распарсить реальный {@code shcntx_ru.hbk} самой свежей
 * установленной платформы и убедиться, что:
 * <ul>
 *   <li>результат не пуст;</li>
 *   <li>в наборе есть известные платформенные типы и примитивы;</li>
 *   <li>generic-эвристика срабатывает на ожидаемых типах;</li>
 *   <li>имена не пустые.</li>
 * </ul>
 * Не часть постоянного CI-прогона — требует установленной платформы.
 * Запуск вручную: {@code BSL_CONTEXT_REAL_HBK=true gradle test --tests *RealHbkSmokeTest*}.
 */
@EnabledIfEnvironmentVariable(named = "BSL_CONTEXT_REAL_HBK", matches = "true")
class RealHbkSmokeTest {

    @Test
    void parseLatestPlatformHbk() throws Exception {
        var install = PlatformFinder.findLatest().orElse(null);
        assertThat(install)
            .as("Platform installation must be present for this smoke test")
            .isNotNull();
        assertThat(Files.isRegularFile(install.syntaxHelperRu()))
            .as("shcntx_ru.hbk must exist at %s", install.syntaxHelperRu())
            .isTrue();

        var workDir = Files.createTempDirectory("bsl-context-smoke-");
        var grabber = PlatformContextGrabber.fromPlatformBin(install.binDir(), workDir);
        long t0 = System.currentTimeMillis();
        grabber.parse();
        long elapsedMs = System.currentTimeMillis() - t0;
        var provider = grabber.getProvider();
        System.out.println("[smoke] parsed " + provider.getContexts().size()
            + " contexts from " + install.binDir() + " in " + elapsedMs + " ms");

        // Минимальный объём.
        assertThat(provider.getContexts().size()).isGreaterThan(500);

        // Глобальный контекст с непустым набором методов и свойств.
        var global = provider.getGlobalContext();
        assertThat(global).isNotNull();
        assertThat(global.methods()).isNotEmpty();
        assertThat(global.properties()).isNotEmpty();

        // Ключевые типы должны быть найдены по обоим языкам.
        assertResolves(provider, "Массив", "Array");
        assertResolves(provider, "Структура", "Structure");
        assertResolves(provider, "Соответствие", "Map");
        assertResolves(provider, "ТаблицаЗначений", "ValueTable");

        // Generic-эвристика должна срабатывать на ожидаемых семействах типов.
        var generics = collectGenericTypeNames(provider);
        assertThat(generics)
            .as("generic types should be detected by isGeneric() heuristic")
            .anyMatch(n -> n.startsWith("СправочникСсылка."))
            .anyMatch(n -> n.startsWith("ДокументСсылка."))
            .anyMatch(n -> n.startsWith("ПеречислениеСсылка."));

        // Имена не должны быть пустыми. Считаем как long, чтобы не плодить
        // огромный assertion-fail message по 2400+ элементам.
        long namelessContexts = provider.getContexts().stream()
            .filter(c -> c.name().getName().isBlank() && c.name().getAlias().isBlank())
            .count();
        assertThat(namelessContexts).isZero();

        // У значений перечислений должно быть хотя бы одно описание,
        // т.к. parseEnumValuePage теперь заполняет description.
        long enumValuesWithDescription = provider.getContexts().stream()
            .filter(c -> c instanceof ContextEnum)
            .map(c -> (ContextEnum) c)
            .flatMap(e -> e.values().stream())
            .filter(v -> !v.description().isBlank())
            .count();
        assertThat(enumValuesWithDescription).isGreaterThan(0);

        // Связь "свойство-плейсхолдер ↔ generic-тип": на namespace-типе
        // СправочникиМенеджер должно быть generic-свойство «<Имя справочника>»,
        // тип которого должен быть разрезолвлен в реальный generic-тип
        // СправочникМенеджер.<Имя справочника>.
        var catalogsManager = provider.getContextByName("СправочникиМенеджер").orElse(null);
        assertThat(catalogsManager).isInstanceOf(ContextType.class);
        var managerType = (ContextType) catalogsManager;
        var genericProp = managerType.properties().stream()
            .filter(ContextProperty::isGeneric)
            .findFirst()
            .orElse(null);
        assertThat(genericProp)
            .as("СправочникиМенеджер должен иметь generic-свойство <Имя справочника>")
            .isNotNull();
        assertThat(genericProp.types())
            .as("тип generic-свойства <Имя справочника> должен резолвиться в generic-тип СправочникМенеджер.<…>")
            .isNotEmpty()
            .anyMatch(t -> t.isGeneric() && t.name().getName().startsWith("СправочникМенеджер."));

        // === ContextCollection: разные кейсы ===

        // 1. Соответствие — элемент типизированный (ссылка → КлючИЗначение),
        //    доступен и обход «Для каждого», и индексатор (по ключу).
        assertCollection(provider, "Соответствие", c -> {
            assertThat(c.collectionElementTypes())
                .as("Соответствие.collectionElementTypes → КлючИЗначение")
                .singleElement()
                .satisfies(t -> assertThat(t.name().getName()).isEqualTo("КлючИЗначение"));
            assertThat(c.supportsForEach()).as("Соответствие supportsForEach").isTrue();
            assertThat(c.forEachDescription())
                .as("у Соответствие при обходе выбираются элементы (boilerplate-prefix отрезан)")
                .contains("выбираются")
                .doesNotContain("Для каждого", "Для объекта доступен");
            assertThat(c.supportsIndexAccess()).as("Соответствие supportsIndexAccess").isTrue();
            assertThat(c.indexAccessDescription())
                .as("у Соответствие в скобки передаётся ключ")
                .contains("ключ")
                .doesNotContain("Возможно обращение");
        });

        // 2. Массив — элемент текстом «Произвольный» (резолвится в синтетический
        //    ArbitraryType), индексатор от 0.
        assertCollection(provider, "Массив", c -> {
            assertThat(c.collectionElementTypes())
                .as("Массив.collectionElementTypes → Произвольный")
                .singleElement()
                .satisfies(t -> {
                    assertThat(t.name().getName()).isEqualTo("Произвольный");
                    assertThat(t.kind())
                        .isEqualTo(com.github._1c_syntax.bsl.context.api.ContextKind.PRIMITIVE_TYPE);
                });
            assertThat(c.supportsForEach()).as("Массив supportsForEach").isTrue();
            assertThat(c.supportsIndexAccess()).as("Массив supportsIndexAccess").isTrue();
            assertThat(c.indexAccessDescription())
                .as("у Массив в [] передаётся индекс (нумерация с 0)")
                .containsAnyOf("индекс", "нумерация");
        });

        // 3. Структура — элемент КлючИЗначение, обход «Для каждого»,
        //    индексатор (по имени ключа).
        assertCollection(provider, "Структура", c -> {
            assertThat(c.collectionElementTypes())
                .extracting(t -> t.name().getName())
                .contains("КлючИЗначение");
            assertThat(c.supportsForEach()).isTrue();
        });

        // 4. ТаблицаЗначений — элемент СтрокаТаблицыЗначений.
        assertCollection(provider, "ТаблицаЗначений", c -> {
            assertThat(c.collectionElementTypes())
                .extracting(t -> t.name().getName())
                .contains("СтрокаТаблицыЗначений");
            assertThat(c.supportsForEach()).isTrue();
        });

        // 5. ЭлементыФормы — несколько типов элементов (5 вариантов).
        assertCollection(provider, "ЭлементыФормы", c -> {
            var names = c.collectionElementTypes().stream()
                .map(t -> t.name().getName())
                .toList();
            assertThat(names)
                .as("ЭлементыФормы — multi-type collection")
                .contains("ГруппаФормы", "ДекорацияФормы", "КнопкаФормы",
                    "ПолеФормы", "ТаблицаФормы");
        });

        // 6. БуферДвоичныхДанных — элемент примитивного типа Число.
        assertCollection(provider, "БуферДвоичныхДанных", c -> {
            assertThat(c.collectionElementTypes())
                .extracting(t -> t.name().getName())
                .contains("Число");
        });

        // 7. ФиксированныйМассив — не коллекция в смысле bsl-context: на странице
        //    блока «Элементы коллекции:» нет, поэтому это обычный ContextType.
        assertThat(provider.getContextByName("ФиксированныйМассив").orElseThrow())
            .as("у ФиксированныйМассив страница СП не содержит блока «Элементы коллекции:» — не ContextCollection")
            .isNotInstanceOf(com.github._1c_syntax.bsl.context.api.ContextCollection.class);
    }

    private static void assertCollection(
        com.github._1c_syntax.bsl.context.api.ContextProvider provider,
        String name,
        java.util.function.Consumer<com.github._1c_syntax.bsl.context.api.ContextCollection> body
    ) {
        var ctx = provider.getContextByName(name).orElseThrow(() ->
            new AssertionError("type not found: " + name));
        assertThat(ctx)
            .as("%s должен быть ContextCollection", name)
            .isInstanceOf(com.github._1c_syntax.bsl.context.api.ContextCollection.class);
        assertThat(ctx.kind())
            .as("%s.kind() == COLLECTION", name)
            .isEqualTo(com.github._1c_syntax.bsl.context.api.ContextKind.COLLECTION);
        body.accept((com.github._1c_syntax.bsl.context.api.ContextCollection) ctx);
    }


    private static void assertResolves(
        com.github._1c_syntax.bsl.context.api.ContextProvider provider,
        String ruName,
        String enName
    ) {
        assertThat(provider.getContextByName(ruName))
            .as("resolve '%s'", ruName)
            .isPresent();
        assertThat(provider.getContextByName(enName))
            .as("resolve '%s'", enName)
            .isPresent();
    }

    private static Set<String> collectGenericTypeNames(
        com.github._1c_syntax.bsl.context.api.ContextProvider provider
    ) {
        var set = new LinkedHashSet<String>();
        for (Context c : provider.getContexts()) {
            if (c.isGeneric()) {
                set.add(c.name().getName());
            }
        }
        return set;
    }
}
