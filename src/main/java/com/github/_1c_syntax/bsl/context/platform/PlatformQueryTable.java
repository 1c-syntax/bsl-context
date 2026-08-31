package com.github._1c_syntax.bsl.context.platform;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryTable;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableParameter;
import lombok.Builder;

import java.util.List;
import java.util.Optional;

/**
 * Таблица языка запросов, разобранная из ветки {@code tables/} HBK.
 * <p>
 * Полей вроде {@code availabilities} или {@code sinceVersion} здесь нет
 * намеренно: на страницах {@code tables/} этих блоков не бывает (проверено
 * по всем 59 страницам платформы 8.3.27) — только «Синтаксис», «Поля»,
 * «Параметры», «Описание:» и изредка «Пример:».
 *
 * @see ContextQueryTable
 */
@Builder
public class PlatformQueryTable implements ContextQueryTable {

    private final ContextName name;
    @Builder.Default
    private final List<ContextQueryTableField> fields = List.of();
    @Builder.Default
    private final List<ContextQueryTableParameter> parameters = List.of();
    /**
     * {@code null} — «признак неприменим»: у всех таблиц, кроме таблиц
     * регистра бухгалтерии. Проставляется по заголовку рубрики оглавления.
     */
    private final Boolean correspondence;
    @Builder.Default
    private final String syntaxText = "";
    @Builder.Default
    private final String description = "";
    /** Заполняется {@link BilingualMerger} с парной en-страницы. */
    private String descriptionEn;
    @Builder.Default
    private final List<String> examples = List.of();
    /** Заполняется {@link BilingualMerger} с парной en-страницы. */
    private List<String> examplesEn;
    /**
     * Путь страницы в HBK — по нему {@link BilingualMerger#mergeQueryTables}
     * находит парную таблицу в en-HBK (пути в ru и en совпадают).
     */
    @Builder.Default
    private final String pagePath = "";

    @Override
    public ContextName name() {
        return name;
    }

    @Override
    public List<ContextQueryTableField> fields() {
        return List.copyOf(fields);
    }

    @Override
    public List<ContextQueryTableParameter> parameters() {
        return List.copyOf(parameters);
    }

    @Override
    public Optional<Boolean> correspondence() {
        return Optional.ofNullable(correspondence);
    }

    @Override
    public String syntaxText() {
        return syntaxText;
    }

    @Override
    public String description() {
        return description;
    }

    /** Проставляет en-описание после bilingual-мерджа. */
    void setDescriptionEn(String descriptionEn) {
        this.descriptionEn = descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public String descriptionEn() {
        return descriptionEn == null ? "" : descriptionEn;
    }

    @Override
    public List<String> examples() {
        return List.copyOf(examples);
    }

    /** Проставляет en-примеры после bilingual-мерджа. */
    void setExamplesEn(List<String> examplesEn) {
        this.examplesEn = examplesEn;
    }

    @Override
    public List<String> examplesEn() {
        return examplesEn == null ? List.of() : List.copyOf(examplesEn);
    }

    /** Путь исходной страницы в HBK. */
    public String pagePath() {
        return pagePath;
    }

    @Override
    public String toString() {
        return name.toString();
    }
}
