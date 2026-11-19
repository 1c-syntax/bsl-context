package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformContextTypeResolutionTest {

    @Test
    void reportsUnresolvedAlternativesWithoutChangingResolvedTypes() {
        Context known = PlatformContextType.builder().build();
        var index = Map.of("ИзвестныйТип", known);
        var partial = List.of("ИзвестныйТип", "НеизвестнаяКатегория");
        var parameter = PlatformContextSignatureParameter.builder().rawTypes(partial).build();
        var property = PlatformContextProperty.builder().rawTypes(partial).build();
        var method = PlatformContextMethod.builder().rawReturnValues(partial).signatures(List.of()).build();
        parameter.processRawTypes(index);
        property.processRawTypes(index);
        method.processRawTypes(index);
        assertThat(((ContextSignatureParameter) parameter).hasUnresolvedTypes()).isTrue();
        assertThat(((ContextProperty) property).hasUnresolvedTypes()).isTrue();
        assertThat(((ContextMethod) method).hasUnresolvedReturnTypes()).isTrue();
        assertThat(parameter.types()).containsExactly(known);
        assertThat(property.types()).containsExactly(known);
        assertThat(method.returnValues()).containsExactly(known);

        var complete = PlatformContextSignatureParameter.builder()
            .rawTypes(List.of("ИзвестныйТип")).build();
        complete.processRawTypes(index);
        assertThat(complete.hasUnresolvedTypes()).isFalse();
        assertThat(complete.types()).containsExactly(known);
    }
}
