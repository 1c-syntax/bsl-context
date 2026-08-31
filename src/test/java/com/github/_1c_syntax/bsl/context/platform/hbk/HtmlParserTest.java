package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github.eightm.lib.DoubleLanguageString;
import com.github.eightm.lib.Page;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link HtmlParser} на обезличенных фикстурах, повторяющих
 * HTML-разметку реального синтакс-помощника платформы 1С
 * (классы {@code V8SH_pagetitle}, {@code V8SH_title}, {@code V8SH_heading},
 * {@code V8SH_chapter}, {@code V8SH_rubric}, экранирование угловых скобок).
 * Имена и описания — нейтральные плейсхолдеры.
 */
class HtmlParserTest {

    // --- properties ---

    @Test
    void parsePropertyPage_ReadOnly_MultipleTypes() throws URISyntaxException {
        var property = parsePropertyPage("properties/property_read_only");
        assertThat(property)
            .hasFieldOrPropertyWithValue("accessMode", "Только чтение")
            .hasFieldOrPropertyWithValue("types", List.of("Число", "Строка"))
            .hasFieldOrPropertyWithValue("sinceVersion", "8.0");
        assertThat(property.getDescription()).contains("Описание для теста");
        // XDTO-блурб после <BR> в той же <p> не должен попадать в availabilities.
        assertThat(property.getAvailabilities())
            .containsExactly("Сервер", "толстый клиент", "внешнее соединение");
    }

    @Test
    void parsePropertyPage_NotesAndSeeAlso() throws URISyntaxException {
        // «Примечание:» после «Описание:» — отдельная заметка, а не хвост
        // описания; «См. также:» отдаётся квалифицированным именем члена.
        var property = parsePropertyPage("properties/property_with_notes_and_see_also");
        assertThat(property.getDescription())
            .contains("Описание для теста")
            .doesNotContain("Заметка для теста");
        assertThat(property.getNotes()).contains("Заметка для теста");
        // Без pageIndex владелец не подставляется — остаётся имя члена.
        assertThat(property.getSeeAlso()).containsExactly("Создать");
        assertThat(property.getTypes()).containsExactly("Булево");
    }

    @Test
    void parsePropertyPage_Generic() throws URISyntaxException {
        // Свойство с именем-плейсхолдером «<Имя элемента>» — generic. Парсер должен
        // корректно вытащить структурные поля; флаг isGeneric() проверяется на уровне
        // ContextProperty (см. ContextNamesTest и PlatformContextProperty).
        var property = parsePropertyPage("properties/property_generic");
        assertThat(property)
            .hasFieldOrPropertyWithValue("accessMode", "Только чтение")
            .hasFieldOrPropertyWithValue("sinceVersion", "8.0");
        // Имя типа содержит generic-плейсхолдер — приходит как есть, без потерь.
        assertThat(property.getTypes()).containsExactly("МенеджерЭлемента.<Имя элемента>");
    }

    @Test
    void parsePropertyPage_CollectionElementType_ru() throws URISyntaxException {
        // Свойство-коллекция: после блока «Тип:» в описании идёт маркер
        // «Элементами коллекции являются объекты типа <NAME>», по которому парсер
        // извлекает per-property element-type (qualifiedName с двоеточием).
        var property = parsePropertyPage("properties/property_collection_element_ru");
        assertThat(property.getTypes()).containsExactly("КоллекцияЭлементов");
        assertThat(property.getRawCollectionElementTypes())
            .containsExactly("ОписаниеЭлемента: Виджет");
    }

    @Test
    void parsePropertyPage_CollectionElementType_en() throws URISyntaxException {
        var property = parsePropertyPage("properties/property_collection_element_en");
        assertThat(property.getTypes()).containsExactly("CollectionOfItems");
        assertThat(property.getRawCollectionElementTypes())
            .containsExactly("ItemDescription: Widget");
    }

