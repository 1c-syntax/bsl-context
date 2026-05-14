package com.github._1c_syntax.bsl.context.api;

import java.util.Arrays;
import java.util.Optional;

/**
 * Режим доступа к свойствам.
 */
public enum AccessMode {
    READ("Только чтение", "Read only"),
    READ_WRITE("Чтение и запись", "Read and write");

    private final ContextName name;

    AccessMode(String name, String alias) {
        this.name = new ContextName(name, alias);
    }

    public static Optional<AccessMode> findByName(String name) {
        return Arrays.stream(AccessMode.values())
                .filter(value -> value.name.getName().equalsIgnoreCase(name)
                        || value.name.getAlias().equalsIgnoreCase(name))
                .findFirst();
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
