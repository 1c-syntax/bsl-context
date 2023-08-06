package com.github._1c_syntax.bsl.context.api;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextName that = (ContextName) o;
        return name.equalsIgnoreCase(that.name) && alias.equalsIgnoreCase(that.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, alias);
    }

    @Override
    public String toString() {
        return String.format("%s (%s)", getName(), getAlias());
    }
}
