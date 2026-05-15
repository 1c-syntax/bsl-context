package com.github._1c_syntax.bsl.context.smoke;

import com.github._1c_syntax.bsl.context.PlatformContextGrabber;
import com.github._1c_syntax.bsl.context.PlatformFinder;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextKind;
import com.github._1c_syntax.bsl.context.api.ContextLanguageKeyword;
import com.github._1c_syntax.bsl.context.api.ContextType;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "BSL_CONTEXT_REAL_HBK", matches = "true")
class ShlangSmokeTest {

    @Test
    void parseShlangFromInstalledPlatform() throws Exception {
        var install = PlatformFinder.findLatest().orElseThrow();
        var workDir = Files.createTempDirectory("bsl-context-shlang-");
        var grabber = PlatformContextGrabber.fromPlatformBin(install.binDir(), workDir);
        grabber.parse();
        var provider = grabber.getProvider();

        var primitives = new ArrayList<ContextType>();
        var keywordsByCategory = new EnumMap<LanguageKeywordCategory, java.util.List<ContextLanguageKeyword>>(
            LanguageKeywordCategory.class);
        for (var ctx : provider.getContexts()) {
            if (ctx.kind() == ContextKind.PRIMITIVE_TYPE && ctx instanceof ContextType type) {
                primitives.add(type);
            } else if (ctx.kind() == ContextKind.LANGUAGE_KEYWORD && ctx instanceof ContextLanguageKeyword kw) {
                keywordsByCategory.computeIfAbsent(kw.category(),
                    k -> new ArrayList<>()).add(kw);
            }
        }

        var out = new StringBuilder();
        out.append("\n========================================================\n");
        out.append("  bsl-context :: shlang_*.hbk parse result\n");
        out.append("  Platform: ").append(install.binDir()).append('\n');
        out.append("========================================================\n\n");

        out.append("PRIMITIVE_TYPE (").append(primitives.size()).append("):\n");
        primitives.sort(Comparator.comparing(t -> t.name().getName(), String.CASE_INSENSITIVE_ORDER));
        for (var t : primitives) {
            var en = t.name().getAlias() == null || t.name().getAlias().isEmpty() ? "—" : t.name().getAlias();
            var desc = t.description().isEmpty() ? "" : "  // " + abbreviate(t.description(), 100);
            out.append(String.format("  %-20s  %-12s%s%n", t.name().getName(), en, desc));
        }
        out.append('\n');

        for (var cat : LanguageKeywordCategory.values()) {
            var list = keywordsByCategory.getOrDefault(cat, java.util.List.of());
            if (list.isEmpty()) {
                continue;
            }
            list.sort(Comparator.comparing(k -> k.name().getName(), String.CASE_INSENSITIVE_ORDER));
            out.append(cat).append(" (").append(list.size()).append("):\n");
            for (var kw : list) {
                var en = kw.name().getAlias() == null || kw.name().getAlias().isEmpty() ? "—" : kw.name().getAlias();
                var snippetRu = kw.snippet().ru();
                var snippetEn = kw.snippet().en();
                var snippet = kw.snippet().isEmpty()
                    ? ""
                    : "  snippet=«" + escape(snippetRu) + "» / «" + escape(snippetEn) + "»";
                var desc = kw.description().isEmpty() ? "" : "  // " + abbreviate(kw.description(), 80);
                out.append(String.format("  %-30s  %-30s%s%s%n",
                    kw.name().getName(), en, snippet, desc));
            }
            out.append('\n');
        }
        System.out.println(out);

        // Sanity assertions.
        var primitiveNames = primitives.stream().map(t -> t.name().getName()).toList();
        assertThat(primitiveNames).contains(
            "Строка", "Число", "Дата", "Булево", "Тип", "Null", "Неопределено", "Произвольный"
        );
        // Описание примитивов из shlang должно быть непустым (для большинства).
        var stringPrimitive = primitives.stream()
            .filter(t -> "Строка".equals(t.name().getName()))
            .findFirst().orElseThrow();
        assertThat(stringPrimitive.description())
            .as("Описание Строки должно тянуться из shlang def_String")
            .contains("Unicode");
        var literals = keywordsByCategory.get(LanguageKeywordCategory.LITERAL);
        assertThat(literals).anyMatch(k -> k.name().getName().equals("Истина"));
        assertThat(literals).anyMatch(k -> k.name().getName().equals("Ложь"));

        // processRawTypes: типы параметров методов должны резолвиться в наши
        // shlang-примитивы по СИЛЬНОЙ (==) идентичности — то есть в общем typeIndex
        // примитивы видны и обычные методы их используют. Берём глобальную
        // функцию Лев(Строка, Число) и проверяем что у её первого параметра
        // среди допустимых типов есть наш экземпляр Строки.
        var globalCtx = provider.getGlobalContext();
        var lev = globalCtx.methods().stream()
            .filter(m -> "Лев".equalsIgnoreCase(m.name().getName()))
            .findFirst().orElseThrow(() -> new AssertionError("Лев not found in global context"));
        var firstSig = lev.signatures().get(0);
        var firstParamTypes = firstSig.parameters().get(0).types();
        assertThat(firstParamTypes)
            .as("Лев(Строка, Число) — первый параметр должен резолвиться в shlang-Строку (==)")
            .anyMatch(t -> t == stringPrimitive);
    }

    private static String escape(String s) {
        return s.replace("\n", "\\n").replace("\r", "");
    }

    private static String abbreviate(String s, int max) {
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
