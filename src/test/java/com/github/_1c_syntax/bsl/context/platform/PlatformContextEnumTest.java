package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Тесты {@link PlatformContextEnum} — двух конструкторов и опциональной
 * подписи {@link ContextEnum#valueType()}.
 */
class PlatformContextEnumTest {

    @Test
    void legacyConstructor_noValueType_returnsEmpty() {
        var pictureLib = new PlatformContextEnum(
            new ContextName("БиблиотекаКартинок", "PictureLib"), List.of());
        assertThat(pictureLib.valueType()).isEmpty();
    }

    @Test
    void valueTypeConstructor_returnsProvidedType() {
        var picture = new ContextName("Картинка", "Picture");
        var pictureLib = new PlatformContextEnum(
            new ContextName("БиблиотекаКартинок", "PictureLib"), List.of(), picture);
        assertThat(pictureLib.valueType()).contains(picture);
    }

    @Test
    void builder_canSetValueType() {
        var picture = new ContextName("Картинка", "Picture");
        var enumeration = PlatformContextEnum.builder()
            .name(new ContextName("БиблиотекаКартинок", "PictureLib"))
            .values(List.of())
            .valueType(picture)
            .build();
        assertThat(enumeration.valueType()).contains(picture);
        assertThat(enumeration.name().getName()).isEqualTo("БиблиотекаКартинок");
    }

    @Test
    void builder_withoutValueType_returnsEmpty() {
        var enumeration = PlatformContextEnum.builder()
            .name(new ContextName("ВидДвиженияНакопления", "AccumulationRecordType"))
            .values(List.of())
            .build();
        assertThat(enumeration.valueType()).isEmpty();
    }

    @Test
    void bindBilingualValueType_fillsEnAliasFromIndex() {
        var pictureLib = new PlatformContextEnum(
            new ContextName("БиблиотекаКартинок", "PictureLib"), List.of(),
            new ContextName("Картинка", ""));
        var picture = PlatformContextType.builder()
            .name(new ContextName("Картинка", "Picture"))
            .methods(List.of())
            .constructors(List.of())
            .events(List.of())
            .properties(List.of())
            .description("")
            .build();
        pictureLib.bindBilingualValueType(Map.of(
            "Картинка", picture, "Picture", picture));
        assertThat(pictureLib.valueType()).isPresent()
            .get()
            .extracting(ContextName::getAlias)
            .isEqualTo("Picture");
    }

    @Test
    void bindBilingualValueType_noOp_whenNoValueType() {
        var enumeration = new PlatformContextEnum(
            new ContextName("ВидДвиженияНакопления", "AccumulationRecordType"),
            List.of());
        enumeration.bindBilingualValueType(Map.of());
        assertThat(enumeration.valueType()).isEmpty();
    }

    @Test
    void bindBilingualValueType_keepsExistingAlias() {
        var pictureLib = new PlatformContextEnum(
            new ContextName("БиблиотекаКартинок", "PictureLib"), List.of(),
            new ContextName("Картинка", "AlreadySet"));
        var picture = PlatformContextType.builder()
            .name(new ContextName("Картинка", "Picture"))
            .methods(List.of())
            .constructors(List.of())
            .events(List.of())
            .properties(List.of())
            .description("")
            .build();
        pictureLib.bindBilingualValueType(Map.of("Картинка", picture));
        assertThat(pictureLib.valueType().orElseThrow().getAlias())
            .isEqualTo("AlreadySet");
    }

    @Test
    void bindBilingualValueType_noOp_whenIndexMissesType() {
        var pictureLib = new PlatformContextEnum(
            new ContextName("БиблиотекаКартинок", "PictureLib"), List.of(),
            new ContextName("Картинка", ""));
        pictureLib.bindBilingualValueType(Map.of());
        // alias остаётся пустым, name — на месте.
        assertThat(pictureLib.valueType().orElseThrow().getName()).isEqualTo("Картинка");
        assertThat(pictureLib.valueType().orElseThrow().getAlias()).isEmpty();
    }

    @Test
    void valuesGetter_exposesProvidedValues() {
        var values = List.<ContextEnumValue>of(
            new PlatformContextEnumValue(new ContextName("Один", "One")));
        var enumeration = new PlatformContextEnum(
            new ContextName("Пример", "Example"), values);
        assertThat(enumeration.values()).hasSize(1);
        assertThat(enumeration.values().get(0).name().getName()).isEqualTo("Один");
    }
}
