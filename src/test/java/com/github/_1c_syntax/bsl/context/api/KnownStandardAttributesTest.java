package com.github._1c_syntax.bsl.context.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnownStandardAttributesTest {

    @Test
    void documentReturnsExpectedNames() {
        var attrs = KnownStandardAttributes.forOwner("ОбъектМетаданных: Документ");
        assertThat(attrs).extracting(ContextName::getName)
            .contains("Ссылка", "Дата", "Номер", "Проведен", "ПометкаУдаления", "МоментВремени");
        assertThat(attrs).extracting(ContextName::getAlias)
            .contains("Ref", "Date", "Number", "Posted", "DeletionMark", "PointInTime");
    }

    @Test
    void catalogReturnsExpectedNames() {
        var attrs = KnownStandardAttributes.forOwner("ОбъектМетаданных: Справочник");
        assertThat(attrs).extracting(ContextName::getName)
            .contains("Ссылка", "Код", "Наименование", "Родитель", "Владелец");
    }

    @Test
    void tabularSectionReturnsLineNumberAndRef() {
        var attrs = KnownStandardAttributes.forOwner("ОбъектМетаданных: ТабличнаяЧасть");
        assertThat(attrs).extracting(ContextName::getName).containsExactly("НомерСтроки", "Ссылка");
    }

    @Test
    void unknownOwnerReturnsEmpty() {
        assertThat(KnownStandardAttributes.forOwner("НеизвестныйТип")).isEmpty();
        assertThat(KnownStandardAttributes.forOwner(null)).isEmpty();
        assertThat(KnownStandardAttributes.forOwner("")).isEmpty();
    }

    @Test
    void lookupIsCaseInsensitive() {
        var lower = KnownStandardAttributes.forOwner("объектметаданных: документ");
        var upper = KnownStandardAttributes.forOwner("ОБЪЕКТМЕТАДАННЫХ: ДОКУМЕНТ");
        assertThat(lower).isNotEmpty();
        assertThat(upper).isNotEmpty();
        assertThat(lower).isEqualTo(upper);
    }
}
