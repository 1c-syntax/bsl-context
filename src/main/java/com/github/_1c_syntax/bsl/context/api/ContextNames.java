package com.github._1c_syntax.bsl.context.api;

/**
 * Утилитные функции над именами контекстов.
 */
public final class ContextNames {

    private ContextNames() {
    }

    /**
     * Проверка, что имя содержит generic-плейсхолдер {@code <…>},
     * который заполняется конкретным значением из конфигурации
     * (например, {@code СправочникСсылка.<Имя справочника>}).
     *
     * @return {@code true}, если хотя бы одна из частей имени (ru/en) содержит
     *     угловую скобку.
     */
    public static boolean isGeneric(ContextName name) {
        if (name == null) {
            return false;
        }
        return containsAngleBracket(name.getName()) || containsAngleBracket(name.getAlias());
    }

    private static boolean containsAngleBracket(String s) {
        return s != null && (s.indexOf('<') >= 0 || s.indexOf('>') >= 0);
    }
}
