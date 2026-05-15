package com.github._1c_syntax.bsl.context.platform.primitive;

import com.github._1c_syntax.bsl.context.api.ContextName;

/**
 * Примитивный тип платформы с именем и описанием, пришедшими из
 * синтакс-помощника ({@code shlang_*.hbk}, страница {@code def_*}).
 * Методов, свойств, событий и конструкторов у примитивов нет.
 * <p>
 * Заменяет ранее хардкоженные классы {@code BooleanType}, {@code StringType}
 * и т.п.: имена больше не дублируются в коде, всё приходит из СП.
 */
public final class PrimitivePlaceholderType extends PrimitiveType {
    private final ContextName name;
    private final String description;

    public PrimitivePlaceholderType(ContextName name) {
        this(name, "");
    }

    public PrimitivePlaceholderType(ContextName name, String description) {
        this.name = name;
        this.description = description == null ? "" : description;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
