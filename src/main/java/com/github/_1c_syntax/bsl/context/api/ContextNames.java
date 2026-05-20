package com.github._1c_syntax.bsl.context.api;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Извлекает имена generic-плейсхолдеров из имени контекста.
     * <p>
     * Для {@code "СправочникСсылка.<Имя справочника>"} возвращает
     * {@code ["Имя справочника"]}. Если в имени нет угловых скобок —
     * возвращает пустой список. Если в имени несколько плейсхолдеров
     * (теоретически возможно для составных типов) — возвращает их все
     * в порядке появления.
     * <p>
     * Имя берётся из {@link ContextName#getName()} (ru), как канонический
     * источник; en-алиас обычно содержит те же placeholder'ы в той же
     * позиции, но с переводом, поэтому здесь не объединяется.
     *
     * @param name двухъязычное имя контекста
     * @return список имён placeholder'ов (без угловых скобок), пустой если
     *         placeholder'ов нет
     */
    public static List<String> typeParameters(ContextName name) {
        if (name == null) {
            return List.of();
        }
        var placeholders = placeholders(name.getName());
        if (placeholders.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<String>(placeholders.size());
        for (var p : placeholders) {
            result.add(p.name());
        }
        return List.copyOf(result);
    }

    /**
     * Структурное представление всех generic-плейсхолдеров в строке.
     * Возвращает список {@link Placeholder} с именем placeholder'а
     * (без угловых скобок) и позициями {@code [start, end)} в исходной
     * строке: {@code start} указывает на символ {@code <}, {@code end} —
     * сразу после {@code >}.
     * <p>
     * Это единая точка парсинга угловых скобок в API; потребители (например,
     * LS-серверы), которым нужны позиции для подстановки, должны
     * использовать этот метод вместо самостоятельного скана.
     *
     * @param raw строка, в которой искать placeholder'ы (например,
     *            {@link ContextName#getName()})
     * @return неизменяемый список placeholder'ов в порядке появления;
     *         пустой, если placeholder'ов нет либо {@code raw} пуст
     */
    public static List<Placeholder> placeholders(String raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        var result = new ArrayList<Placeholder>();
        int from = 0;
        while (true) {
            int lt = raw.indexOf('<', from);
            if (lt < 0) {
                break;
            }
            int gt = raw.indexOf('>', lt + 1);
            if (gt < 0) {
                break;
            }
            var name = raw.substring(lt + 1, gt).trim();
            if (!name.isEmpty()) {
                result.add(new Placeholder(name, lt, gt + 1));
            }
            from = gt + 1;
        }
        return result.isEmpty() ? List.of() : List.copyOf(result);
    }

    /**
     * Возвращает «семейный префикс» имени — часть до первого
     * плейсхолдера, обрезанная по последней точке. Для
     * {@code "СправочникСсылка.<Имя справочника>"} даст {@code "СправочникСсылка"}.
     * Если плейсхолдеров нет — возвращает само имя без изменений.
     *
     * @param name двухъязычное имя контекста
     * @return семейный префикс или полное имя, если оно не generic
     */
    public static String familyCore(ContextName name) {
        if (name == null) {
            return "";
        }
        var raw = name.getName();
        if (raw == null) {
            return "";
        }
        int lt = raw.indexOf('<');
        if (lt < 0) {
            return raw;
        }
        var head = raw.substring(0, lt);
        // Срезаем хвостовую точку, если placeholder шёл сразу после неё:
        // "СправочникСсылка.<…>" → "СправочникСсылка"
        while (!head.isEmpty() && head.charAt(head.length() - 1) == '.') {
            head = head.substring(0, head.length() - 1);
        }
        return head;
    }

    private static boolean containsAngleBracket(String s) {
        return s != null && (s.indexOf('<') >= 0 || s.indexOf('>') >= 0);
    }
}
