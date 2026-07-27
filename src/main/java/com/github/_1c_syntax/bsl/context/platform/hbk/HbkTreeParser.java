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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    /** Пути страниц, по которым контекст уже создан — см. {@link #claimPage(Page)}. */
    private final Set<String> visitedPages = ConcurrentHashMap.newKeySet();
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

    public void visitPagesFromTree(List<Page> pages) {

        pages.parallelStream()
            // страницы-заглушки не интересны в парсере
            .filter(page -> !page.htmlPath().isEmpty())
            .forEach(page -> {
                if (isGlobalContextPage(page)) {
                    visitGlobalContextPage(page);
                } else if (isCatalogPage(page)) {
                    visitPagesFromTree(page.children());
                } else if (isEnumPage(page)) {
                    if (claimPage(page)) {
                        visitEnumPage(page);
                    }
                } else {
                    if (claimPage(page)) {
                        visitTypePage(page);
                    }
                }
            });

    }

    /**
     * Резервирует страницу за создаваемым контекстом; {@code false}, если по
     * ней контекст уже построен.
     * <p>
     * В оглавлении одна и та же страница иногда висит несколькими узлами с
     * разными подписями: {@code PlannerCommandSource.html} — это и
     * «ИсточникКомандПланировщика», и «ИсточникКомандПоляПланировщика» (в en-HBK
     * второй узел помечен суффиксом {@code #&^@^%&*^#1}). Имя контекста берётся
     * с заголовка страницы, а он один на всех, поэтому такие узлы дали бы
     * несколько одинаковых контекстов с одинаковым составом членов.
     * <p>
     * Обход идёт через {@code parallelStream}, поэтому набор конкурентный.
     */
    private boolean claimPage(Page page) {
        return visitedPages.add(PageSource.normalize(page.htmlPath()));
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
     */
    private static ContextName contextName(Page page, String pageTitleRu, String pageTitleEn) {
        var tocRu = page.title().ru();
        var tocEn = page.title().en();
        if (pageTitleRu == null || pageTitleRu.isBlank()) {
            return new ContextName(tocRu, tocEn);
        }
        if (HtmlParser.hasCyrillic(tocRu) != HtmlParser.hasCyrillic(pageTitleRu)) {
            // Заголовок на языке alias'а (en-HBK) — уточняем только его.
            return new ContextName(tocRu, pageTitleRu);
        }
        var en = pageTitleEn == null || pageTitleEn.isBlank() ? tocEn : pageTitleEn;
        return new ContextName(pageTitleRu, en);
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

    private boolean isEnumPage(Page page) {
        // FIXME нужна проверка более точная
        return page.children().stream().anyMatch(it -> PageSource.normalize(it.htmlPath()).contains("/properties/"));
    }

    private boolean isGlobalContextPage(Page page) {
        return page.htmlPath().contains("Global context.html");
    }
}
