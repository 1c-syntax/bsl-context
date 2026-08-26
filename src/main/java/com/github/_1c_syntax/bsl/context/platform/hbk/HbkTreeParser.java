package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.AccessMode;
import com.github._1c_syntax.bsl.context.api.Availability;
import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextConstructor;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextEvent;
import com.github._1c_syntax.bsl.context.api.ContextFormParameter;
import com.github._1c_syntax.bsl.context.api.ContextMethod;
import com.github._1c_syntax.bsl.context.api.ContextMethodSignature;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextProperty;
import com.github._1c_syntax.bsl.context.api.ContextQueryTableField;
import com.github._1c_syntax.bsl.context.api.ContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextCollection;
import com.github._1c_syntax.bsl.context.platform.PlatformContextConstructor;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEvent;
import com.github._1c_syntax.bsl.context.platform.PlatformContextFormParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethod;
import com.github._1c_syntax.bsl.context.platform.PlatformContextMethodSignature;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProperty;
import com.github._1c_syntax.bsl.context.platform.PlatformContextProvider;
import com.github._1c_syntax.bsl.context.platform.PlatformContextQueryTable;
import com.github._1c_syntax.bsl.context.platform.PlatformContextQueryTableField;
import com.github._1c_syntax.bsl.context.platform.PlatformContextSignatureParameter;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformGlobalContext;
import com.github._1c_syntax.bsl.context.platform.primitive.ArbitraryType;
import com.github.eightm.lib.DoubleLanguageString;
import com.github.eightm.lib.Page;
import com.github.eightm.lib.TableOfContent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HbkTreeParser {
    // visitPagesFromTree обходит дерево через parallelStream — Jsoup-parse
    // тысяч HTML-страниц параллельно ускоряет полный парс HBK с ~3.3с до ~2.4с.
    // Порядок добавления при этом недетерминирован, поэтому при коллизии
    // имён (в HBK есть, например, два «ЭлементыФормы»: FormItems-коллекция и
    // Controls-тип) PlatformContextStorage использует putIfAbsent (первый-
    // побеждает): какой именно из двух окажется первым — недетерминированно,
    // но мы не затираем уже проиндексированный контекст. Сам список —
    // synchronizedList, иначе ArrayList.add под contention теряет элементы
    // (между ensureCapacity и size++).
    private final List<Context> contexts = Collections.synchronizedList(new ArrayList<>());
    /**
     * Сколько узлов оглавления ведёт на каждую страницу. Обычно один, но
     * бывает и несколько — см. {@link #contextName(Page, String, String)}.
     */
    private final Map<String, Integer> nodesPerPage = new HashMap<>();
    private final HtmlParser htmlParser;

    /**
     * Создаёт обходчик на распакованном каталоге страниц. Использовать
     * там, где FileStorage уже распакован (тесты).
     */
    public HbkTreeParser(Path pagesPath) {
        this(new HtmlParser(pagesPath));
    }

    /**
     * Создаёт обходчик на произвольном источнике страниц. В production-коде
     * передаётся {@link PageSource.InMemory}, чтобы избежать распаковки
     * тысяч мелких файлов на файловую систему.
     */
    public HbkTreeParser(PageSource pageSource) {
        this(new HtmlParser(pageSource));
    }

    private HbkTreeParser(HtmlParser htmlParser) {
        this.htmlParser = htmlParser;
    }

    public List<Context> parse(TableOfContent tree) {
        return parse(tree, List.of());
    }

    /**
     * Парсит дерево shcntx и сразу подмешивает дополнительные контексты
     * (например, примитивы и языковые конструкции из shlang). Все они
     * попадают в один список ДО создания {@link PlatformContextProvider}
     * — а значит {@code processRawTypes} в его конструкторе сможет
     * резолвить, например, имя «Строка» в shlang-{@code PrimitivePlaceholderType}
     * по {@code ==}-идентичности.
     *
     * @param tree     дерево shcntx (TableOfContent)
     * @param extra    дополнительные контексты (shlang-примитивы и keyword'ы)
     */
    public List<Context> parse(TableOfContent tree, List<Context> extra) {
        // Произвольный — псевдо-маркер «любой тип», в shlang страницы нет,
        // поэтому остаётся хардкодом. Остальные примитивы приходят через
        // extra из shlang.
        contexts.add(new ArbitraryType());
        contexts.addAll(extra);

        // Индекс путей страниц нужен HtmlParser'у для квалификации ссылок в
        // секциях «Рекомендуется»/«См. также» (имя владельца — из href).
        var pageIndex = new HashMap<String, DoubleLanguageString>();
        indexPages(tree.getPages(), pageIndex);
        htmlParser.setPageIndex(pageIndex);

        countNodes(tree.getPages(), nodesPerPage);

        visitPagesFromTree(tree.getPages());

        return contexts;
    }

    /**
     * Рекурсивно собирает индекс {@code нормализованный htmlPath → заголовок}
     * по всему дереву страниц. {@code putIfAbsent} — первый-побеждает при
     * коллизии путей (как и в основном обходе).
     */
    private static void indexPages(List<Page> pages, Map<String, DoubleLanguageString> index) {
        for (var page : pages) {
            var htmlPath = page.htmlPath();
            if (htmlPath != null && !htmlPath.isEmpty()) {
                index.putIfAbsent(PageSource.normalize(htmlPath), page.title());
            }
            indexPages(page.children(), index);
        }
    }

    /**
     * Рекурсивно считает, сколько узлов оглавления ссылается на каждую
     * страницу.
     */
    private static void countNodes(List<Page> pages, Map<String, Integer> sink) {
        for (var page : pages) {
            var htmlPath = page.htmlPath();
            if (htmlPath != null && !htmlPath.isEmpty()) {
                sink.merge(PageSource.normalize(htmlPath), 1, Integer::sum);
            }
            countNodes(page.children(), sink);
        }
    }

    public void visitPagesFromTree(List<Page> pages) {

        pages.parallelStream()
            // страницы-заглушки не интересны в парсере. Исключение — рубрика
            // «Таблицы запросов»: у неё своей страницы нет, а дети — настоящие
            // страницы таблиц, и без спуска в неё вся ветка теряется.
            .filter(page -> !page.htmlPath().isEmpty() || isQueryTablesRubric(page))
            .forEach(page -> {
                if (isQueryTablesRubric(page)) {
                    visitQueryTablesRubric(page, null);
                } else if (isGlobalContextPage(page)) {
                    visitGlobalContextPage(page);
                } else if (isQueryTablePage(page)) {
                    // Обычно таблицы приходят через рубрику; сюда попадает
                    // только страница таблицы, оказавшаяся вне рубрики.
                    visitQueryTablePage(page, null);
                } else if (isCatalogPage(page)) {
                    visitPagesFromTree(page.children());
                } else if (isEnumPage(page)) {
                    visitEnumPage(page);
                } else {
                    visitTypePage(page);
                }
            });

    }

    public void visitGlobalContextPage(Page page) {
        List<ContextProperty> properties = Collections.emptyList();
        List<ContextMethod> methods = new ArrayList<>();
        List<ContextEvent> externalConnectionModuleEvents = Collections.emptyList();
        List<ContextEvent> sessionModuleEvents = Collections.emptyList();
        List<ContextEvent> ordinaryApplicationEvents = Collections.emptyList();
        List<ContextEvent> applicationEvents = Collections.emptyList();

        for (var subPage : page.children()) {
            if (subPage.title().en().equals("Свойства")) {
                properties = getPropertiesFromPage(subPage);
            } else if (PageSource.normalize(subPage.htmlPath()).contains("methods")) {
                methods.addAll(getMethodsFromPage(subPage));
            } else if (subPage.title().en().equals("События внешнего соединения")) {
                externalConnectionModuleEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События модуля сеанса")) {
                sessionModuleEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События обычного приложения")) {
                ordinaryApplicationEvents = getEventsFromPage(subPage);
            } else if (subPage.title().en().equals("События приложения")) {
                applicationEvents = getEventsFromPage(subPage);
            }
        }

        var pageInfo = htmlParser.parseGlobalContextPage(page);

        contexts.add(
            PlatformGlobalContext.builder()
                .properties(properties)
                .methods(methods)
                .applicationEvents(applicationEvents)
                .ordinaryApplicationEvents(ordinaryApplicationEvents)
                .sessionModuleEvents(sessionModuleEvents)
                .externalConnectionModuleEvents(externalConnectionModuleEvents)
                .sinceVersion(pageInfo.getSinceVersion())
                .deprecatedSinceVersion(pageInfo.getDeprecatedSinceVersion())
                .build()
        );
    }

    public void visitTypePage(Page page) {

        List<ContextProperty> properties = Collections.emptyList();
        List<ContextMethod> methods = Collections.emptyList();
        List<ContextEvent> events = Collections.emptyList();
        List<ContextConstructor> constructors = Collections.emptyList();
        List<ContextFormParameter> formParameters = Collections.emptyList();

        for (var subPage : page.children()) {
            // title.en() и title.ru() в зависимости от языка HBK могут содержать
            // что угодно (для en-HBK это «Properties/Methods/...»). Сматчиваем
            // и по локализованному, и по англоязычному варианту.
            var ru = subPage.title().ru();
            var en = subPage.title().en();
            if ("Свойства".equals(ru) || "Свойства".equals(en) || "Properties".equals(ru) || "Properties".equals(en)) {
                properties = getPropertiesFromPage(subPage);
            } else if ("Методы".equals(ru) || "Методы".equals(en) || "Methods".equals(ru) || "Methods".equals(en)) {
                methods = getMethodsFromPage(subPage);
            } else if ("События".equals(ru) || "События".equals(en) || "Events".equals(ru) || "Events".equals(en)) {
                events = getEventsFromPage(subPage);
            } else if ("Конструкторы".equals(ru) || "Конструкторы".equals(en) || "Constructors".equals(ru) || "Constructors".equals(en)) {
                constructors = getConstructors(subPage);
            } else if ("Параметры формы".equals(ru) || "Параметры формы".equals(en)
                || "Form parameters".equals(ru) || "Form parameters".equals(en)) {
                formParameters = getFormParametersFromPage(subPage);
            }
        }

        var pageInfo = htmlParser.parseTypePage(page);
        var name = contextName(page, pageInfo.getPageTitleRu(), pageInfo.getPageTitleEn());
        var collection = pageInfo.getCollectionInfo();

        // Если у страницы типа есть блок «Элементы коллекции:» — это коллекция
        // (Массив, Соответствие, Структура, ТаблицаЗначений и т.п.), публикуем
        // её как ContextCollection с типами элементов и доступными операциями
        // обхода / индексатора. Иначе — обычный ContextType (в том числе
        // типы-формы: параметры формы живут прямо на ContextType). Ни одна
        // страница с секцией «Параметры формы:» блока «Элементы коллекции:»
        // не имеет, так что на коллекциях список параметров всегда пуст.
        if (!collection.isEmpty()) {
            contexts.add(PlatformContextCollection.builder()
                .name(name)
                .methods(methods)
                .properties(properties)
                .events(events)
                .constructors(constructors)
                .description(pageInfo.getDescription())
                .notes(pageInfo.getNotes())
                .availabilities(mapAvailabilities(pageInfo.getAvailabilities()))
                .sinceVersion(pageInfo.getSinceVersion())
                .deprecatedSinceVersion(pageInfo.getDeprecatedSinceVersion())
                .examples(List.copyOf(pageInfo.getExamples()))
                .seeAlso(List.copyOf(pageInfo.getSeeAlso()))
                .recommendedReplacements(List.copyOf(pageInfo.getRecommendedReplacements()))
                .pagePath(PageSource.normalize(page.htmlPath()))
                .rawCollectionElementTypes(collection.rawElementTypes())
                .supportsForEach(collection.supportsForEach())
                .forEachDescription(collection.forEachDescription())
                .supportsIndexAccess(collection.supportsIndexAccess())
                .indexAccessDescription(collection.indexAccessDescription())
                .build());
        } else {
            contexts.add(PlatformContextType.builder()
                .name(name)
                .methods(methods)
                .properties(properties)
                .events(events)
                .constructors(constructors)
                .formParameters(formParameters)
                .description(pageInfo.getDescription())
                .notes(pageInfo.getNotes())
                .availabilities(mapAvailabilities(pageInfo.getAvailabilities()))
                .sinceVersion(pageInfo.getSinceVersion())
                .deprecatedSinceVersion(pageInfo.getDeprecatedSinceVersion())
                .examples(List.copyOf(pageInfo.getExamples()))
                .seeAlso(List.copyOf(pageInfo.getSeeAlso()))
                .recommendedReplacements(List.copyOf(pageInfo.getRecommendedReplacements()))
                .pagePath(PageSource.normalize(page.htmlPath()))
                .build());
        }
    }

    /**
     * Имя контекста: приоритет у заголовка самой страницы
     * ({@code V8SH_pagetitle}), оглавление — запасной вариант.
     * <p>
     * В оглавлении узел назван относительно родителя: под «Поле ввода» лежит
     * узел «Расширение», хотя страница называется «Расширение поля ввода
     * системного перечисления». Такое имя вне дерева бессмысленно и вдобавок
     * не уникально — в 8.3.27 так названы 209 из 2420 страниц-типов.
     * <p>
     * Заголовок уточняет ту сторону имени, на языке которой он написан:
     * в {@code shcntx_ru.hbk} он вида «Имя (Name)» и задаёт обе стороны, а в
     * {@code shcntx_root.hbk} — только английский, и тогда он уточняет alias.
     * Иначе имена ru- и en-провайдеров разъехались бы, и
     * {@link com.github._1c_syntax.bsl.context.platform.BilingualMerger}
     * перестал бы сопоставлять контексты по имени.
     * <p>
     * Исключение — страница, на которую ведёт несколько узлов оглавления: так
     * платформа оформляет переименования. {@code PlannerCommandSource.html} —
     * это и «ИсточникКомандПланировщика» (устаревший с 8.3.23), и
     * «ИсточникКомандПоляПланировщика», причём наборы значений у них разные.
     * Заголовок страницы один на всех, поэтому для таких узлов имя берётся из
     * оглавления — только оно их и различает.
     */
    private ContextName contextName(Page page, String pageTitleRu, String pageTitleEn) {
        var tocRu = page.title().ru();
        var tocEn = page.title().en();
        if (pageTitleRu == null || pageTitleRu.isBlank()
            || nodesPerPage.getOrDefault(PageSource.normalize(page.htmlPath()), 1) > 1) {
            return new ContextName(tocRu, tocEn);
        }
        if (tocRu != null && !tocRu.isBlank()
            && HtmlParser.hasCyrillic(tocRu) != HtmlParser.hasCyrillic(pageTitleRu)) {
            // Заголовок на языке alias'а (en-HBK) — уточняем только его.
            // Пустое имя узла языком не является: у страниц таблиц запросов
            // ru-узел безымянный, и обе стороны надо брать со страницы.
            return new ContextName(tocRu, pageTitleRu);
        }
        var en = pageTitleEn == null || pageTitleEn.isBlank() ? tocEn : pageTitleEn;
        return new ContextName(pageTitleRu, en);
    }

    /**
     * Обходит рубрику ветки «Таблицы запросов», прокидывая вниз признак
     * корреспонденции: у регистра бухгалтерии одноимённые таблицы описаны
     * дважды, и различает их только заголовок рубрики
     * (см. {@link #correspondenceOf(Page)}).
     */
    private void visitQueryTablesRubric(Page rubric, Boolean correspondence) {
        var inherited = correspondence == null ? correspondenceOf(rubric) : correspondence;
        for (var child : rubric.children()) {
            if (isQueryTablesRubric(child)) {
                visitQueryTablesRubric(child, inherited);
            } else if (isQueryTablePage(child)) {
                visitQueryTablePage(child, inherited);
            }
        }
    }

    /**
     * Признак корреспонденции по заголовку рубрики. Заголовок в оглавлении
     * записан на языке HBK: ru — «Таблицы регистра бухгалтерии (с поддержкой
     * корреспонденции)», en — «Accounting Register Tables (with correspondence
     * support)». {@code null}, если рубрика не про регистр бухгалтерии.
     */
    private static Boolean correspondenceOf(Page rubric) {
        var title = rubric.title().ru() == null || rubric.title().ru().isBlank()
            ? rubric.title().en() : rubric.title().ru();
        if (title == null) {
            return null;
        }
        var lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("без поддержки корреспонденции")
            || lower.contains("without correspondence support")) {
            return Boolean.FALSE;
        }
        if (lower.contains("с поддержкой корреспонденции")
            || lower.contains("with correspondence support")) {
            return Boolean.TRUE;
        }
        return null;
    }

    /**
     * Таблица языка запросов: имя и поля.
     * <p>
     * Имя таблицы и имена полей на ru-странице записаны парой
     * «русское (английское)», поэтому берутся из заголовка страницы тем же
     * путём, что и у типов — см. {@link #contextName(Page, String, String)}.
     * Часть имени, которую задаёт конфигурация, остаётся плейсхолдером
     * ({@code Справочник.<Имя справочника>}) и материализуется потребителем.
     */
    private void visitQueryTablePage(Page page, Boolean correspondence) {
        var pageInfo = htmlParser.parseTypePage(page);
        contexts.add(PlatformContextQueryTable.builder()
            .name(contextName(page, pageInfo.getPageTitleRu(), pageInfo.getPageTitleEn()))
            .fields(getQueryTableFieldsFromPage(page))
            .correspondence(correspondence)
            .description(pageInfo.getDescription())
            .notes(pageInfo.getNotes())
            .availabilities(mapAvailabilities(pageInfo.getAvailabilities()))
            .sinceVersion(pageInfo.getSinceVersion())
            .deprecatedSinceVersion(pageInfo.getDeprecatedSinceVersion())
            .examples(List.copyOf(pageInfo.getExamples()))
            .seeAlso(List.copyOf(pageInfo.getSeeAlso()))
            .recommendedReplacements(List.copyOf(pageInfo.getRecommendedReplacements()))
            .pagePath(PageSource.normalize(page.htmlPath()))
            .build());
    }

    /**
     * Поля таблицы.
     * <p>
     * Тип поля отдаётся сырой строкой: он бывает шаблонным
     * ({@code БизнесПроцессСсылка.<Имя бизнес-процесса>}), и подставить
     * в него имя объекта может только потребитель.
     */
    private List<ContextQueryTableField> getQueryTableFieldsFromPage(Page page) {
        return queryTableFieldNodes(page).stream()
            .map(it -> {
                var fieldInfo = htmlParser.parseQueryTableFieldPage(it);
                return (ContextQueryTableField) PlatformContextQueryTableField.builder()
                    .name(contextName(it, fieldInfo.getPageTitleRu(), fieldInfo.getPageTitleEn()))
                    .rawValueType(fieldInfo.getType())
                    .description(fieldInfo.getDescription())
                    .notes(fieldInfo.getNotes())
                    .build();
            })
            .toList();
    }

    private void visitEnumPage(Page page) {
        var properties = getEnumValuesFromPage(page);
        var pageDesc = htmlParser.parseEnumPage(page);
        var rawValueType = pageDesc.getValueType();
        // valueType хранится в индексе типов через ContextName с пустым en;
        // если en-маркер на странице будет добавлен — расширить здесь.
        ContextName valueType = (rawValueType == null || rawValueType.isBlank())
            ? null
            : new ContextName(rawValueType, "");
        var builder = PlatformContextEnum.builder()
            .name(contextName(page, pageDesc.getPageTitleRu(), pageDesc.getPageTitleEn()))
            .values(properties)
            .description(pageDesc.getDescription())
            .notes(pageDesc.getNotes())
            .availabilities(mapAvailabilities(pageDesc.getAvailabilities()))
            .sinceVersion(pageDesc.getSinceVersion())
            .deprecatedSinceVersion(pageDesc.getDeprecatedSinceVersion())
            .examples(List.copyOf(pageDesc.getExamples()))
            .seeAlso(List.copyOf(pageDesc.getSeeAlso()))
            .recommendedReplacements(List.copyOf(pageDesc.getRecommendedReplacements()))
            .pagePath(PageSource.normalize(page.htmlPath()));
        if (valueType != null) {
            builder.valueType(valueType);
        }
        contexts.add(builder.build());
    }

    private List<ContextEnumValue> getEnumValuesFromPage(Page page) {
        return page.children().stream()
            .filter(it -> PageSource.normalize(it.htmlPath()).contains("/properties/"))
            .map(it -> {
                var description = htmlParser.parseEnumValuePage(it);
                return new PlatformContextEnumValue(
                    new ContextName(it.title().ru(), it.title().en()),
                    description.getDescription(),
                    description.getSinceVersion(),
                    description.getDeprecatedSinceVersion(),
                    List.copyOf(description.getRecommendedReplacements())
                );
            })
            .collect(Collectors.toList());
    }

    private List<ContextMethod> getMethodsFromPage(Page page) {
        return page.children().stream()
            .map(it -> {

                var methodDescription = htmlParser.parseMethodPage(it);

                return PlatformContextMethod.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .description(methodDescription.getDescription())
                    .availabilities(mapAvailabilities(methodDescription.getAvailabilities()))
                    .rawReturnValues(methodDescription.getReturnValues())
                    .signatures(buildSignatures(methodDescription.getSignatures()))
                    .sinceVersion(methodDescription.getSinceVersion())
                    .deprecatedSinceVersion(methodDescription.getDeprecatedSinceVersion())
                    .returnValueDescription(methodDescription.getReturnValueDescription())
                    .notes(methodDescription.getNotes())
                    .examples(List.copyOf(methodDescription.getExamples()))
                    .seeAlso(List.copyOf(methodDescription.getSeeAlso()))
                    .recommendedReplacements(List.copyOf(methodDescription.getRecommendedReplacements()))
                    .async(isAsyncName(it.title().ru(), it.title().en()))
                    .build();

            })
            .collect(Collectors.toList());
    }

    private List<ContextConstructor> getConstructors(Page page) {
        return page.children().stream()
            .map(this::getConstructor)
            .collect(Collectors.toList());
    }

    private ContextConstructor getConstructor(Page page) {

        var constructorDescription = htmlParser.parseConstructorPage(page);

        return PlatformContextConstructor.builder()
            .name(new ContextName(page.title().ru(), page.title().en()))
            .description(constructorDescription.getDescription())
            .parameters(constructorDescription.getParameters().stream()
                .map(HbkTreeParser::buildParameter)
                .map(p -> (ContextSignatureParameter) p)
                .toList())
            .sinceVersion(constructorDescription.getSinceVersion())
            .deprecatedSinceVersion(constructorDescription.getDeprecatedSinceVersion())
            .syntaxText(constructorDescription.getSyntaxText())
            .recommendedReplacements(List.copyOf(constructorDescription.getRecommendedReplacements()))
            .examples(List.copyOf(constructorDescription.getExamples()))
            .seeAlso(List.copyOf(constructorDescription.getSeeAlso()))
            .build();
    }

    private List<ContextEvent> getEventsFromPage(Page page) {
        return page.children().stream()
            .map(it -> {

                var methodDescription = htmlParser.parseMethodPage(it);

                return PlatformContextEvent.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .description(methodDescription.getDescription())
                    .availabilities(mapAvailabilities(methodDescription.getAvailabilities()))
                    .signatures(buildSignatures(methodDescription.getSignatures()))
                    .sinceVersion(methodDescription.getSinceVersion())
                    .deprecatedSinceVersion(methodDescription.getDeprecatedSinceVersion())
                    .recommendedReplacements(List.copyOf(methodDescription.getRecommendedReplacements()))
                    .notes(methodDescription.getNotes())
                    .examples(List.copyOf(methodDescription.getExamples()))
                    .seeAlso(List.copyOf(methodDescription.getSeeAlso()))
                    .build();

            })
            .collect(Collectors.toList());
    }

    private static List<Availability> mapAvailabilities(List<String> raw) {
        return raw.stream()
            .map(Availability::findByName)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }

    /**
     * Async-метод платформы определяется по суффиксу имени: {@code Асинх} (ru)
     * или {@code Async} (en). Это конвенция await-методов 8.3.18+; callback-методы
     * ({@code Начать…} / {@code Begin…}) суффикса не имеют.
     */
    private static boolean isAsyncName(String ru, String en) {
        return ru != null && ru.endsWith("Асинх") || en != null && en.endsWith("Async");
    }

    private static List<ContextMethodSignature> buildSignatures(
        List<HtmlParser.MethodSignatureDescription> raw) {
        return raw.stream()
            .map(sigDesc -> PlatformContextMethodSignature.builder()
                .description(sigDesc.getDescription())
                .name(new ContextName(sigDesc.getName(), ""))
                .parameters(sigDesc.getParameters().stream()
                    .map(HbkTreeParser::buildParameter)
                    .map(p -> (ContextSignatureParameter) p)
                    .toList())
                .syntaxText(sigDesc.getSyntaxText())
                .build())
            .map(s -> (ContextMethodSignature) s)
            .toList();
    }

    private static PlatformContextSignatureParameter buildParameter(
        HtmlParser.MethodSignatureParameterDescription paramDesc) {
        return PlatformContextSignatureParameter.builder()
            .description(paramDesc.getDescription())
            .name(new ContextName(paramDesc.getName(), ""))
            .isRequired(paramDesc.isRequired())
            .rawTypes(paramDesc.getTypes())
            .defaultValue(paramDesc.getDefaultValue())
            .variadic(paramDesc.isVariadic())
            .build();
    }

    /**
     * Собирает параметры формы из страницы-рубрики «Параметры формы».
     * Страницы самих параметров лежат в подкаталоге {@code formparams/}
     * рядом с {@code properties/} и {@code methods/} типа-формы.
     */
    private List<ContextFormParameter> getFormParametersFromPage(Page page) {
        return page.children().stream()
            .filter(it -> PageSource.normalize(it.htmlPath()).contains("/formparams/"))
            .map(it -> {

                var description = htmlParser.parseFormParameterPage(it);

                return (ContextFormParameter) PlatformContextFormParameter.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .rawTypes(description.getTypes())
                    .description(description.getDescription())
                    .key(description.isKey())
                    .sinceVersion(description.getSinceVersion())
                    .deprecatedSinceVersion(description.getDeprecatedSinceVersion())
                    .recommendedReplacements(List.copyOf(description.getRecommendedReplacements()))
                    .seeAlso(List.copyOf(description.getSeeAlso()))
                    .build();

            })
            .collect(Collectors.toList());
    }

    private List<ContextProperty> getPropertiesFromPage(Page page) {
        // Свойства с именем, начинающимся с «<» (например, «<Имя справочника>»),
        // — generic-плейсхолдеры, заполняемые из конфигурации. Не отбрасываем,
        // а помечаем флагом isGeneric() (см. ContextProperty / ContextNames).
        return page.children().stream()
            .filter(it -> PageSource.normalize(it.htmlPath()).contains("/properties/"))
            .map(it -> {

                var propertyDescription = htmlParser.parsePropertyPage(it);

                var accessMode = AccessMode.findByName(propertyDescription.getAccessMode());

                return PlatformContextProperty.builder()
                    .name(new ContextName(it.title().ru(), it.title().en()))
                    .accessMode(accessMode.orElse(AccessMode.READ_WRITE))
                    .rawTypes(propertyDescription.getTypes())
                    .rawCollectionElementTypes(propertyDescription.getRawCollectionElementTypes())
                    .description(propertyDescription.getDescription())
                    .availabilities(mapAvailabilities(propertyDescription.getAvailabilities()))
                    .sinceVersion(propertyDescription.getSinceVersion())
                    .deprecatedSinceVersion(propertyDescription.getDeprecatedSinceVersion())
                    .recommendedReplacements(List.copyOf(propertyDescription.getRecommendedReplacements()))
                    .notes(propertyDescription.getNotes())
                    .seeAlso(List.copyOf(propertyDescription.getSeeAlso()))
                    .examples(List.copyOf(propertyDescription.getExamples()))
                    .build();

            })
            .collect(Collectors.toList());
    }

    /**
     * Имя страницы-каталога (узла-рубрики дерева СП): {@code catalogNNNN.html}.
     * Регистр значимый — страницы с заглавной {@code Catalog…}
     * ({@code Catalog2779.html}, {@code CatalogsManager.html}) это типы, а не
     * рубрики.
     */
    private static final Pattern CATALOG_PAGE_NAME = Pattern.compile("catalog\\d+(\\.html)?");

    /**
     * Страница-каталог — узел-рубрика дерева, у которого нет собственного
     * содержимого: парсер не создаёт по ней контекст, а спускается в детей.
     * <p>
     * Проверять «имя файла содержит catalog» нельзя: единственная страница СП
     * с осмысленным именем-фразой — {@code Client application form extension
     * for catalogs.html} («Расширение справочника») — тогда принимается за
     * рубрику, и весь тип вместе со своими членами и параметрами формы молча
     * теряется (её дети — рубрики с пустым htmlPath — на следующем витке
     * отфильтровываются).
     */
    private boolean isCatalogPage(Page page) {
        var elements = PageSource.normalize(page.htmlPath()).split("/");
        String endElement;
        if (elements.length > 1) {
            endElement = elements[elements.length - 1];
        } else {
            endElement = elements[0];
        }

        return CATALOG_PAGE_NAME.matcher(endElement).matches();
    }

    /**
     * Рубрика «Таблицы запросов» — узел без своей страницы, дети которого
     * лежат в ветке {@code tables}. Опознаётся структурой, а не заголовком:
     * заголовок в ru-оглавлении пуст.
     */
    private static boolean isQueryTablesRubric(Page page) {
        var path = page.htmlPath();
        if (path != null && !path.isEmpty() || page.children().isEmpty()) {
            return false;
        }
        // Рубрика бывает вложенной: «Таблицы запросов» → «Таблицы регистра
        // бухгалтерии» → страницы таблиц. Внутри неё не должно быть ничего,
        // кроме таких же рубрик и страниц ветки tables.
        for (var child : page.children()) {
            var childPath = child.htmlPath();
            var empty = childPath == null || childPath.isEmpty();
            if (empty ? !isQueryTablesRubric(child)
                : !PageSource.normalize(childPath).startsWith("tables/")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Страница таблицы языка запросов ({@code tables/table58.html},
     * {@code tables/catalog36/table42.html}). Поля лежат не прямыми детьми,
     * а под безымянной рубрикой «Поля», поэтому проверяется наличие хоть
     * одного узла {@code /fields/} на два уровня вглубь.
     */
    private static boolean isQueryTablePage(Page page) {
        return PageSource.normalize(page.htmlPath()).startsWith("tables/")
            && !queryTableFieldNodes(page).isEmpty();
    }

    /**
     * Узлы полей таблицы: прямые дети со страницей {@code /fields/…} плюс
     * дети безымянных рубрик того же узла.
     */
    private static List<Page> queryTableFieldNodes(Page page) {
        var result = new ArrayList<Page>();
        for (var child : page.children()) {
            if (PageSource.normalize(child.htmlPath()).contains("/fields/")) {
                result.add(child);
            } else if (child.htmlPath() == null || child.htmlPath().isEmpty()) {
                child.children().stream()
                    .filter(it -> PageSource.normalize(it.htmlPath()).contains("/fields/"))
                    .forEach(result::add);
            }
        }
        return result;
    }

    private boolean isEnumPage(Page page) {
        // FIXME нужна проверка более точная
        return page.children().stream().anyMatch(it -> PageSource.normalize(it.htmlPath()).contains("/properties/"));
    }

    private boolean isGlobalContextPage(Page page) {
        return page.htmlPath().contains("Global context.html");
    }
}
