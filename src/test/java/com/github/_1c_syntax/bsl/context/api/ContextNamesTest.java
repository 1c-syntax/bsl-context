package com.github._1c_syntax.bsl.context.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextNamesTest {

    @Test
    void plainNameIsNotGeneric() {
        assertThat(ContextNames.isGeneric(new ContextName("Массив", "Array"))).isFalse();
    }

    @Test
    void nameWithAngleBracketIsGeneric_Ru() {
        assertThat(ContextNames.isGeneric(
            new ContextName("СправочникСсылка.<Имя справочника>", "CatalogRef.Catalog name"))
        ).isTrue();
    }

    @Test
    void nameWithAngleBracketIsGeneric_En() {
        assertThat(ContextNames.isGeneric(
            new ContextName("CatalogRef.Catalog name", "CatalogRef.<Catalog name>"))
        ).isTrue();
    }

    @Test
    void propertyNameStartingWithAngleBracketIsGeneric() {
        assertThat(ContextNames.isGeneric(new ContextName("<Имя справочника>", "<Catalog name>")))
            .isTrue();
    }

    @Test
    void nullNameIsNotGeneric() {
        assertThat(ContextNames.isGeneric(null)).isFalse();
    }
}
