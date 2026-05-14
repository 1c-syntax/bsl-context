package com.github._1c_syntax.bsl.context.api;

import java.util.Objects;

/**
 * Двухъязычное имя элемента синтакс-помощника: русское и английское.
 * Сравнение и хэш регистронезависимы по обеим частям.
 *
 * <p>Пример: {@code new ContextName("Массив", "Array")}.
 *
 * <p>Если одно из имён неизвестно, передаётся пустая строка.
 */
public final class ContextName {
    /**
     * Имя на русском.
     */
    private final String name;
    /**
     * Альтернативное имя на английском.
     */
    private final String alias;

    public ContextName(String name, String alias) {
        this.name = name;
        this.alias = alias;
    }

    /**
     * @return русское имя
     */
    public String getName() {
        return name;
    }

    /**
     * @return английское имя (alias)
     */
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
