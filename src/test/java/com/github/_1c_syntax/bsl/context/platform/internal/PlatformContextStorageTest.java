package com.github._1c_syntax.bsl.context.platform.internal;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformContextStorageTest {

    @Test
    void resolvesByRussianAndEnglishName() {
        var array = simpleType("Массив", "Array");
        var storage = new PlatformContextStorage(new ArrayList<>(List.of(array)));

        assertThat(storage.getContextByName("Массив")).contains(array);
        assertThat(storage.getContextByName("Array")).contains(array);
    }

    @Test
    void resolveIsCaseInsensitive() {
        var array = simpleType("Массив", "Array");
        var storage = new PlatformContextStorage(new ArrayList<>(List.of(array)));

        assertThat(storage.getContextByName("МАССИВ")).contains(array);
        assertThat(storage.getContextByName("array")).contains(array);
    }

    @Test
    void resolveByContextNameReturnsTypeWhenEitherNameMatches() {
        var array = simpleType("Массив", "Array");
        var storage = new PlatformContextStorage(new ArrayList<>(List.of(array)));

        assertThat(storage.getContextByName(new ContextName("Массив", "Unrelated"))).contains(array);
        assertThat(storage.getContextByName(new ContextName("Unrelated", "Array"))).contains(array);
    }

    @Test
    void missingNameReturnsEmpty() {
        var storage = new PlatformContextStorage(new ArrayList<>());
        assertThat(storage.getContextByName("???")).isEmpty();
    }

    @Test
    void globalContextIsExtractedFromList() {
        var global = PlatformGlobalContext.builder()
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .applicationEvents(Collections.emptyList())
            .ordinaryApplicationEvents(Collections.emptyList())
            .sessionModuleEvents(Collections.emptyList())
            .externalConnectionModuleEvents(Collections.emptyList())
            .build();
        var array = simpleType("Массив", "Array");

        var storage = new PlatformContextStorage(new ArrayList<>(List.of(global, array)));

        assertThat(storage.getGlobalContext()).isSameAs(global);
        assertThat(storage.getContexts()).doesNotContain((Context) global).contains(array);
    }

    @Test
    void globalContextIsNullWhenAbsent() {
        var storage = new PlatformContextStorage(new ArrayList<>(List.of(simpleType("X", "Y"))));
        assertThat(storage.getGlobalContext()).isNull();
    }

    private static PlatformContextType simpleType(String name, String alias) {
        return PlatformContextType.builder()
            .name(new ContextName(name, alias))
            .methods(Collections.emptyList())
            .properties(Collections.emptyList())
            .events(Collections.emptyList())
            .constructors(Collections.emptyList())
            .build();
    }
}
