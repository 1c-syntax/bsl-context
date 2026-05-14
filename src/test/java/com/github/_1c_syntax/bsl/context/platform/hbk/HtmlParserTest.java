package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github.eightm.lib.DoubleLanguageString;
import com.github.eightm.lib.Page;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
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

    // --- enum value ---

    @Test
    void parseEnumValuePage() throws URISyntaxException {
        var page = page("/enums/enum_value_active.html");
        var ev = newParser().parseEnumValuePage(page);
        assertThat(ev.getDescription()).contains("Виджет в активном режиме");
        assertThat(ev.getSinceVersion()).isEqualTo("8.0");
        assertThat(ev.getDeprecatedSinceVersion()).isEmpty();
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
    void parseConstructorPage_Variadic() throws URISyntaxException {
        var ctor = parseConstructorPage("constructors/ctor_variadic");
        assertThat(ctor)
            .hasFieldOrPropertyWithValue("name", "По размерам");
        assertThat(ctor.getParameters()).hasSize(1);
        // Вариадик-форма `<X1>,...,<XN>` теперь корректно разбирается на «X1,...,XN».
        assertThat(ctor.getParameters().get(0))
            .hasFieldOrPropertyWithValue("name", "Размер1,...,РазмерN")
            .hasFieldOrPropertyWithValue("isRequired", false)
            .hasFieldOrPropertyWithValue("types", List.of("Число"));
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
