package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import lombok.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Платформенная таблица языка запросов.
 * <p>
 * «Страничные» метаданные (описание, доступность, версии, «Пример:»,
 * «См. также:») снимаются с главной страницы таблицы так же, как
 * у {@link PlatformContextType}.
 */
public class PlatformContextQueryTable implements ContextQueryTable {
    private final ContextName name;
    private final List<ContextQueryTableField> fields;
    private final Boolean correspondence;
    private final String description;
    private final String notes;
    private final List<Availability> availabilities;
    private final String sinceVersion;
    private final String deprecatedSinceVersion;
    private final List<String> examples;
    private final List<String> seeAlso;
    private final List<String> recommendedReplacements;
    /** Путь страницы в HBK — ключ сопоставления ru↔en, см. {@link PlatformContextType}. */
    private final String pagePath;

    /**
     * Полный конструктор; {@code null} у любого «страничного» поля означает
     * «на странице такого блока нет» и превращается в пустую строку / список.
     */
    @Builder
    PlatformContextQueryTable(ContextName name, List<ContextQueryTableField> fields,
                              Boolean correspondence,
                              String description, String notes, List<Availability> availabilities,
                              String sinceVersion, String deprecatedSinceVersion,
                              List<String> examples, List<String> seeAlso,
                              List<String> recommendedReplacements, String pagePath) {
        this.name = name;
        this.fields = fields == null ? List.of() : List.copyOf(fields);
        this.correspondence = correspondence;
        this.description = description == null ? "" : description;
        this.notes = notes == null ? "" : notes;
        this.availabilities = availabilities == null ? List.of() : List.copyOf(availabilities);
        this.sinceVersion = sinceVersion == null ? "" : sinceVersion;
        this.deprecatedSinceVersion = deprecatedSinceVersion == null ? "" : deprecatedSinceVersion;
        this.examples = examples == null ? List.of() : List.copyOf(examples);
        this.seeAlso = seeAlso == null ? List.of() : List.copyOf(seeAlso);
        this.recommendedReplacements = recommendedReplacements == null
            ? List.of() : List.copyOf(recommendedReplacements);
        this.pagePath = pagePath == null ? "" : pagePath;
    }

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public List<ContextQueryTableField> fields() {
        return fields;
    }

    @Override
    public Optional<Boolean> correspondence() {
        return Optional.ofNullable(correspondence);
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public String notes() {
        return notes;
    }

    @Override
    public List<Availability> availabilities() {
        return availabilities;
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
    public List<String> examples() {
        return examples;
    }

    @Override
    public List<String> seeAlso() {
        return seeAlso;
    }

    @Override
    public List<String> recommendedReplacements() {
        return recommendedReplacements;
    }

    public String pagePath() {
        return pagePath;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
