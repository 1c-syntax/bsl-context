package com.github._1c_syntax.bsl.context.api;

/**
 * Имя любой части контекста.
 */
public final class ContextName {
    /**
     * Имя на русском.
     */
    private final String name;
    /**
     * Альтернативное имя, как правильно на английском.
     */
    private final String alias;

    public ContextName(String name, String alias) {
        this.name = name;
        this.alias = alias;
    }

    public String getName() {
        return name;
    }

    public String getAlias() {
        return alias;
    }

    // TODO хеш, сравнение

    @Override
    public String toString() {
        return String.format("%s (%s)", getName(), getAlias());
    }
}
