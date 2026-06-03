package com.github._1c_syntax.bsl.context.api;

import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты default-метода {@link ContextEnumValue#isGeneric()}. Подразумевается,
 * что значения с плейсхолдером в имени (например, {@code <Имя картинки>} у
 * {@code БиблиотекаКартинок}) распознаются как generic-template'ы и потом
 * материализуются из конфигурации.
 */
class ContextEnumValueTest {

    @Test
    void isGeneric_whenNameHasPlaceholder_returnsTrue() {
        var value = new PlatformContextEnumValue(new ContextName("<Имя картинки>", "<Icon name>"));
        assertThat(value.isGeneric()).isTrue();
    }

    @Test
    void isGeneric_whenOnlyEnAliasHasPlaceholder_returnsTrue() {
        // Если ru-имя без скобок, а en — с ними, всё равно generic
        // (исторически встречается на несинхронных страницах HBK).
        var value = new PlatformContextEnumValue(new ContextName("ИмяПростое", "<Plain name>"));
        assertThat(value.isGeneric()).isTrue();
    }

    @Test
    void isGeneric_forRegularValue_returnsFalse() {
        var value = new PlatformContextEnumValue(new ContextName("АктивироватьЗадачу", "ActivateTask"));
        assertThat(value.isGeneric()).isFalse();
    }

    @Test
    void isGeneric_forNullName_returnsFalse() {
        var value = new PlatformContextEnumValue(null);
        assertThat(value.isGeneric()).isFalse();
    }
}