    @Test
    void parsePropertyPage_ReadWrite_SingleType() throws URISyntaxException {
        var property = parsePropertyPage("properties/property_read_write");
        assertThat(property)
            .hasFieldOrPropertyWithValue("accessMode", "Чтение и запись")
            .hasFieldOrPropertyWithValue("types", List.of("Строка"))
            .hasFieldOrPropertyWithValue("sinceVersion", "8.3");
        assertThat(property.getDescription()).contains("Описание для теста");
        assertThat(property.getAvailabilities()).containsExactly("Сервер");
    }

    // --- methods ---

    @Test
    void parseMethodPage_NoParamsNoReturn() throws URISyntaxException {
        var method = parseMethodPage("methods/method_no_params_no_return");
        assertThat(method.getDescription()).contains("Описание для теста");
        assertThat(method.getReturnValues()).isEmpty();
        assertThat(method.getAvailabilities()).containsExactly("Сервер", "толстый клиент");
        assertThat(method.getSinceVersion()).isEqualTo("8.0");
        assertThat(method.getSignatures()).hasSize(1);

        var sig = method.getSignatures().get(0);
        // Для метода без перегрузок имя варианта пустое — содержательного
        // имени в HBK нет, а двуязычный мерджер не должен встречать строку
        // «Основной» как фиктивное имя в обеих HBK.
        assertThat(sig).hasFieldOrPropertyWithValue("name", "");
        assertThat(sig.getParameters()).isEmpty();
    }

    @Test
    void parseMethodPage_RequiredParam_WithReturn() throws URISyntaxException {
        var method = parseMethodPage("methods/method_with_required_param");
        assertThat(method.getDescription()).contains("Описание для теста");
        assertThat(method.getReturnValues()).containsExactly("Булево");

        var params = method.getSignatures().get(0).getParameters();
        assertThat(params).hasSize(1);
        assertThat(params.get(0))
            .hasFieldOrPropertyWithValue("name", "Значение")
            .hasFieldOrPropertyWithValue("isRequired", true)
            .hasFieldOrPropertyWithValue("types", List.of("Произвольный"));
    }

    @Test
    void parseMethodPage_OptionalParams() throws URISyntaxException {
        var method = parseMethodPage("methods/method_with_optional_params");
        assertThat(method.getReturnValues()).containsExactly("Произвольный");

        var signature = method.getSignatures().get(0);
        assertThat(signature.getSyntaxText()).contains("Извлечь(", "Имя", "ЗначениеПоУмолчанию", ")");

        var params = signature.getParameters();
        assertThat(params).hasSize(2);
        assertThat(params.get(0))
            .hasFieldOrPropertyWithValue("name", "Имя")
            .hasFieldOrPropertyWithValue("isRequired", true)
            .hasFieldOrPropertyWithValue("types", List.of("Строка"))
            .hasFieldOrPropertyWithValue("defaultValue", "");
        assertThat(params.get(1))
            .hasFieldOrPropertyWithValue("name", "ЗначениеПоУмолчанию")
            .hasFieldOrPropertyWithValue("isRequired", false)
            .hasFieldOrPropertyWithValue("types", List.of("Произвольный"))
            .hasFieldOrPropertyWithValue("defaultValue", "Неопределено");
    }

