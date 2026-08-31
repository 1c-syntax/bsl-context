package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.ContextQueryElement;
import com.github._1c_syntax.bsl.context.api.ContextQueryParameter;
import com.github._1c_syntax.bsl.context.api.QueryElementCategory;
import com.github._1c_syntax.bsl.context.api.QueryFunctionGroup;
import com.github._1c_syntax.bsl.context.platform.PlatformQueryElement;
import com.github._1c_syntax.bsl.context.platform.PlatformQueryParameter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Парсер {@code shquery_*.hbk} — раздела справки «Язык запросов»: агрегатные
 * функции ({@code СРЕДНЕЕ}, {@code КОЛИЧЕСТВО}), функции ({@code ПОДСТРОКА},
 * {@code РАЗНОСТЬДАТ}), ключевые слова ({@code ВЫБРАТЬ}, {@code ПЕРВЫЕ}),
 * предложения ({@code ИЗ}), операторы, литералы и обзорные статьи.
 * <p>
 * Страницы там размечены свободным HTML — {@code <H1>} с заголовком,
 * абзацы {@code <P>} и блоки примеров после {@code <H4>Пример:</H4>}, без
 * {@code V8SH_*}-классов, на которые опирается {@link HtmlParser}. Структура
 * поэтому берётся из оглавления ({@link ShqueryToc}): ветка задаёт категорию
 * и вид функции, а вложенность узлов — место конструкции в тексте запроса.
 * С заголовка снимается только имя: «Агрегатная функция СРЕДНЕЕ» →
 * {@code СРЕДНЕЕ}, «SELECT clause» → {@code SELECT}.
 * <p>
 * Контейнер {@code shquery_ru.hbk} не читается обычным {@code ZipInputStream}
 * («invalid distance too far back»), поэтому страницы достаются
 * brute-force-сканером {@link ShlangParser#readAllPages(byte[])}.
 */
public final class ShqueryParser {

    private ShqueryParser() {
    }

    /** Ветки оглавления, задающие категорию элемента (ru- и en-заголовки). */
    private static final Map<String, QueryElementCategory> CATEGORY_BRANCHES = Map.ofEntries(
        Map.entry("Ключевые слова", QueryElementCategory.KEYWORD),
        Map.entry("Keywords", QueryElementCategory.KEYWORD),
        Map.entry("Функции", QueryElementCategory.FUNCTION),
        Map.entry("Functions", QueryElementCategory.FUNCTION),
        Map.entry("Функции языка запросов", QueryElementCategory.FUNCTION),
        Map.entry("Functions of Query Language", QueryElementCategory.FUNCTION),
        Map.entry("Агрегатные функции", QueryElementCategory.FUNCTION),
        Map.entry("Aggregate functions", QueryElementCategory.FUNCTION),
        Map.entry("Операторы", QueryElementCategory.OPERATOR),
        Map.entry("Operators", QueryElementCategory.OPERATOR),
        Map.entry("Логические выражения", QueryElementCategory.OPERATOR),
        Map.entry("Logical expressions", QueryElementCategory.OPERATOR),
        Map.entry("Константы и параметры (значение)", QueryElementCategory.LITERAL),
        Map.entry("Constants and parameters (value)", QueryElementCategory.LITERAL)
    );

    /** Подразделы ветки «Функции» — вид функции. */
    private static final Map<String, QueryFunctionGroup> FUNCTION_GROUPS = Map.ofEntries(
        Map.entry("Функции работы со строками", QueryFunctionGroup.STRING),
        Map.entry("String functions", QueryFunctionGroup.STRING),
        Map.entry("Функции работы с датами", QueryFunctionGroup.DATE),
        Map.entry("Date functions", QueryFunctionGroup.DATE),
        Map.entry("Математические функции", QueryFunctionGroup.MATH),
        Map.entry("Mathematical functions", QueryFunctionGroup.MATH),
        Map.entry("Агрегатные функции", QueryFunctionGroup.AGGREGATE),
        Map.entry("Aggregate functions", QueryFunctionGroup.AGGREGATE),
        Map.entry("Прочие функции", QueryFunctionGroup.OTHER),
        Map.entry("Other functions", QueryFunctionGroup.OTHER)
    );

    /**
     * Общий список функций — вида функции не задаёт: в него платформа
     * складывает всё подряд, а деление на строковые/датные/математические
     * лежит подразделами ветки «Функции».
     */
    private static final Set<String> GENERIC_FUNCTION_BRANCHES = Set.of(
        "Функции языка запросов", "Functions of Query Language");

    /** Ветка, описывающая структуру текста запроса — источник parent/children. */
    private static final Set<String> STRUCTURE_BRANCHES = Set.of("Текст запроса", "Query text");

    /** Маркеры в заголовке узла, по которым видно секцию и предложение. */
    private static final List<String> SECTION_MARKERS = List.of("Секция ", " section", " Section");
    private static final List<String> CLAUSE_MARKERS = List.of(
        "Предложение ", " clause", " Clause", " statement", " Statement");

    /** Порядковые слова, которыми справка нумерует параметры функции. */
    private static final List<String> PARAMETER_ORDINALS = List.of(
        "Первый параметр", "Второй параметр", "Третий параметр", "Четвертый параметр",
        "Четвёртый параметр", "Пятый параметр", "Шестой параметр", "Седьмой параметр");

    /**
     * Разбирает пару shquery-контейнеров.
     * <p>
     * Структура берётся из оглавления, а не из заголовков страниц: оно задаёт
     * категорию, вид функции и место конструкции в тексте запроса. Ведущим
     * выбирается en-оглавление — в ru-контейнере эта запись распаковывается
     * повреждённой (обрывается на последней трети, теряя строковые и
     * математические функции), тогда как en читается целиком. Русские имена
     * при этом берутся с ru-страниц, которые в контейнере есть.
     *
     * @param ruFileStorage содержимое {@code shquery_ru.hbk}
     * @param enFileStorage содержимое {@code shquery_root.hbk}; может быть
     *                      {@code null} — тогда работаем по ru-оглавлению
     */
    public static List<ContextQueryElement> parse(byte[] ruFileStorage, byte[] enFileStorage) {
        var ruPages = ShlangParser.readAllPages(ruFileStorage);
        var enPages = enFileStorage == null
            ? Map.<String, byte[]>of()
            : ShlangParser.readAllPages(enFileStorage);

        var ruToc = ShqueryToc.from(ruPages);
        var enToc = ShqueryToc.from(enPages);
        var toc = enToc.isEmpty() ? ruToc : enToc;
        if (toc.isEmpty()) {
            return List.of();
        }

        var nodesByPage = new LinkedHashMap<String, List<ShqueryToc.Node>>();
        for (var node : toc.pageNodes()) {
            nodesByPage.computeIfAbsent(node.pageKey(), key -> new ArrayList<>()).add(node);
        }

        var result = new ArrayList<ContextQueryElement>();
        for (var entry : nodesByPage.entrySet()) {
            var element = buildElement(entry.getKey(), entry.getValue(), toc, ruToc, ruPages, enPages);
            if (element != null) {
                result.add(element);
            }
        }
        return mergeByName(result);
    }

    /** Категории в порядке «объемлющая раньше» — для слияния одноимённых страниц. */
    private static final List<QueryElementCategory> CATEGORY_PRIORITY = List.of(
        QueryElementCategory.SECTION, QueryElementCategory.CLAUSE, QueryElementCategory.KEYWORD,
        QueryElementCategory.FUNCTION, QueryElementCategory.OPERATOR, QueryElementCategory.LITERAL,
        QueryElementCategory.ARTICLE);

    /**
     * Сливает страницы, описывающие одну и ту же конструкцию. Справка
     * рассказывает про {@code ВЫБРАТЬ} трижды — как про секцию запроса, как
     * про предложение внутри неё и как про ключевое слово, — но в языке это
     * одно слово, и трёх элементов с одним именем модель давать не должна.
     */
    private static List<ContextQueryElement> mergeByName(List<ContextQueryElement> elements) {
        var byName = new LinkedHashMap<String, List<ContextQueryElement>>();
        for (var element : elements) {
            byName.computeIfAbsent(element.name().getName(), key -> new ArrayList<>()).add(element);
        }
        var result = new ArrayList<ContextQueryElement>();
        for (var group : byName.values()) {
            result.add(group.size() == 1 ? group.get(0) : merge(group));
        }
        return result;
    }

    private static ContextQueryElement merge(List<ContextQueryElement> group) {
        var sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparingInt(e -> CATEGORY_PRIORITY.indexOf(e.category())));
        var main = sorted.get(0);

        var children = new ArrayList<ContextName>();
        var seeAlso = new ArrayList<String>();
        var seeAlsoEn = new ArrayList<String>();
        var examples = new ArrayList<String>();
        var examplesEn = new ArrayList<String>();
        var description = "";
        var descriptionEn = "";
        var syntaxRule = "";
        var parameters = List.<ContextQueryParameter>of();
        ContextName parent = null;

        for (var element : sorted) {
            for (var child : element.children()) {
                // Предложение ВЫБРАТЬ — ребёнок секции ВЫБРАТЬ; после слияния
                // это ссылка на самого себя.
                if (!child.getName().equals(main.name().getName()) && !contains(children, child)) {
                    children.add(child);
                }
            }
            addNew(seeAlso, element.seeAlso());
            addNew(seeAlsoEn, element.seeAlsoEn());
            addNew(examples, element.examples());
            addNew(examplesEn, element.examplesEn());
            if (element.description().length() > description.length()) {
                description = element.description();
            }
            if (element.descriptionEn().length() > descriptionEn.length()) {
                descriptionEn = element.descriptionEn();
            }
            if (syntaxRule.isBlank()) {
                syntaxRule = element.syntaxRule();
            }
            if (parameters.isEmpty()) {
                parameters = element.parameters();
            }
            if (parent == null && element.parent() != null
                && !element.parent().getName().equals(main.name().getName())) {
                parent = element.parent();
            }
        }

        return PlatformQueryElement.builder()
            .name(main.name())
            .category(main.category())
            .functionGroup(main.functionGroup())
            .parent(parent)
            .children(children)
            .syntaxRule(syntaxRule)
            .parameters(parameters)
            .description(description)
            .descriptionEn(descriptionEn)
            .examples(examples)
            .examplesEn(examplesEn)
            .seeAlso(seeAlso)
            .seeAlsoEn(seeAlsoEn)
            .pagePath(main instanceof PlatformQueryElement p ? p.pagePath() : "")
            .build();
    }

    private static boolean contains(List<ContextName> names, ContextName candidate) {
        for (var name : names) {
            if (name.getName().equals(candidate.getName())) {
                return true;
            }
        }
        return false;
    }

    private static void addNew(List<String> sink, List<String> source) {
        for (var item : source) {
            if (!sink.contains(item)) {
                sink.add(item);
            }
        }
    }

    private static ContextQueryElement buildElement(String pageKey, List<ShqueryToc.Node> nodes,
                                                    ShqueryToc toc, ShqueryToc ruToc,
                                                    Map<String, byte[]> ruPages,
                                                    Map<String, byte[]> enPages) {
        var ruDocument = document(ruPages.get(pageKey));
        var enDocument = document(enPages.get(pageKey));
        if (ruDocument == null && enDocument == null) {
            return null;
        }

        var categoryNode = pickCategoryNode(nodes, toc);
        var category = categoryOf(nodes, toc);
        var structureNode = pickStructureNode(nodes, toc);

        var ruTocNode = ruToc.byPath(categoryNode.path());
        var ruName = cleanTitle(headingText(ruDocument));
        if (ruName.isEmpty() && ruTocNode != null) {
            ruName = cleanTitle(ruTocNode.title());
        }
        var enName = cleanTitle(headingText(enDocument));
        if (enName.isEmpty()) {
            enName = cleanTitle(categoryNode.title());
        }
        if (ruName.isEmpty()) {
            ruName = enName;
        }
        if (ruName.isEmpty()) {
            return null;
        }

        var main = ruDocument == null ? enDocument : ruDocument;
        var parameters = parameters(main, enDocument == main ? null : enDocument);

        return PlatformQueryElement.builder()
            .name(new ContextName(ruName, enName))
            .category(category)
            .functionGroup(category == QueryElementCategory.FUNCTION
                ? functionGroupOf(nodes, toc) : QueryFunctionGroup.NONE)
            .parent(parentName(structureNode, toc, ruToc, ruPages))
            .children(childrenNames(structureNode, toc, ruToc, ruPages))
            .syntaxRule(syntaxRule(main, ruName))
            .parameters(parameters)
            .description(ruDocument == null ? "" : description(ruDocument))
            .examples(ruDocument == null ? List.of() : examples(ruDocument))
            .seeAlso(ruDocument == null ? List.of() : seeAlso(ruDocument))
            .descriptionEn(enDocument == null ? "" : description(enDocument))
            .examplesEn(enDocument == null ? List.of() : examples(enDocument))
            .seeAlsoEn(enDocument == null ? List.of() : seeAlso(enDocument))
            .pagePath(pageKey)
            .build();
    }

    /** Текст первого {@code <H1>} страницы; пусто, если заголовка нет. */
    private static String headingText(Document document) {
        if (document == null) {
            return "";
        }
        var h1 = document.selectFirst("h1");
        return h1 == null ? "" : h1.text().strip();
    }

    private static Document document(byte[] bytes) {
        return bytes == null ? null : Jsoup.parse(new String(bytes, StandardCharsets.UTF_8), "");
    }

    /**
     * Узел, по которому определяется категория. На одну страницу оглавление
     * нередко ссылается дважды: из ветки «Текст запроса» (там узел назван
     * «Предложение ВЫБРАТЬ») и из ветки «Ключевые слова и функции» (там —
     * просто «ВЫБРАТЬ»). Вторая ветка классифицирует точнее, поэтому она
     * в приоритете.
     */
    private static ShqueryToc.Node pickCategoryNode(List<ShqueryToc.Node> nodes, ShqueryToc toc) {
        // Узел из подраздела «Функции работы со строками» и т.п. точнее: он
        // задаёт ещё и вид функции, тогда как общий список «Функции языка
        // запросов» вида не даёт.
        for (var node : nodes) {
            if (toc.ancestorByTitle(node, FUNCTION_GROUPS.keySet()) != null) {
                return node;
            }
        }
        for (var node : nodes) {
            if (toc.ancestorByTitle(node, GENERIC_FUNCTION_BRANCHES) != null) {
                return node;
            }
        }
        for (var node : nodes) {
            if (toc.ancestorByTitle(node, CATEGORY_BRANCHES.keySet()) != null) {
                return node;
            }
        }
        return nodes.get(0);
    }

    /** Узел из ветки «Текст запроса» — он задаёт место конструкции в запросе. */
    private static ShqueryToc.Node pickStructureNode(List<ShqueryToc.Node> nodes, ShqueryToc toc) {
        for (var node : nodes) {
            if (toc.ancestorByTitle(node, STRUCTURE_BRANCHES) != null) {
                return node;
            }
        }
        return null;
    }

    /**
     * Категория страницы. Секцию и предложение выдаёт сам заголовок узла
     * («Секция ВЫБРАТЬ (Описание запроса)», «FROM clause»), причём достаточно
     * любого из узлов страницы: в ветке «Ключевые слова» тот же ИЗ назван
     * просто «FROM», и по нему предложение не отличить от ключевого слова.
     */
    private static QueryElementCategory categoryOf(List<ShqueryToc.Node> nodes, ShqueryToc toc) {
        for (var node : nodes) {
            if (containsAny(node.title(), SECTION_MARKERS)) {
                return QueryElementCategory.SECTION;
            }
        }
        for (var node : nodes) {
            if (containsAny(node.title(), CLAUSE_MARKERS)) {
                return QueryElementCategory.CLAUSE;
            }
        }
        for (var node : nodes) {
            var branch = toc.ancestorByTitle(node, CATEGORY_BRANCHES.keySet());
            if (branch != null) {
                return CATEGORY_BRANCHES.get(branch.title());
            }
        }
        return QueryElementCategory.ARTICLE;
    }

    private static QueryFunctionGroup functionGroupOf(List<ShqueryToc.Node> nodes, ShqueryToc toc) {
        for (var node : nodes) {
            var group = toc.ancestorByTitle(node, FUNCTION_GROUPS.keySet());
            if (group != null) {
                return FUNCTION_GROUPS.get(group.title());
            }
        }
        return QueryFunctionGroup.OTHER;
    }

    private static ContextName parentName(ShqueryToc.Node node, ShqueryToc toc,
                                          ShqueryToc ruToc, Map<String, byte[]> ruPages) {
        if (node == null) {
            return null;
        }
        var parent = toc.parentOf(node);
        if (parent == null || STRUCTURE_BRANCHES.contains(parent.title())) {
            return null;
        }
        return nameOf(parent, ruToc, ruPages);
    }

    private static List<ContextName> childrenNames(ShqueryToc.Node node, ShqueryToc toc,
                                                   ShqueryToc ruToc, Map<String, byte[]> ruPages) {
        if (node == null) {
            return List.of();
        }
        var result = new ArrayList<ContextName>();
        for (var child : toc.childrenOf(node)) {
            var name = nameOf(child, ruToc, ruPages);
            if (name != null && !name.getName().isBlank()) {
                result.add(name);
            }
        }
        return result;
    }

    /** Двуязычное имя узла: ru — со страницы или ru-оглавления, en — из оглавления. */
    private static ContextName nameOf(ShqueryToc.Node node, ShqueryToc ruToc,
                                      Map<String, byte[]> ruPages) {
        var en = cleanTitle(node.title());
        var ru = "";
        if (!node.path().isBlank()) {
            ru = cleanTitle(headingText(document(ruPages.get(node.pageKey()))));
            if (ru.isEmpty()) {
                var ruNode = ruToc.byPath(node.path());
                ru = ruNode == null ? "" : cleanTitle(ruNode.title());
            }
        }
        return new ContextName(ru.isEmpty() ? en : ru, en);
    }

    /**
     * Снимает с заголовка служебный префикс и хвост в скобках:
     * «Секция ВЫБРАТЬ (Описание запроса)» → {@code ВЫБРАТЬ},
     * «Функция ПОДСТРОКА» → {@code ПОДСТРОКА}. Описательные заголовки
     * («Правила сравнения значений») остаются как есть — это статьи.
     */
    private static String cleanTitle(String heading) {
        if (heading == null || heading.isBlank()) {
            return "";
        }
        var text = normalizeSpaces(heading);
        for (var prefix : PREFIXES_TO_STRIP) {
            if (text.regionMatches(true, 0, prefix, 0, prefix.length())) {
                text = text.substring(prefix.length()).strip();
                break;
            }
        }
        // В скобках либо перевод («Функция Лев(Left)»), либо пояснение
        // («Секция ВЫБРАТЬ (Описание запроса)») — именем не является ни то,
        // ни другое.
        var bracket = text.indexOf('(');
        if (bracket > 0) {
            text = text.substring(0, bracket).strip();
        }
        // В английских заголовках маркер стоит после имени: «SELECT clause»,
        // «AVG Aggregate Function», «TOP Keyword».
        for (var suffix : SUFFIXES_TO_STRIP) {
            if (text.regionMatches(true, text.length() - suffix.length(), suffix, 0, suffix.length())) {
                text = text.substring(0, text.length() - suffix.length()).strip();
                break;
            }
        }
        return text.strip();
    }

    private static final List<String> SUFFIXES_TO_STRIP = List.of(
        " aggregate function", " function", " keyword", " clause", " statement",
        " section", " type literal", " literal", " operator");

    private static final List<String> PREFIXES_TO_STRIP = List.of(
        "Агрегатная функция ", "Функция ", "Ключевое слово ", "Предложение ", "Секция ",
        "Литерал типа ", "Литерал ", "Оператор ",
        "Aggregate function ", "Function ", "Keyword ", "Clause ", "Section ",
        "Type literal ", "Literal ", "Operator ");

    private static boolean startsWithAny(String text, List<String> prefixes) {
        for (var prefix : prefixes) {
            if (text.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAny(String text, List<String> markers) {
        for (var marker : markers) {
            if (text.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Синтаксическое правило конструкции — первый блок страницы, если он
     * идёт до всех заголовков и задаёт форму:
     * {@code ВЫБРАТЬ [РАЗРЕШЕННЫЕ] … <Список полей выборки>}. Блоки после
     * «Пример:» — это код, а не правило.
     */
    private static String syntaxRule(Document document, String name) {
        if (document == null || document.body() == null) {
            return "";
        }
        for (var element : document.body().select("h4, blockquote")) {
            if (!"blockquote".equalsIgnoreCase(element.tagName())) {
                return "";
            }
            var text = codeText(element);
            if (text.isEmpty() || isSeeAlso(text)) {
                continue;
            }
            var head = text.split("\\R", 2)[0];
            if (head.contains("<") || head.startsWith(name)) {
                return text;
            }
            return "";
        }
        return "";
    }

    /**
     * Параметры функции. Справка нумерует их прозой — «Первый параметр – строка,
     * из которой необходимо выделить подстроку», — а типы даёт ссылками на
     * страницы литералов, поэтому имён у параметров нет, только позиция,
     * описание и типы.
     */
    private static List<ContextQueryParameter> parameters(Document ru, Document en) {
        if (ru == null) {
            return List.of();
        }
        var byPosition = new TreeMap<Integer, PlatformQueryParameter>();
        collectParameters(ru, byPosition, false);
        if (en != null) {
            collectParameters(en, byPosition, true);
        }
        return List.copyOf(byPosition.values());
    }

    private static void collectParameters(Document document,
                                          TreeMap<Integer, PlatformQueryParameter> sink,
                                          boolean english) {
        for (var paragraph : document.select("p, li")) {
            if (isInsideCodeOrTable(paragraph)) {
                continue;
            }
            var text = normalizeSpaces(paragraph.text());
            var position = ordinalOf(text, english);
            if (position <= 0) {
                continue;
            }
            if (english) {
                var existing = sink.get(position);
                if (existing != null) {
                    existing.setDescriptionEn(text);
                }
                continue;
            }
            var types = new ArrayList<String>();
            for (var link : paragraph.select("a")) {
                var type = normalizeSpaces(link.text());
                if (!type.isEmpty() && !types.contains(type)) {
                    types.add(type);
                }
            }
            sink.putIfAbsent(position, PlatformQueryParameter.builder()
                .position(position)
                .description(text)
                .types(List.copyOf(types))
                .build());
        }
    }

    /** Номер параметра по порядковому слову в начале абзаца; 0 — не параметр. */
    private static int ordinalOf(String text, boolean english) {
        if (english) {
            var lower = text.toLowerCase(Locale.ROOT);
            var ordinals = List.of("the first parameter", "the second parameter", "the third parameter",
                "the fourth parameter", "the fifth parameter", "the sixth parameter", "the seventh parameter");
            for (int i = 0; i < ordinals.size(); i++) {
                if (lower.startsWith(ordinals.get(i))) {
                    return i + 1;
                }
            }
            return 0;
        }
        for (int i = 0; i < PARAMETER_ORDINALS.size(); i++) {
            if (text.startsWith(PARAMETER_ORDINALS.get(i))) {
                // «Четвертый» и «Четвёртый» — один и тот же номер.
                return i >= 4 ? i : i + 1;
            }
        }
        return 0;
    }


    /**
     * Текст статьи: абзацы и пункты списков по всей странице.
     * <p>
     * Текст прерывается блоками «Пример:» / «Результат запроса:», а после них
     * продолжается — причём продолжение платформа кладёт внутрь того же
     * {@code <BLOCKQUOTE>}, что и таблицу результата. Заканчивается статья
     * нередко списком {@code <UL>} («Данную функцию НЕЛЬЗЯ использовать в
     * следующих случаях:» на странице АВТОНОМЕРЗАПИСИ). Поэтому берём всё,
     * кроме кода, ячеек таблиц и блока «см. также:» — он уезжает
     * в {@link #seeAlso(Document)}.
     */
    private static String description(Document document) {
        var sb = new StringBuilder();
        var body = document.body();
        if (body == null) {
            return "";
        }
        for (var element : body.select("p, li")) {
            if (isInsideCodeOrTable(element)) {
                continue;
            }
            var text = normalizeSpaces(element.text());
            if (text.isEmpty() || isSeeAlso(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            if ("li".equalsIgnoreCase(element.tagName())) {
                sb.append("— ");
            }
            sb.append(text);
        }
        return sb.toString();
    }

    /**
     * Абзац внутри примера кода или ячейки таблицы результата — не текст
     * статьи. Код на этих страницах размечен шрифтом {@code Courier New},
     * данные результата — таблицей.
     */
    private static boolean isInsideCodeOrTable(Element element) {
        if (!element.select("table").isEmpty()) {
            return true;
        }
        for (var parent : element.parents()) {
            var tag = parent.tagName().toLowerCase(Locale.ROOT);
            if ("table".equals(tag)) {
                return true;
            }
        }
        return !element.select("font[face~=(?i)courier]").isEmpty();
    }

    /**
     * Примеры — содержимое блоков {@code <BLOCKQUOTE>}: в shquery туда
     * завёрнут код запроса, идущий после {@code <H4>Пример:</H4>}.
     */
    private static List<String> examples(Document document) {
        var result = new ArrayList<String>();
        var isExample = false;
        // h4 и blockquote в порядке документа: заголовок задаёт смысл
        // следующего за ним блока. «Результат запроса:» — это таблица с
        // выборкой, а не код, и в примеры она попадать не должна.
        for (var element : document.select("h4, blockquote")) {
            if (!"blockquote".equalsIgnoreCase(element.tagName())) {
                isExample = isExampleHeading(element.text());
                continue;
            }
            if (!isExample) {
                continue;
            }
            var text = codeText(element);
            if (!text.isEmpty()) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * Заголовок блока с кодом: «Пример:», «Примеры:», «Пример запроса:» и
     * их английские пары. Всё остальное («Результат запроса:», «Результат:»)
     * примером не считается.
     */
    private static boolean isExampleHeading(String heading) {
        var text = heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT);
        return text.startsWith("пример") || text.startsWith("example");
    }

    /**
     * Ссылки из блока «см. также:» — имена смежных статей. Блок платформа
     * кладёт то отдельным {@code <BLOCKQUOTE>}, то прямо в блок с примером
     * (страница ЕСТЬNULL), поэтому ищем его по тексту абзаца, а не по месту
     * в разметке.
     */
    private static List<String> seeAlso(Document document) {
        var result = new ArrayList<String>();
        for (var paragraph : document.select("p, li")) {
            if (!isSeeAlso(paragraph.text())) {
                continue;
            }
            for (var link : paragraph.select("a")) {
                var text = normalizeSpaces(link.text());
                if (!text.isEmpty() && !result.contains(text)) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    /** Абзац блока «см. также:» / «See also:». */
    private static boolean isSeeAlso(String text) {
        var lower = text == null ? "" : normalizeSpaces(text).toLowerCase(Locale.ROOT);
        return lower.startsWith("см. также") || lower.startsWith("см.также")
            || lower.startsWith("see also");
    }

    /** Неразрывные пробелы платформы — обычными. */
    private static String normalizeSpaces(String text) {
        return text.replace(' ', ' ').strip();
    }

    /**
     * Текст блока кода с сохранением переносов строк: в HBK строки запроса
     * разделены {@code <BR>}, который {@code text()} и {@code wholeText()}
     * молча съедают, склеивая многострочный запрос в одну строку.
     */
    private static String codeText(Element element) {
        var copy = element.clone();
        for (var br : copy.select("br")) {
            br.replaceWith(new TextNode("\n"));
        }
        // Код размечен шрифтом Courier New; если он в блоке есть, берём
        // только его — иначе к запросу прилипает абзац «см. также:», который
        // платформа кладёт в тот же <BLOCKQUOTE> (страница ЕСТЬNULL).
        var code = copy.select("font[face~=(?i)courier]");
        if (code.isEmpty()) {
            return normalizeSpaces(copy.wholeText());
        }
        var sb = new StringBuilder();
        for (var font : code) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(font.wholeText());
        }
        return normalizeSpaces(sb.toString());
    }
}
