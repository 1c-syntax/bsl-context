package com.github._1c_syntax.bsl.context.smoke;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Дамп всех {@link ContextCollection}-типов в markdown-таблицу для
 * человекочитаемого аудита: имя коллекции, типы элементов, поддержка
 * {@code Для каждого} / {@code [...]} с описаниями. Записывает в
 * {@code tmp/collections-real-hbk.md} (каталог tmp/ в .gitignore).
 */
@EnabledIfEnvironmentVariable(named = "BSL_CONTEXT_REAL_HBK", matches = "true")
class CollectionsDumpTest {

    @Test
    void dumpCollectionsAndElementTypes() throws Exception {
        var install = PlatformFinder.findLatest().orElseThrow();
        var workDir = Files.createTempDirectory("bsl-context-coll-");
        var grabber = PlatformContextGrabber.fromPlatformBin(install.binDir(), workDir);
        grabber.parse();
        var provider = grabber.getProvider();

        var collections = provider.getContexts().stream()
            .filter(ContextCollection.class::isInstance)
            .map(ContextCollection.class::cast)
            .sorted(Comparator.comparing(c -> c.name().getName(), String.CASE_INSENSITIVE_ORDER))
            .toList();

        var md = renderMarkdown(install.binDir().toString(), collections);
        var out = Path.of("tmp/collections-real-hbk.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, md);
        System.out.println("Wrote " + collections.size() + " collections to " + out.toAbsolutePath());
    }

    private static String renderMarkdown(String platformBin, List<ContextCollection> collections) {
        var sb = new StringBuilder();
        sb.append("# Платформенные коллекции\n\n");
        sb.append("> Источник: блок «Элементы коллекции:» на главной HTML-странице типа\n");
        sb.append("> в `shcntx_ru.hbk`. Каждая такая страница в bsl-context публикуется как\n");
        sb.append("> `ContextCollection extends ContextType` со списком элементов и\n");
        sb.append("> признаками поддержки обхода `Для каждого` и индексатора `[...]`.\n");
        sb.append(">\n");
        sb.append("> Сгенерировано из реальной 1С: `").append(platformBin).append("`\n");
        sb.append("> Всего коллекций: **").append(collections.size()).append("**\n\n");
        sb.append("| # | Коллекция (ru / en) | Тип элемента (ru / en) | `Для каждого` | `[...]` | Описание обхода | Описание индексатора |\n");
        sb.append("|--:|---|---|:-:|:-:|---|---|\n");
        int i = 0;
        for (var c : collections) {
            i++;
            sb.append("| ").append(i)
              .append(" | ").append(codeCell(nameWithAlias(c.name().getName(), c.name().getAlias())))
              .append(c.isGeneric() ? "<br>_generic_" : "")
              .append(" | ").append(codeCell(renderElements(c.collectionElementTypes())))
              .append(" | ").append(c.supportsForEach() ? "✅" : "—")
              .append(" | ").append(c.supportsIndexAccess() ? "✅" : "—")
              .append(" | ").append(textCell(c.forEachDescription()))
              .append(" | ").append(textCell(c.indexAccessDescription()))
              .append(" |\n");
        }
        return sb.toString();
    }

    private static String nameWithAlias(String ru, String en) {
        if (en == null || en.isBlank() || en.equalsIgnoreCase(ru)) {
            return ru;
        }
        return ru + " / " + en;
    }

    private static String renderElements(List<Context> elements) {
        if (elements.isEmpty()) {
            return "—";
        }
        return elements.stream()
            .map(t -> nameWithAlias(t.name().getName(), t.name().getAlias()))
            .collect(Collectors.joining(", "));
    }

    /**
     * Идентификаторы / списки идентификаторов в backticks — это решает
     * сразу две проблемы: markdown не интерпретирует {@code <...>} в
     * generic-именах как HTML-теги и читается моноширинно. Внутри `code`
     * pipe нужно эскейпить — иначе обрывает ячейку.
     */
    private static String codeCell(String s) {
        if (s == null || s.isEmpty() || "—".equals(s)) {
            return s == null ? "" : s;
        }
        return "`" + s.replace("|", "\\|").trim() + "`";
    }

    /**
     * Произвольный текст (описания). Эскейпим pipe и переносы строк.
     * Угловые скобки в описаниях не встречаются как теги — оставляем
     * как есть; на случай — конвертируем в HTML-entity.
     */
    private static String textCell(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.replace("|", "\\|")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", " ")
            .replace("\r", "")
            .trim();
    }
}
