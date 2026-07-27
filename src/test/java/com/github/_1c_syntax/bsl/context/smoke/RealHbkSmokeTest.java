package com.github._1c_syntax.bsl.context.smoke;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextProvider;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
                        .isEqualTo(ContextKind.PRIMITIVE_TYPE);
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
        //    В HBK два типа с этим же ru-именем: FormItems (managed forms,
        //    коллекция) и Controls (обычные формы, не коллекция). Под именем
        //    «ЭлементыФормы» в storage может оказаться любой из них (первый-
        //    побеждает), поэтому здесь выбираем именно FormItems по en-алиасу.
        var formItems = provider.getContextByName("FormItems").orElseThrow();
        assertThat(formItems)
            .as("FormItems должен быть ContextCollection")
            .isInstanceOf(ContextCollection.class);
        assertThat(formItems.kind())
            .isEqualTo(ContextKind.COLLECTION);
        var formItemsNames = ((ContextCollection) formItems)
            .collectionElementTypes().stream()
            .map(t -> t.name().getName())
            .toList();
        assertThat(formItemsNames)
            .as("ЭлементыФормы (FormItems) — multi-type collection")
            .contains("ГруппаФормы", "ДекорацияФормы", "КнопкаФормы",
                "ПолеФормы", "ТаблицаФормы");

        // 6. БуферДвоичныхДанных — элемент примитивного типа Число.
        assertCollection(provider, "БуферДвоичныхДанных", c -> {
            assertThat(c.collectionElementTypes())
                .extracting(t -> t.name().getName())
                .contains("Число");
        });

        // 7. ФиксированныйМассив — в 8.3.27 страница СП не содержит блока
        //    «Элементы коллекции:» (ContextType), но в 8.5.4 блок добавлен —
        //    становится ContextCollection. Проверяем что тип в принципе
        //    распознаётся, без жёсткой фиксации kind.
        assertThat(provider.getContextByName("ФиксированныйМассив")).isPresent();

        // === ContextEnum.valueType ===
        // Для enum-«библиотек» на главной странице есть фраза
        // «Значения этого набора имеют тип X.» — парсер должен извлечь имя X
        // в ContextEnum.valueType(). Проверяем ru-маркер на самой известной
        // (БиблиотекаКартинок → Картинка) + соседние стилевые наборы.
        assertEnumValueType(provider, "БиблиотекаКартинок", "Картинка", "Picture");
        assertEnumValueType(provider, "ЦветаСтиля", "Цвет", "Color");
        assertEnumValueType(provider, "РамкиСтиля", "Рамка", "Border");
        assertEnumValueType(provider, "ШрифтыСтиля", "Шрифт", "Font");
        // Обычное системное перечисление valueType иметь не должно.
        var sysEnum = provider.getContextByName("ВидДвиженияНакопления").orElse(null);
        assertThat(sysEnum).isInstanceOf(ContextEnum.class);
        assertThat(((ContextEnum) sysEnum).valueType())
            .as("у обычного системного перечисления нет valueType")
            .isEmpty();

        // === Параметры формы (секция «Параметры формы:») ===
        // ФормаКлиентскогоПриложения — базовая форма, у неё 6 параметров, из
        // которых ключевые (участвуют в ключе уникальности окна) — ВыборДобавлением
        // и КлючНазначенияИспользования.
        var caf = provider.getContextByName("ФормаКлиентскогоПриложения").orElseThrow();
        assertThat(caf).isInstanceOf(ContextType.class);
        var cafParams = ((ContextType) caf).formParameters();
        assertThat(cafParams)
            .extracting(p -> p.name().getName())
            .contains("ТолькоПросмотр", "КлючНазначенияИспользования",
                "ПараметрыФункциональныхОпций", "ВыборДобавлением");
        assertThat(cafParams)
            .as("en-алиасы параметров формы приходят из TableOfContent")
            .extracting(p -> p.name().getAlias())
            .contains("ReadOnly", "PurposeUseKey");
        assertThat(cafParams)
            .filteredOn(ContextFormParameter::isKey)
            .as("ключевые параметры формы — «Использование: Ключевой»")
            .extracting(p -> p.name().getName())
            .containsExactlyInAnyOrder("ВыборДобавлением", "КлючНазначенияИспользования");

        var readOnlyParam = cafParams.stream()
            .filter(p -> "ТолькоПросмотр".equals(p.name().getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ТолькоПросмотр form parameter not found"));
        assertThat(readOnlyParam.types())
            .as("тип параметра формы должен резолвиться в Context")
            .extracting(c -> c.name().getName())
            .containsExactly("Булево");
        assertThat(readOnlyParam.isKey()).isFalse();
        assertThat(readOnlyParam.description()).isNotBlank();
        assertThat(readOnlyParam.sinceVersion()).isNotBlank();

        // en-описания параметров формы подтягиваются BilingualMerger'ом.
        if (provider instanceof PlatformContextProvider p) {
            assertThat(p.getDescriptionEn(readOnlyParam))
                .as("en-описание параметра формы из shcntx_root.hbk")
                .isNotBlank();
        }

        // Расширение формы для справочника — ключевой параметр «Ключ» с
        // generic-типом СправочникСсылка.<…>: проверяем, что резолвятся не
        // только примитивы. Имя берётся с заголовка страницы («Расширение формы
        // клиентского приложения для справочника»), в оглавлении узел назван
        // короче — «Расширение справочника».
        // Страница этого типа единственная в HBK, чьё имя файла подпадало под
        // старую эвристику isCatalogPage («…extension for catalogs.html»), из-за
        // чего весь тип молча терялся — здесь заодно и регрессионная проверка.
        var catalogFormExt = provider
            .getContextByName("Расширение формы клиентского приложения для справочника")
            .orElse(null);
        assertThat(catalogFormExt)
            .as("тип «Расширение справочника» должен присутствовать в модели")
            .isInstanceOf(ContextType.class);
        var keyParam = ((ContextType) catalogFormExt)
            .formParameters().stream()
            .filter(ContextFormParameter::isKey)
            .findFirst()
            .orElseThrow(() -> new AssertionError("key form parameter not found on catalog form extension"));
        assertThat(keyParam.name().getName()).isEqualTo("Ключ");
        assertThat(keyParam.types())
            .extracting(c -> c.name().getName())
            .anyMatch(n -> n.startsWith("СправочникСсылка."));

        // Формы — не «случайная» горстка: секция есть у полутора десятков типов
        // (в 8.3.27 — ровно 20: ФормаКлиентскогоПриложения, 14 расширений формы,
        // 3 системные формы истории данных, формы сохранения и загрузки настроек).
        var typesWithFormParameters = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .filter(t -> !t.formParameters().isEmpty())
            .toList();
        assertThat(typesWithFormParameters).hasSizeGreaterThanOrEqualTo(15);
        // У обычных типов секции нет — список пуст, но не null.
        assertThat(((ContextType) provider.getContextByName("Массив").orElseThrow()).formParameters())
            .isEmpty();

        // === Имя берётся с заголовка страницы, а не из оглавления ===
        // В оглавлении узел назван относительно родителя («Поле ввода» →
        // «Расширение»), что вне дерева бессмысленно и неуникально.
        assertThat(provider.getContextByName("Расширение поля ввода системного перечисления"))
            .as("имя типа — полное, с заголовка страницы")
            .isPresent();
        assertThat(provider.getContextByName("Расширение"))
            .as("контекстное имя из оглавления больше не публикуется")
            .isEmpty();
        assertThat(provider.getContextByName("Расширение поля формы для поля картинки")).isPresent();
        assertThat(provider.getContextByName("Расширение декорации формы для картинки")).isPresent();
        // Обычные типы, перечисления и generic'и от этого не пострадали.
        assertThat(provider.getContextByName("Массив")).isPresent();
        assertThat(provider.getContextByName("ГруппировкаКолонок")).isPresent();
        assertThat(provider.getContexts())
            .as("generic-имена разбираются штатно (скобки в них — не en-часть)")
            .anyMatch(c -> c.name().getName().startsWith("СправочникСсылка.<"));
        assertThat(provider.getContexts())
            .as("в имя не должна попадать en-часть заголовка «Имя (Name)»")
            .noneMatch(c -> c.name().getName().contains("("));

        // Дублей имён должно стать заметно меньше, чем при именах из оглавления
        // (там их было 27); оставшиеся — честные омонимы платформы.
        var duplicateRuNames = provider.getContexts().stream()
            .collect(Collectors.groupingBy(
                c -> c.name().getName().toLowerCase(), Collectors.counting()))
            .values().stream().filter(n -> n > 1).count();
        assertThat(duplicateRuNames).isLessThanOrEqualTo(12);

        // Омонимы: «Расширение элементов управления, расположенных в форме» есть
        // и для обычных форм (8.0, 11 свойств), и для управляемых (8.2). Имена
        // совпадают целиком, поэтому getContextByName отдаёт какой-то один —
        // все доступны через getContextsByName.
        var homonyms = provider.getContextsByName(
            "Расширение элементов управления, расположенных в форме");
        assertThat(homonyms).as("омонимы не теряются").hasSize(2);
        assertThat(homonyms).extracting(Context::sinceVersion)
            .containsExactlyInAnyOrder("8.0", "8.2");
        assertThat(provider.getContextByName(
            "Расширение элементов управления, расположенных в форме")).isPresent();
        // У типа без омонимов список из одного элемента.
        assertThat(provider.getContextsByName("Массив")).hasSize(1);
        assertThat(provider.getContextsByName("Array")).hasSize(1);
        assertThat(provider.getContextsByName("НетТакогоТипа")).isEmpty();

        // Переименование: на PlannerCommandSource.html ведут два узла оглавления
        // — «ИсточникКомандПланировщика» (устарел с 8.3.23) и
        // «ИсточникКомандПоляПланировщика». Это разные перечисления с разными
        // наборами значений, и оба должны сохраниться.
        var oldPlannerSource = provider.getContextByName("ИсточникКомандПланировщика")
            .orElseThrow(() -> new AssertionError("ИсточникКомандПланировщика не найден"));
        var newPlannerSource = provider.getContextByName("ИсточникКомандПоляПланировщика")
            .orElseThrow(() -> new AssertionError("ИсточникКомандПоляПланировщика не найден"));
        assertThat(oldPlannerSource).isNotSameAs(newPlannerSource);
        assertThat(((ContextEnum) oldPlannerSource).values())
            .as("у устаревшего перечисления все значения помечены deprecated")
            .isNotEmpty()
            .allMatch(v -> !v.deprecatedSinceVersion().isBlank());
        assertThat(((ContextEnum) newPlannerSource).values())
            .as("у нового перечисления значения актуальны")
            .isNotEmpty()
            .allMatch(v -> v.deprecatedSinceVersion().isBlank());

        // === Страничные метаданные типа ===
        // Со страницы типа снимаются не только «Описание:», но и доступность,
        // версия появления, «Пример:» и «См. также:».
        var arrayCtx = (ContextType) provider.getContextByName("Массив").orElseThrow();
        assertThat(arrayCtx.sinceVersion())
            .as("Массив доступен с 8.0 — версия со страницы типа")
            .isEqualTo("8.0");
        assertThat(arrayCtx.availabilities())
            .as("доступность типа со страницы «Доступность:»")
            .contains(Availability.SERVER, Availability.THIN_CLIENT, Availability.THICK_CLIENT);
        assertThat(arrayCtx.examples())
            .as("на странице Массив есть блок «Пример:»")
            .isNotEmpty();
        assertThat(arrayCtx.deprecatedSinceVersion()).isEmpty();

        // Не единичный случай: версии и доступность должны быть у большинства типов.
        var typesWithVersion = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .filter(t -> !t.sinceVersion().isBlank())
            .count();
        assertThat(typesWithVersion)
            .as("версия появления снимается со страниц типов массово")
            .isGreaterThan(1000);
        var typesWithAvailability = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .filter(t -> !t.availabilities().isEmpty())
            .count();
        assertThat(typesWithAvailability).isGreaterThan(1000);
        var typesWithSeeAlso = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .filter(t -> !t.seeAlso().isEmpty())
            .count();
        assertThat(typesWithSeeAlso).isGreaterThan(100);

        // === Страничные метаданные перечисления ===
        var columnsGroup = provider.getContextByName("ГруппировкаКолонок").orElseThrow();
        assertThat(columnsGroup).isInstanceOf(ContextEnum.class);
        assertThat(columnsGroup.description())
            .as("описание перечисления со страницы")
            .contains("группировки колонок");
        assertThat(columnsGroup.availabilities()).contains(Availability.SERVER);
        assertThat(columnsGroup.sinceVersion()).isEqualTo("8.2");
        assertThat(columnsGroup.seeAlso())
            .as("«См. также:» перечисления квалифицируется владельцем")
            .isNotEmpty();

        // === Заметки / примеры / «См. также» у членов ===
        // События: «Примечание:» и «См. также:» раньше парсились и терялись.
        var formEvents = ((ContextType) caf).events();
        assertThat(formEvents)
            .as("у события формы должна быть хотя бы одна заметка")
            .anyMatch(e -> !e.notes().isBlank());
        assertThat(formEvents).anyMatch(e -> !e.seeAlso().isEmpty());

        // Конструкторы: «Пример:» и «См. также:» (блока «Доступность:» у них в HBK нет).
        var allConstructors = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .flatMap(t -> t.constructors().stream())
            .toList();
        assertThat(allConstructors).anyMatch(c -> !c.examples().isEmpty());
        assertThat(allConstructors).anyMatch(c -> !c.seeAlso().isEmpty());

        // Свойства: «См. также:» и отделённая заметка.
        var allProperties = provider.getContexts().stream()
            .filter(ContextType.class::isInstance)
            .map(ContextType.class::cast)
            .flatMap(t -> t.properties().stream())
            .toList();
        assertThat(allProperties).anyMatch(p -> !p.seeAlso().isEmpty());
        assertThat(allProperties).anyMatch(p -> !p.notes().isBlank());

        // === Generic-методы: возврат должен резолвиться ===
        // HtmlParser в секции «Возвращаемое значение:» аккумулирует фрагменты
        // одной строки «Тип: …» (включая <a>-ссылки и угловые скобки в
        // <span>'ах), и разрезает по запятым только на flush'е. Без этой
        // склейки «СправочникОбъект.<Имя справочника>» распадался на 4
        // отдельных returnValue (одни не резолвились), и метод выглядел как
        // возвращающий только «Неопределено».
        var refGeneric = provider.getContexts().stream()
            .filter(c -> c instanceof ContextType)
            .map(c -> (ContextType) c)
            .filter(t -> t.name().getName().startsWith("СправочникСсылка.<"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("СправочникСсылка.<…> generic not found"));
        var getObject = refGeneric.methods().stream()
            .filter(m -> m.name().getName().equalsIgnoreCase("ПолучитьОбъект"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ПолучитьОбъект method not found"));
        assertThat(getObject.returnValues())
            .as("ПолучитьОбъект должен возвращать СправочникОбъект.<…> + Неопределено")
            .extracting(c -> c.name().getName())
            .anyMatch(n -> n.startsWith("СправочникОбъект.<"))
            .contains("Неопределено");

        var managerGeneric = provider.getContexts().stream()
            .filter(c -> c instanceof ContextType)
            .map(c -> (ContextType) c)
            .filter(t -> t.name().getName().startsWith("СправочникМенеджер.<"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("СправочникМенеджер.<…> not found"));
        var findByCode = managerGeneric.methods().stream()
            .filter(m -> m.name().getName().equalsIgnoreCase("НайтиПоКоду"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("НайтиПоКоду not found"));
        assertThat(findByCode.returnValues())
            .as("НайтиПоКоду должен возвращать СправочникСсылка.<…> + Неопределено")
            .extracting(c -> c.name().getName())
            .anyMatch(n -> n.startsWith("СправочникСсылка.<"))
            .contains("Неопределено");

        // ПустаяСсылка: единственный тип возврата, после `>` идёт хвостовая
        // точка «Тип: СправочникСсылка.<Имя справочника>.» — flushTypeLine
        // должен срезать её, иначе rawReturnValues остаётся с лишней точкой
        // и typeIndex.get(...) не резолвится (returnValues пуст).
        var emptyRef = managerGeneric.methods().stream()
            .filter(m -> m.name().getName().equalsIgnoreCase("ПустаяСсылка"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("ПустаяСсылка not found on СправочникМенеджер"));
        assertThat(emptyRef.returnValues())
            .as("ПустаяСсылка должна возвращать СправочникСсылка.<…> (без хвостовой точки)")
            .extracting(c -> c.name().getName())
            .anyMatch(n -> n.startsWith("СправочникСсылка.<"));

        // === Constructor parameters ===
        // Массив имеет два конструктора — variadic «По количеству элементов»
        // и единичный «На основании фиксированного массива». Парсер должен
        // отдать ОБА, чтобы LS-side type-aware подбор работал.
        var arrayType = (ContextType) provider.getContextByName("Массив").orElseThrow();
        assertThat(arrayType.constructors())
            .as("у Массив два конструктора (variadic + по фиксированному массиву)")
            .hasSizeGreaterThanOrEqualTo(2);
        var paramNames = arrayType.constructors().stream()
            .flatMap(c -> c.parameters().stream())
            .map(p -> p.name().getName())
            .toList();
        // В 8.3.27 variadic-параметр имел нотацию X1,...,XN; в 8.5.4
        // конструктор Массив описан в СП как «КоличествоЭлементов1[, …]» —
        // нотация ушла. Проверяем только что параметры в принципе есть.
        assertThat(paramNames).isNotEmpty();
    }

    private static void assertCollection(
        ContextProvider provider,
        String name,
        Consumer<ContextCollection> body
    ) {
        var ctx = provider.getContextByName(name).orElseThrow(() ->
            new AssertionError("type not found: " + name));
        assertThat(ctx)
            .as("%s должен быть ContextCollection", name)
            .isInstanceOf(ContextCollection.class);
        assertThat(ctx.kind())
            .as("%s.kind() == COLLECTION", name)
            .isEqualTo(ContextKind.COLLECTION);
        body.accept((ContextCollection) ctx);
    }


    private static void assertEnumValueType(
        ContextProvider provider,
        String enumName,
        String expectedRu,
        String expectedEn
    ) {
        var ctx = provider.getContextByName(enumName).orElseThrow(() ->
            new AssertionError("enum not found: " + enumName));
        assertThat(ctx)
            .as("%s должен быть ContextEnum", enumName)
            .isInstanceOf(ContextEnum.class);
        var vt = ((ContextEnum) ctx).valueType();
        assertThat(vt)
            .as("%s.valueType() должен быть распознан со страницы", enumName)
            .isPresent();
        var n = vt.orElseThrow();
        assertThat(n.getName()).as("%s.valueType().ru", enumName).isEqualTo(expectedRu);
        assertThat(n.getAlias())
            .as("%s.valueType().en — должен резолвиться через bindBilingualValueType", enumName)
            .isEqualTo(expectedEn);
    }

    private static void assertResolves(
        ContextProvider provider,
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
        ContextProvider provider
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
