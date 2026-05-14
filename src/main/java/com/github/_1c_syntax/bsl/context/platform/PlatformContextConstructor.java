package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import lombok.Builder;

import java.util.List;

@Builder
public class PlatformContextConstructor implements ContextConstructor {
    private final ContextName name;
    private final List<ContextSignatureParameter> parameters;
    private final String description;
    @lombok.Builder.Default
    private final String sinceVersion = "";
    @lombok.Builder.Default
    private final String deprecatedSinceVersion = "";
    @lombok.Builder.Default
    private final String syntaxText = "";

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public List<ContextSignatureParameter> parameters() {
        return List.copyOf(parameters);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String sinceVersion() {
        return sinceVersion;
    }

    @Override
    public String deprecatedSinceVersion() {
        return deprecatedSinceVersion;
    }

    @Override
    public String syntaxText() {
        return syntaxText;
    }

    protected void processRawTypes(java.util.Map<String, Context> typeIndex) {
        for (var p : parameters) {
            ((PlatformContextSignatureParameter) p).processRawTypes(typeIndex);
        }
    }
}