    @Test
    void parseMethodPage_Overloads() throws URISyntaxException {
        var method = parseMethodPage("methods/method_with_overloads");
        assertThat(method.getDescription()).contains("Описание для теста");
        assertThat(method.getReturnValues()).containsExactly("Произвольный");
        assertThat(method.getSignatures()).hasSize(2);

        var byIndex = method.getSignatures().get(0);
        assertThat(byIndex).hasFieldOrPropertyWithValue("name", "По индексу");
        assertThat(byIndex.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Индекс")
            .hasFieldOrPropertyWithValue("types", List.of("Число"));

        var byKey = method.getSignatures().get(1);
        assertThat(byKey).hasFieldOrPropertyWithValue("name", "По ключу");
        assertThat(byKey.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Ключ")
            .hasFieldOrPropertyWithValue("types", List.of("Строка"));
    }

    @Test
    void parseMethodPage_ExamplesSeeAlsoReturnDescription() throws URISyntaxException {
        var method = parseMethodPage("methods/method_with_examples_and_see_also");
        assertThat(method.getReturnValues()).containsExactly("Булево");
        assertThat(method.getReturnValueDescription())
            .contains("Истина", "проверка пройдена", "Ложь");
        assertThat(method.getExamples()).hasSize(1);
        assertThat(method.getExamples().get(0)).contains("Результат = Виджет.Проверить();");
        assertThat(method.getSeeAlso()).containsExactly("Очистить", "Получить");
        assertThat(method.getNotes()).contains("Безопасный режим", "может быть отключён");
    }

    @Test
    void parseMethodPage_PrimechanieAfterDescriptionGoesToNotes() throws URISyntaxException {
        // Реальный HBK: ru-заметка лежит под «Примечание:» (пара к en «Note:»),
        // при этом на странице есть отдельная «Описание:». «Примечание:» должно
        // попасть в notes, а не в описание; ссылка <a>Неопределено</a> внутри
        // заметки не должна задваиваться.
        var method = parseMethodPage("methods/method_with_note_primechanie");
        assertThat(method.getDescription())
            .contains("Описание для теста")
            .doesNotContain("Неопределено");
        assertThat(method.getNotes())
            .contains("Если", "Неопределено", "пустая строка")
            .doesNotContain("НеопределеноНеопределено");
    }

    @Test
    void parseMethodPage_RecommendedReplacementsQualifiedByOwnerFromHref() throws URISyntaxException {
        // Текст <a> несёт только имя члена, владелец — в href. По индексу путей
        // владелец резолвится в локализованное имя: "МенеджерОбработкиОшибок.…".
        var parser = newParser();
        parser.setPageIndex(Map.of(
            "objects/cat/ErrorProcessingManager.html",
            new DoubleLanguageString("ErrorProcessingManager", "МенеджерОбработкиОшибок")));
        var method = parser.parseMethodPage(page("/methods/method_recommended_qualified.html"));
        assertThat(method.getRecommendedReplacements())
            .containsExactly("МенеджерОбработкиОшибок.ПодробноеПредставлениеОшибки");
    }

    @Test
    void parseMethodPage_SeeAlsoPairsOwnerAndMember() throws URISyntaxException {
        // Пара <a>Владелец</a>, метод <a>Член</a>: владелец-ссылка не дублируется.
        // Для глобального контекста префикс владельца опускается (метод доступен
        // по голому имени), standalone-тип отдаётся как есть.
        var parser = newParser();
        parser.setPageIndex(Map.of(
            "objects/cat/GlobalContext.html",
            new DoubleLanguageString("Global context", "Глобальный контекст")));
        var method = parser.parseMethodPage(page("/methods/method_seealso_qualified.html"));
        assertThat(method.getSeeAlso())
            .containsExactly("НачатьПомещениеФайлов", "ПростоТип");
    }

    @Test
    void parseMethodPage_Deprecated() throws URISyntaxException {
        var method = parseMethodPage("methods/method_deprecated");
        // Несколько <p class="V8SH_versionInfo"> на странице: «Доступен…» и «Не рекомендуется…».
        // Парсер должен различать их по префиксу и заполнять разные поля.
        assertThat(method.getSinceVersion()).isEqualTo("8.3.11");
        assertThat(method.getDeprecatedSinceVersion()).isEqualTo("8.3.15");
        // «Рекомендуется использовать:» — содержимое блока __DEPRECATED_SHOW_STYLE__
        // должно собирать имена из вложенных <a>-ссылок.
        assertThat(method.getRecommendedReplacements())
            .containsExactly("НовыйМетод", "ЕщёОдинМетод");
    }

    // --- заголовок страницы «Имя (Name)» ---

    @Test
    void splitBilingualTitle() {
        assertThat(HtmlParser.splitBilingualTitle("Массив (Array)"))
            .containsExactly("Массив", "Array");
        // Полное имя расширения — то, ради чего имя берётся со страницы,
        // а не из оглавления (там узел назван просто «Расширение»).
        assertThat(HtmlParser.splitBilingualTitle(
            "Расширение поля ввода системного перечисления (System enumeration text box extension)"))
            .containsExactly("Расширение поля ввода системного перечисления",
                "System enumeration text box extension");
        // Generic: скобки placeholder'ов не мешают.
        assertThat(HtmlParser.splitBilingualTitle(
            "СправочникСсылка.<Имя справочника> (CatalogRef.<Catalog name>)"))
            .containsExactly("СправочникСсылка.<Имя справочника>", "CatalogRef.<Catalog name>");
        // Вложенные скобки в en-части.
        assertThat(HtmlParser.splitBilingualTitle("Имя (Name (extra))"))
            .containsExactly("Имя", "Name (extra)");
        // Кириллица в скобках — это не перевод, а часть самого имени.
        assertThat(HtmlParser.splitBilingualTitle("Расширение (устаревшее)"))
            .containsExactly("Расширение (устаревшее)", "");
        // en-страница: заголовок без скобок.
        assertThat(HtmlParser.splitBilingualTitle("ClientApplicationForm"))
            .containsExactly("ClientApplicationForm", "");
    }

    // --- type page (страничные метаданные) ---

    @Test
    void parseTypePage_FullMetadata() throws URISyntaxException {
        // Страница типа несёт не только «Описание:», но и доступность, версию,
        // заметку, пример и «См. также:» — всё это должно доезжать до модели.
        var page = page("/types/type_page_full.html");
        var info = newParser().parseTypePage(page);
        assertThat(info.getPageTitleRu()).isEqualTo("Виджет");
        assertThat(info.getPageTitleEn()).isEqualTo("Widget");
        assertThat(info.getDescription()).contains("Описание для теста");
        assertThat(info.getNotes()).contains("Заметка для теста");
        assertThat(info.getAvailabilities())
            .containsExactly("Сервер", "толстый клиент", "внешнее соединение");
        assertThat(info.getSinceVersion()).isEqualTo("8.1");
        assertThat(info.getDeprecatedSinceVersion()).isEmpty();
        assertThat(info.getExamples()).hasSize(1);
        assertThat(info.getExamples().get(0)).contains("Новый Виджет()");
        // Пара <a> «владелец + член» отдаётся одной записью: type-ссылка —
        // владелец следующего члена, поэтому не дублируется. Квалификация
        // «Владелец.Член» требует pageIndex (см. отдельный тест на методах),
        // без него остаётся голое имя члена.
        assertThat(info.getSeeAlso()).containsExactly("Создать");
        // Навигационные чаптеры «Методы:»/«Конструкторы:» в текст не попадают.
        assertThat(info.getDescription()).doesNotContain("Добавить", "По имени");
        assertThat(info.getCollectionInfo().isEmpty()).isTrue();
    }

    @Test
    void parseTypePage_Deprecated() throws URISyntaxException {
        var info = newParser().parseTypePage(page("/types/type_page_deprecated.html"));
        assertThat(info.getSinceVersion()).isEqualTo("8.0");
        assertThat(info.getDeprecatedSinceVersion()).isEqualTo("8.3");
        assertThat(info.getRecommendedReplacements()).containsExactly("Виджет");
        assertThat(info.getExamples()).isEmpty();
    }

    // --- enum page (страничные метаданные) ---

    @Test
    void parseEnumPage_PageMetadata() throws URISyntaxException {
        // У перечисления страница такая же, как у типа: описание + доступность.
        var description = newParser().parseEnumPage(page("/enums/enum_page_with_value_type_ru.html"));
        assertThat(description.getValueType()).isEqualTo("Картинка");
        assertThat(description.getDescription()).contains("Определяет набор тестовых объектов");
        assertThat(description.getAvailabilities()).containsExactly("Сервер");
    }

    // --- form parameters ---

    @Test
    void parseFormParameterPage_Key() throws URISyntaxException {
        // Страница параметра формы размечена как страница свойства, но чаптер
        // «Использование:» несёт не режим доступа, а признак ключевого параметра.
        var param = parseFormParameterPage("formparams/form_parameter_key");
        assertThat(param)
            .hasFieldOrPropertyWithValue("key", true)
            .hasFieldOrPropertyWithValue("types", List.of("Строка"))
            .hasFieldOrPropertyWithValue("sinceVersion", "8.2")
            .hasFieldOrPropertyWithValue("deprecatedSinceVersion", "");
        assertThat(param.getDescription()).contains("Описание для теста");
    }

    @Test
    void parseFormParameterPage_KeyMarker_en() throws URISyntaxException {
        // en-маркер ключевого параметра — «Usage: Key Parameter».
        var param = parseFormParameterPage("formparams/form_parameter_key_en");
        assertThat(param)
            .hasFieldOrPropertyWithValue("key", true)
            .hasFieldOrPropertyWithValue("types", List.of("String"));
    }

    @Test
    void parseFormParameterPage_Plain_MultipleTypes() throws URISyntaxException {
        // Без чаптера «Использование:» параметр не ключевой; секция «См. также:»
        // не должна протекать в описание.
        var param = parseFormParameterPage("formparams/form_parameter_plain");
        assertThat(param)
            .hasFieldOrPropertyWithValue("key", false)
            .hasFieldOrPropertyWithValue("types", List.of("Число", "Строка"))
            .hasFieldOrPropertyWithValue("sinceVersion", "8.3");
        assertThat(param.getDescription())
            .contains("Описание для теста")
            .doesNotContain("свойство");
    }

    // --- таблицы языка запросов ---

    @Test
    void parseQueryTablePage() throws URISyntaxException {
        // Чаптеры «Синтаксис», «Поля» и «Параметры» здесь без двоеточия —
        // разметка таблиц отличается от страниц типов.
        var info = newParser().parseQueryTablePage(page("/querytables/query_table.html"));
        assertThat(info.getPageTitleRu()).isEqualTo("РегистрТестовый.<Имя регистра>.СрезПоследних");
        assertThat(info.getPageTitleEn()).isEqualTo("TestRegister.<Имя регистра>.SliceLast");
        assertThat(info.getSyntaxText()).contains("РегистрТестовый.<Имя регистра>.СрезПоследних");
        assertThat(info.getDescription()).contains("Описание для теста");
        // Списки полей и параметров берутся из дерева, в описание не попадают.
        assertThat(info.getDescription()).doesNotContain("Активность", "Период");
    }

    @Test
    void parseQueryTableFieldPage() throws URISyntaxException {
        var field = newParser().parseQueryTableFieldPage(page("/querytables/query_table_field.html"));
        assertThat(field.getPageTitleRu()).isEqualTo("Активность");
        assertThat(field.getPageTitleEn()).isEqualTo("Active");
        assertThat(field.getTypes()).containsExactly("Булево");
        assertThat(field.getDescription()).contains("Описание для теста");
        assertThat(field.getNotes()).contains("Заметка для теста");
    }

    @Test
    void parseQueryTableParameterPage() throws URISyntaxException {
        // Обязательность и имя — в заголовке-чаптере, типы — в «Тип параметра:».
        var param = newParser().parseQueryTableParameterPage(page("/querytables/query_table_param.html"));
        assertThat(param.getName()).isEqualTo("Период");
        assertThat(param.isRequired()).isFalse();
        assertThat(param.getTypes()).containsExactly("Дата", "МоментВремени");
        assertThat(param.getDescription()).contains("Описание для теста");
    }

    @Test
    void parseQueryTableParameterPage_RequiredWithoutTypes() throws URISyntaxException {
        // Без «(необязательный)» параметр обязательный; блока «Тип параметра:»
        // может не быть — параметр принимает выражение языка запросов.
        var param = newParser().parseQueryTableParameterPage(
            page("/querytables/query_table_param_required.html"));
        assertThat(param.getName()).isEqualTo("Условие");
        assertThat(param.isRequired()).isTrue();
        assertThat(param.getTypes()).isEmpty();
        assertThat(param.getDescription()).contains("Конструкция языка запросов");
    }

    // --- enum value ---

    @Test
    void parseEnumValuePage() throws URISyntaxException {
        var page = page("/enums/enum_value_active.html");
        var ev = newParser().parseEnumValuePage(page);
        assertThat(ev.getDescription()).contains("Виджет в активном режиме");
        assertThat(ev.getSinceVersion()).isEqualTo("8.0");
        assertThat(ev.getDeprecatedSinceVersion()).isEmpty();
    }

    // --- enum page (main page; valueType marker) ---

    @Test
    void parseEnumPage_ru_extractsValueType() throws URISyntaxException {
        // Главная страница enum-«библиотеки»: «Значения этого набора имеют тип <a>X</a>.»
        // — задаёт общий тип всех значений набора. Парсер извлекает имя из ссылки.
        var page = page("/enums/enum_page_with_value_type_ru.html");
        var description = newParser().parseEnumPage(page);
        assertThat(description.getValueType()).isEqualTo("Картинка");
    }

    @Test
    void parseEnumPage_en_extractsValueType() throws URISyntaxException {
        // EN-вариант маркера: «Values of this set have the type <a>X</a>.»
        var page = page("/enums/enum_page_with_value_type_en.html");
        var description = newParser().parseEnumPage(page);
        assertThat(description.getValueType()).isEqualTo("Picture");
    }

    @Test
    void parseEnumPage_withoutMarker_leavesValueTypeEmpty() throws URISyntaxException {
        // Обычное системное перечисление (ВидДвиженияНакопления и т.п.) на странице
        // фразы про общий тип не имеет — valueType остаётся пустым.
        var page = page("/enums/enum_page_without_value_type.html");
        var description = newParser().parseEnumPage(page);
        assertThat(description.getValueType()).isEmpty();
    }

    // --- events ---

    @Test
    void parseMethodPage_EventLikeNoReturn_WithParams() throws URISyntaxException {
        // События парсятся тем же parseMethodPage — у них нет «Возвращаемое значение:».
        var event = parseMethodPage("events/event_with_params");
        assertThat(event.getReturnValues()).isEmpty();

        var params = event.getSignatures().get(0).getParameters();
        assertThat(params).hasSize(2);
        assertThat(params.get(0))
            .hasFieldOrPropertyWithValue("name", "Отказ")
            .hasFieldOrPropertyWithValue("types", List.of("Булево"));
        assertThat(params.get(1))
            .hasFieldOrPropertyWithValue("name", "РежимЗаписи")
            .hasFieldOrPropertyWithValue("types", List.of("Число"));
    }

    // --- constructors ---

    @Test
    void parseConstructorPage_Default_NoParams() throws URISyntaxException {
        var ctor = parseConstructorPage("constructors/ctor_default");
        assertThat(ctor)
            .hasFieldOrPropertyWithValue("name", "По умолчанию")
            .hasFieldOrPropertyWithValue("description", "Описание для теста.")
            .hasFieldOrPropertyWithValue("sinceVersion", "8.0");
        assertThat(ctor.getSyntaxText()).contains("Новый Виджет()");
        assertThat(ctor.getParameters()).isEmpty();
    }

    @Test
    void parseConstructorPage_RequiredParam() throws URISyntaxException {
        var ctor = parseConstructorPage("constructors/ctor_with_required_param");
        assertThat(ctor)
            .hasFieldOrPropertyWithValue("name", "По имени");
        assertThat(ctor.getDescription()).contains("Описание для теста");
        assertThat(ctor.getParameters()).hasSize(1);
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Имя")
            .hasFieldOrPropertyWithValue("isRequired", true)
            .hasFieldOrPropertyWithValue("types", List.of("Строка"));
    }

    @Test
    void parseConstructorPage_ExampleAndSeeAlso() throws URISyntaxException {
        // На странице конструктора есть «Пример:» и «См. также:» — раньше они
        // игнорировались. Блока «Доступность:» у конструкторов в HBK нет.
        var ctor = parseConstructorPage("constructors/ctor_with_example");
        assertThat(ctor.getExamples()).hasSize(1);
        assertThat(ctor.getExamples().get(0)).contains("Новый Виджет(\"Основной\")");
        assertThat(ctor.getSeeAlso()).containsExactly("Создать");
        // Параметры и синтаксис по-прежнему на месте.
        assertThat(ctor.getSyntaxText()).contains("Новый Виджет(<Имя>)");
        assertThat(ctor.getParameters()).hasSize(1);
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Имя")
            .hasFieldOrPropertyWithValue("types", List.of("Строка"));
    }

    @Test
    void parseConstructorPage_Variadic() throws URISyntaxException {
        var ctor = parseConstructorPage("constructors/ctor_variadic");
        assertThat(ctor)
            .hasFieldOrPropertyWithValue("name", "По размерам");
        assertThat(ctor.getParameters()).hasSize(1);
        // Вариадик-форма `<X1>,...,<XN>` помечается флагом variadic, имя приводится
        // к чистой базе «Размер» (потребитель нумерует по фактическим аргументам).
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Размер")
            .hasFieldOrPropertyWithValue("variadic", true)
            .hasFieldOrPropertyWithValue("isRequired", false)
            .hasFieldOrPropertyWithValue("types", List.of("Число"));
    }

    @Test
    void parseConstructorPage_VariadicRange() throws URISyntaxException {
        // Имя-диапазон `<Значение1-Значение10>` (форма СтрШаблон) → флаг variadic,
        // имя приводится к базе «Значение».
        var ctor = parseConstructorPage("constructors/ctor_variadic_range");
        assertThat(ctor.getParameters()).hasSize(2);
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Шаблон")
            .hasFieldOrPropertyWithValue("variadic", false)
            .hasFieldOrPropertyWithValue("isRequired", true);
        assertThat(ctor.getParameters().get(1))
            .hasFieldOrPropertyWithValue("name", "Значение")
            .hasFieldOrPropertyWithValue("variadic", true)
            .hasFieldOrPropertyWithValue("isRequired", false);
    }

    @Test
    void parseConstructorPage_VariadicPlural() throws URISyntaxException {
        // Множественное имя последнего опц. параметра `<Значения>` (конструктор
        // Структуры) → флаг variadic, сингуляризация в базу «Значение».
        var ctor = parseConstructorPage("constructors/ctor_variadic_plural");
        assertThat(ctor.getParameters()).hasSize(2);
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Ключи")
            .hasFieldOrPropertyWithValue("variadic", false);
        assertThat(ctor.getParameters().get(1))
            .hasFieldOrPropertyWithValue("name", "Значение")
            .hasFieldOrPropertyWithValue("variadic", true)
            .hasFieldOrPropertyWithValue("isRequired", false);
    }

    // --- helpers ---

    HtmlParser.ConstructorDescription parseConstructorPage(String relativePath) throws URISyntaxException {
        var page = page("/%s.html".formatted(relativePath));
        return newParser().parseConstructorPage(page);
    }

    HtmlParser.MethodDescription parseMethodPage(String relativePath) throws URISyntaxException {
        var page = page("/%s.html".formatted(relativePath));
        return newParser().parseMethodPage(page);
    }

    HtmlParser.FormParameterDescription parseFormParameterPage(String relativePath) throws URISyntaxException {
        var page = page("/%s.html".formatted(relativePath));
        return newParser().parseFormParameterPage(page);
    }

    HtmlParser.PropertyDescription parsePropertyPage(String relativePath) throws URISyntaxException {
        var page = page("/%s.html".formatted(relativePath));
        return newParser().parsePropertyPage(page);
    }

    private HtmlParser newParser() throws URISyntaxException {
        return new HtmlParser(Path.of(Objects.requireNonNull(
            this.getClass().getClassLoader().getResource("fixtures")).toURI()));
    }

    private static Page page(String htmlPath) {
        return new Page(new DoubleLanguageString("", ""), htmlPath, Collections.emptyList());
    }
}
