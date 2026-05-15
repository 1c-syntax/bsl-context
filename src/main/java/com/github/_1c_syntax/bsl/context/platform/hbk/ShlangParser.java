package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextEnumValue;
import com.github._1c_syntax.bsl.context.api.ContextName;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordCategory;
import com.github._1c_syntax.bsl.context.api.LanguageKeywordSnippet;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnum;
import com.github._1c_syntax.bsl.context.platform.PlatformContextEnumValue;
import com.github._1c_syntax.bsl.context.platform.PlatformContextType;
import com.github._1c_syntax.bsl.context.platform.PlatformLanguageKeyword;
import com.github._1c_syntax.bsl.context.platform.primitive.PrimitivePlaceholderType;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * Парсер {@code shlang_*.hbk} — раздел синтакс-помощника «Встроенный язык».
 * <p>
 * FileStorage этого HBK содержит:
 * <ul>
 *   <li>HTML-страницы (без расширения) — описания примитивных типов
 *       и языковых конструкций;</li>
 *   <li>{@code .st}-файлы — сниппеты автодополнения с локализованными
 *       именами и шаблонами с плейсхолдерами {@code <?>};</li>
 *   <li>сводные страницы {@code Pragma}, {@code annotations},
 *       {@code Instructions} — содержат списки конкретных директив,
 *       аннотаций, препроцессорных инструкций.</li>
 * </ul>
 * Имена парных страниц совпадают с точностью до суффикса {@code .st}:
 * {@code def_BooleanTrue} (HTML) ↔ {@code def_BooleanTrue.st} (snippet).
 * <p>
 * Имя ru/en извлекается из заголовка
 * {@code <H1 class=V8SH_pagetitle>Истина (True)</H1>}.
 */
public final class ShlangParser {

    /**
     * Заголовок страницы СП. ru-часть — что угодно до открывающей скобки;
     * en-часть — только латиница, цифры и {@code &}/{@code _}-знаки
     * (так отсекаются «человеческие» русскоязычные пояснения в скобках,
     * например {@code [...] (обращение к свойствам объекта)} —
     * там скобки описывают операцию, а не локализуют имя).
     */
    private static final Pattern TITLE_BILINGUAL =
        Pattern.compile("^(.+?)\\s*\\(([A-Za-z0-9&_ ]+)\\)\\s*$");
    /**
     * Сопоставляет двуязычную пару директив/аннотаций. Поддерживаемые
     * варианты записи:
     * <ul>
     *   <li>{@code &НаКлиенте (&AtClient)} — Pragma;</li>
     *   <li>{@code &НаКлиенте/&AtClient} — Pragma короткая форма;</li>
     *   <li>{@code &Перед("Имя метода")/&Before("Имя метода")} — annotations
     *       с обязательным параметром в скобках.</li>
     * </ul>
     * Захватываются только идентификаторы (без префикса {@code &} и без
     * параметрической части).
     */
    private static final Pattern PRAGMA_OR_ANNOTATION_LINE =
        Pattern.compile(
            "(?:&amp;|&)([\\p{L}\\p{N}]+)(?:\\([^)]*\\))?\\s*(?:/(?:&amp;|&)?|\\(\\s*(?:&amp;|&))\\s*([\\p{L}\\p{N}]+)",
            Pattern.UNICODE_CHARACTER_CLASS);
    /** {@code <SPAN class=SourceCode>#Если (#If)</SPAN>} */
    private static final Pattern PREPROCESSOR_LINE =
        Pattern.compile("#([\\p{L}\\p{N}]+)\\s*\\(\\s*#([\\p{L}\\p{N}]+)\\s*\\)",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Map<String, LanguageKeywordCategory> SPECIAL_BASENAMES = Map.of(
        "def_BooleanTrue", LanguageKeywordCategory.LITERAL,
        "def_BooleanFalse", LanguageKeywordCategory.LITERAL,
        "def_Var", LanguageKeywordCategory.DECLARATION,
        "def_Proc", LanguageKeywordCategory.DECLARATION,
        "def_Func", LanguageKeywordCategory.DECLARATION
    );

    /**
     * Имена страниц-описаний примитивных типов в shlang. Эти страницы
     * превращаются в {@link com.github._1c_syntax.bsl.context.api.ContextKind#PRIMITIVE_TYPE},
     * остальные {@code def_*} — в LANGUAGE_KEYWORD.
     * <p>
     * <b>Почему белый список, а не эвристика по тегу/группе СП:</b>
     * иерархия «Примитивные типы» из дерева содержания живёт в
     * {@code PackBlock} (TableOfContent) {@code shlang_*.hbk}, а PackBlock
     * shlang упакован форматом, который наш текущий Inflater не открывает
     * ({@code DataFormatException}). FileStorage — плоский. Эвристики по
     * HTML-содержимому ({@code «Доступность:»}, XDTO-блок, отсутствие
     * {@code <?>}-плейсхолдера) не покрывают «голые» страницы вроде
     * {@code def_Null} / {@code def_Undefined}. Список фиксирован: 7
     * примитивов языка 1С 8.x, новые добавляются крайне редко
     * (последним появился {@code Тип}). Если когда-нибудь распарсим
     * PackBlock shlang — заменим на TableOfContent-driven подход.
     */
    private static final java.util.Set<String> PRIMITIVE_BASENAMES = java.util.Set.of(
        "def_String", "def_Number", "def_Date", "def_Boolean",
        "def_Null", "def_Undefined", "def_Type"
    );

    private ShlangParser() {
    }

    /**
     * Распарсить shlang FileStorage (windows-1251 ZIP, как и shcntx).
     * @param fileStorage сырой ZIP-блок из {@code shlang_ru.hbk}
     * @return языковые конструкции и примитивы платформы
     */
    public static List<Context> parse(byte[] fileStorage) {
        return parse(fileStorage, null);
    }

    /**
     * Распарсить ru-FileStorage с подмешиванием en-алиасов из парного
     * {@code shlang_root.hbk} FileStorage. en-FileStorage используется
     * только для подбора английских имён body-keyword'ов (тех, что
     * выделяются {@code <u>...</u>} тегами в теле родительской страницы):
     * сматчиваются по позиции тега в одинаковой странице.
     */
    public static List<Context> parse(byte[] ruFileStorage, byte[] enFileStorage) {
        var pages = readZip(ruFileStorage);
        var enPages = enFileStorage == null ? Map.<String, byte[]>of() : readZip(enFileStorage);
        return parsePages(pages, enPages);
    }

    static List<Context> parsePages(Map<String, byte[]> pages) {
        return parsePages(pages, Map.of());
    }

    static List<Context> parsePages(Map<String, byte[]> pages, Map<String, byte[]> enPages) {
        var result = new ArrayList<Context>();
        // Дедупликация per-category: «Если» как STATEMENT и «Если» как
        // PREPROCESSOR_INSTRUCTION — два разных keyword'а (один используется
        // в коде, второй — в препроцессоре). Делим, чтобы они не подменяли
        // друг друга. Внутри одной категории дубли не плодим.
        var publishedByCategory = new EnumMap<LanguageKeywordCategory, java.util.Set<String>>(
            LanguageKeywordCategory.class);
        for (var c : LanguageKeywordCategory.values()) {
            publishedByCategory.put(c, new java.util.HashSet<>());
        }

        for (var entry : pages.entrySet()) {
            var name = entry.getKey();
            if (!isLanguageItemPage(name)) {
                continue;
            }
            var html = decode(entry.getValue());
            if (html == null) {
                continue;
            }
            var page = parseSinglePage(name, html, pages);
            if (page != null) {
                result.add(page);
                if (page instanceof PlatformLanguageKeyword pageKw) {
                    publishedByCategory.get(pageKw.category()).add(
                        pageKw.name().getName().toLowerCase(Locale.ROOT));
                    // Body-keywords тащим только из STATEMENT и DECLARATION:
                    // у OPERATOR-страниц (root_New, root_Quest, root_brackets)
                    // <u>...</u>-теги обычно отмечают плейсхолдеры аргументов
                    // (например, {@code <Тип>}, {@code <ПараметрыКонструктора>}),
                    // а не дочерние keyword'ы.
                    if (pageKw.category() == LanguageKeywordCategory.STATEMENT
                        || pageKw.category() == LanguageKeywordCategory.DECLARATION) {
                        var enHtml = decode(enPages.get(name));
                        extractBodyControlElements(html, enHtml, pageKw,
                            publishedByCategory.get(pageKw.category()), result);
                    }
                }
            }
        }

        // Snippet-only записи — ключевые слова без HTML-страницы СП:
        // struct_While, struct_Raise, root_Exec, operator_await,
        // operator_set. Имя ru/en извлекается из самого .st-файла
        // («первое слово до пробела/`<?>`/`(`»).
        for (var entry : pages.entrySet()) {
            var name = entry.getKey();
            if (!name.endsWith(".st")) {
                continue;
            }
            var basename = name.substring(0, name.length() - 3);
            if (pages.containsKey(basename)) {
                continue; // обработали через HTML-страницу
            }
            if (!isLanguageItemPage(basename + ".__")) {
                // basename без .st должен подходить под фильтр префиксов.
                var fakeForFilter = basename;
                if (!fakeForFilter.startsWith("def_") && !fakeForFilter.startsWith("struct_")
                    && !fakeForFilter.startsWith("root_") && !fakeForFilter.startsWith("operator_")) {
                    continue;
                }
            }
            var snippet = readSnippetBilingual(entry.getValue());
            var ru = firstToken(snippet.ru());
            var en = firstToken(snippet.en());
            if (ru.isEmpty()) {
                continue;
            }
            var category = categoryFor(basename);
            if (!publishedByCategory.get(category).add(ru.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(PlatformLanguageKeyword.builder()
                .name(new ContextName(ru, en))
                .category(category)
                .description("")
                .snippet(snippet)
                .build());
        }

        // Pragma / annotations / Instructions — сводные страницы со списком
        // конкретных директив. Из HTML вытаскиваем перечень и публикуем
        // каждый пункт как отдельный LANGUAGE_KEYWORD.
        var pragma = pages.get("Pragma");
        if (pragma != null) {
            extractListed(decode(pragma), LanguageKeywordCategory.PRAGMA,
                PRAGMA_OR_ANNOTATION_LINE, publishedByCategory.get(LanguageKeywordCategory.PRAGMA), result);
        }
        var annotations = pages.get("annotations");
        if (annotations != null) {
            extractListed(decode(annotations), LanguageKeywordCategory.ANNOTATION,
                PRAGMA_OR_ANNOTATION_LINE, publishedByCategory.get(LanguageKeywordCategory.ANNOTATION), result);
        }
        var instructions = pages.get("Instructions");
        if (instructions != null) {
            extractListed(decode(instructions), LanguageKeywordCategory.PREPROCESSOR_INSTRUCTION,
                PREPROCESSOR_LINE,
                publishedByCategory.get(LanguageKeywordCategory.PREPROCESSOR_INSTRUCTION), result);
            // На той же странице есть секция «Логические операции»:
            // <SPAN class=SourceCode>И (AND)</SPAN> / ИЛИ (OR) / НЕ (NOT) —
            // публикуем их как OPERATOR.
            extractLogicalOperators(decode(instructions),
                publishedByCategory.get(LanguageKeywordCategory.OPERATOR), result);
        }

        return result;
    }

    /**
     * Страницы языковых конструкций имеют типовые префиксы. Всё, что не
     * подходит — служебные файлы (например {@code JSONconffilter},
     * {@code MainXBase}, {@code __categories__}) или сводные страницы,
     * которые обрабатываются отдельно.
     */
    private static boolean isLanguageItemPage(String name) {
        if (name.endsWith(".st") || name.startsWith("__")) {
            return false;
        }
        return name.startsWith("def_")
            || name.startsWith("struct_")
            || name.startsWith("root_")
            || name.startsWith("operator_");
    }

    private static Context parseSinglePage(String basename, String html, Map<String, byte[]> pages) {
        var doc = Jsoup.parse(html);
        var name = extractTitleName(doc);
        if (name == null) {
            return null;
        }
        var description = extractDescription(doc);

        if (PRIMITIVE_BASENAMES.contains(basename)) {
            // Примитивные типы из shlang — публикуем как PrimitivePlaceholderType,
            // kind=PRIMITIVE_TYPE, методов/свойств/событий нет, но description
            // тащим из СП (раньше хардкод-классы возвращали только имя).
            return new PrimitivePlaceholderType(name, description);
        }

        var category = categoryFor(basename);
        return PlatformLanguageKeyword.builder()
            .name(name)
            .category(category)
            .description(description)
            .snippet(readSnippetBilingual(pages.get(basename + ".st")))
            .build();
    }

    private static LanguageKeywordCategory categoryFor(String basename) {
        var special = SPECIAL_BASENAMES.get(basename);
        if (special != null) {
            return special;
        }
        if (basename.startsWith("struct_")) {
            return LanguageKeywordCategory.STATEMENT;
        }
        if (basename.startsWith("root_") || basename.startsWith("operator_")) {
            return LanguageKeywordCategory.OPERATOR;
        }
        // Прочие def_* без специального маппинга — как DECLARATION; это
        // обычно именованные сущности языка, не описанные в SPECIAL_BASENAMES.
        return LanguageKeywordCategory.DECLARATION;
    }

    /**
     * Из заголовка {@code <H1 class=V8SH_pagetitle>RU (EN)</H1>} достаёт ru-имя
     * и en-алиас. Если скобок с алиасом нет — возвращает {@code (ru, "")}.
     */
    static ContextName extractTitleName(org.jsoup.nodes.Document doc) {
        var h1 = doc.selectFirst("h1.V8SH_pagetitle, H1.V8SH_pagetitle");
        if (h1 == null) {
            return null;
        }
        var raw = h1.text().replace(' ', ' ').trim();
        var matcher = TITLE_BILINGUAL.matcher(raw);
        if (matcher.matches()) {
            return new ContextName(matcher.group(1).trim(), matcher.group(2).trim());
        }
        // Если скобки есть, но содержимое — не латинский алиас (например,
        // «[...] (обращение к свойствам объекта)»), отбрасываем хвост:
        // имя заканчивается перед открывающей скобкой.
        var paren = raw.indexOf('(');
        if (paren > 0) {
            return new ContextName(raw.substring(0, paren).trim(), "");
        }
        return new ContextName(raw, "");
    }

    /**
     * Извлекает первый абзац после маркера «Описание:». Если его нет —
     * берёт первый непустой абзац после заголовка.
     */
    static String extractDescription(org.jsoup.nodes.Document doc) {
        for (var p : doc.select("p, P")) {
            var text = p.text();
            int idx = text.indexOf("Описание:");
            if (idx >= 0) {
                var tail = text.substring(idx + "Описание:".length()).trim();
                if (!tail.isEmpty()) {
                    return tail;
                }
                // Описание может быть в следующем элементе через <BR>
                var next = p.nextElementSibling();
                if (next != null) {
                    return next.text().trim();
                }
            }
        }
        // Fallback: первый непустой абзац после <H1>.
        var h1 = doc.selectFirst("h1.V8SH_pagetitle, H1.V8SH_pagetitle");
        if (h1 != null) {
            for (Element sibling = h1.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
                var t = sibling.text().trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    /**
     * Парсит .st-файл (сериализованная конфиг-структура 1С) и достаёт обе
     * локализации шаблона. Структура файла:
     * <pre>{@code
     * {1,
     *  {2,
     *   {"",1,0,"",""},
     *   {0,{"ru",0,0,"","ШаблонRu"}},
     *   {0,{"en",0,0,"","ШаблонEn"}}
     *  }
     * }
     * }</pre>
     * Если файла нет — {@link LanguageKeywordSnippet#EMPTY}.
     */
    static LanguageKeywordSnippet readSnippetBilingual(byte[] data) {
        if (data == null) {
            return LanguageKeywordSnippet.EMPTY;
        }
        var ru = readSnippet(data, "ru");
        var en = readSnippet(data, "en");
        if (ru.isEmpty() && en.isEmpty()) {
            return LanguageKeywordSnippet.EMPTY;
        }
        return new LanguageKeywordSnippet(ru, en);
    }

    static String readSnippet(byte[] data, String lang) {
        if (data == null) {
            return "";
        }
        var text = decode(data);
        if (text == null) {
            return "";
        }
        // Ищем `"<lang>",0,0,"","..."` — последнее поле и есть шаблон.
        var marker = "\"" + lang + "\"";
        var langIdx = text.indexOf(marker);
        if (langIdx < 0) {
            return "";
        }
        // После маркера идут 4 аргумента: 0, 0, "", "<TEMPLATE>"
        var rest = text.substring(langIdx + marker.length());
        var lastQuoteOpen = -1;
        var commas = 0;
        for (int i = 0; i < rest.length(); i++) {
            var c = rest.charAt(i);
            if (c == ',') commas++;
            if (commas == 4 && c == '"') {
                lastQuoteOpen = i + 1;
                break;
            }
        }
        if (lastQuoteOpen < 0) {
            return "";
        }
        var lastQuoteClose = rest.indexOf('"', lastQuoteOpen);
        // Snippet может содержать переводы строк — простая " как закрывающая работает,
        // т.к. внутри snippet не ожидаются собственные кавычки (1С использует своё экранирование).
        if (lastQuoteClose < 0) {
            return "";
        }
        return rest.substring(lastQuoteOpen, lastQuoteClose);
    }

    /**
     * Извлекает первый «токен» из шаблона: всё до первого пробела,
     * перевода строки, открывающей скобки, точки с запятой или
     * плейсхолдера {@code <?>}. Используется для snippet-only записей
     * без HTML-страницы СП — имя ключевого слова.
     */
    static String firstToken(String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return "";
        }
        int i = 0;
        // Пропускаем ведущие пробелы/переводы строк
        while (i < snippet.length() && Character.isWhitespace(snippet.charAt(i))) {
            i++;
        }
        int start = i;
        while (i < snippet.length()) {
            var c = snippet.charAt(i);
            if (Character.isWhitespace(c) || c == '<' || c == '(' || c == ';' || c == '=') {
                break;
            }
            i++;
        }
        return snippet.substring(start, i);
    }

    /**
     * На странице конструкции (например, {@code struct_IfThenElif}) в теле
     * упоминаются «дочерние» ключевые слова — части синтаксиса родителя:
     * <pre>{@code <strong class="ControlElement">Тогда</strong>}</pre>
     * Они и есть искомые keyword'ы тела. Категория наследуется у родителя
     * (если родитель — STATEMENT, дети тоже STATEMENT; для def_Proc/def_Func
     * — DECLARATION).
     */
    /**
     * Body-keywords конструкции выделяются на странице тегами
     * {@code <strong class="ControlElement">XXX</strong>} и/или
     * {@code <u>XXX</u>}. Берём всё что выглядит как одиночное русское
     * слово (буквы Unicode, без пробелов/тире).
     * <p>
     * Если передан {@code enHtml} — параллельно вытаскиваем те же
     * элементы с en-страницы и сматчиваем по позиции (в синхронных по
     * структуре страницах ru/en порядок ControlElement/{@code <u>} тегов
     * совпадает).
     */
    private static void extractBodyControlElements(String html, String enHtml,
                                                   PlatformLanguageKeyword parent,
                                                   java.util.Set<String> publishedNames,
                                                   List<Context> sink) {
        var ruTokens = collectBodyTokens(html, parent.name().getName());
        var enTokens = enHtml == null ? List.<String>of()
            : collectBodyTokens(enHtml, parent.name().getAlias());
        for (int i = 0; i < ruTokens.size(); i++) {
            var ru = ruTokens.get(i);
            if (!publishedNames.add(ru.toLowerCase(Locale.ROOT))) {
                continue;
            }
            var en = i < enTokens.size() ? enTokens.get(i) : "";
            sink.add(PlatformLanguageKeyword.builder()
                .name(new ContextName(ru, en))
                .category(parent.category())
                .description("Часть конструкции «" + parent.name().getName() + "»")
                .snippet(LanguageKeywordSnippet.EMPTY)
                .build());
        }
    }

    private static List<String> collectBodyTokens(String html, String parentName) {
        var doc = Jsoup.parse(html);
        var single = Pattern.compile("^\\p{L}[\\p{L}\\p{N}]*$", Pattern.UNICODE_CHARACTER_CLASS);
        var selectors = "strong.ControlElement, STRONG.ControlElement, u, U";
        var seen = new java.util.LinkedHashSet<String>();
        for (var el : doc.select(selectors)) {
            var raw = el.text().trim();
            if (raw.isEmpty() || !single.matcher(raw).matches()) {
                continue;
            }
            if (parentName != null && raw.equalsIgnoreCase(parentName)) {
                continue;
            }
            seen.add(raw);
        }
        return new ArrayList<>(seen);
    }

    /**
     * На странице {@code Instructions} после списка #-инструкций препроцессора
     * идут «логические операции» — {@code И (AND)}, {@code ИЛИ (OR)},
     * {@code НЕ (NOT)}. Они оформлены как {@code <SPAN class=SourceCode>}
     * без префикса {@code #}.
     */
    private static void extractLogicalOperators(String html, java.util.Set<String> publishedNames,
                                                List<Context> sink) {
        if (html == null) {
            return;
        }
        var doc = Jsoup.parse(html);
        var pattern = Pattern.compile(
            "^\\s*([\\p{Lu}\\p{Lt}\\p{Lo}]+)\\s*\\(\\s*([A-Z]+)\\s*\\)\\s*$",
            Pattern.UNICODE_CHARACTER_CLASS);
        for (var span : doc.select("span.SourceCode, SPAN.SourceCode")) {
            var text = span.text().trim();
            if (text.startsWith("#")) {
                continue;
            }
            var m = pattern.matcher(text);
            if (!m.matches()) {
                continue;
            }
            var ru = m.group(1);
            var en = m.group(2);
            if (!publishedNames.add(ru.toLowerCase(Locale.ROOT))) {
                continue;
            }
            sink.add(PlatformLanguageKeyword.builder()
                .name(new ContextName(ru, en))
                .category(LanguageKeywordCategory.OPERATOR)
                .description("Логическая операция")
                .snippet(LanguageKeywordSnippet.EMPTY)
                .build());
        }
    }

    /**
     * Из сводной страницы (Pragma/annotations/Instructions) выдирает все
     * вхождения двуязычных пар «ru (en)» и публикует их как отдельные
     * keyword'ы заданной категории. Дубликаты по имени отбрасываются.
     */
    private static void extractListed(String html, LanguageKeywordCategory category,
                                      Pattern itemPattern, java.util.Set<String> publishedNames,
                                      List<Context> sink) {
        if (html == null) {
            return;
        }
        // Берём text+innerHtml — Jsoup упрощает разметку, остаются &amp; в тексте.
        var doc = Jsoup.parse(html);
        var description = extractDescription(doc);

        // Каждый элемент списка / span — отдельная инструкция.
        for (var node : doc.select("li, span, strong, p, P, LI, SPAN, STRONG")) {
            var text = node.text();
            var matcher = itemPattern.matcher(text);
            while (matcher.find()) {
                var ru = matcher.group(1);
                var en = matcher.group(2);
                if (ru.isEmpty() || !publishedNames.add(ru.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                sink.add(PlatformLanguageKeyword.builder()
                    .name(new ContextName(ru, en))
                    .category(category)
                    .description(description)
                    .snippet(LanguageKeywordSnippet.EMPTY)
                    .build());
            }
        }
    }

    /**
     * Мануальный обход последовательности local file headers.
     * <p>
     * FileStorage из HbkContainerExtractor — это «голая» последовательность
     * ZIP local file records без central directory и end-of-CD trailer'а.
     * Поэтому {@link java.util.zip.ZipFile} не открывается («zip END header
     * not found»). А у части записей в {@code shlang_*.hbk} в local header
     * compressed/uncompressed size расходится с фактическим, поэтому
     * {@link java.util.zip.ZipInputStream} падает с {@code invalid entry
     * size}. Простейшее устойчивое решение — раскодировать каждый record
     * вручную через {@link java.util.zip.Inflater}, опираясь на длину
     * файла и сигнатуру {@code 0x04034b50}.
     */
    private static Map<String, byte[]> readZip(byte[] data) {
        var pages = new HashMap<String, byte[]>();
        var charset = Charset.forName("windows-1251");
        var pos = 0;
        while (pos + 30 <= data.length) {
            var sig = readU32LE(data, pos);
            if (sig != 0x04034b50) {
                // Конец последовательности (например, central directory или мусорный хвост).
                break;
            }
            var gpFlag = readU16LE(data, pos + 6);
            var method = readU16LE(data, pos + 8);
            var compressedSize = (int) readU32LE(data, pos + 18);
            var uncompressedSize = (int) readU32LE(data, pos + 22);
            var nameLen = readU16LE(data, pos + 26);
            var extraLen = readU16LE(data, pos + 28);

            var name = new String(data, pos + 30, nameLen, charset);
            var dataStart = pos + 30 + nameLen + extraLen;

            // Если установлен bit 3 GP-флага — sizes лежат в data descriptor
            // после данных (формат: optional sig 0x08074b50, CRC, csize, usize).
            // В обнаруженных shlang HBK эта ветка не встречалась, для надёжности
            // оставляем fallback ниже.
            byte[] uncompressed;
            if ((gpFlag & 0x08) != 0 && compressedSize == 0) {
                throw new RuntimeException(
                    "Streaming data descriptor in shlang FileStorage is not supported (entry " + name + ")");
            }
            if (method == 0) {
                uncompressed = java.util.Arrays.copyOfRange(data, dataStart, dataStart + compressedSize);
            } else if (method == 8) {
                uncompressed = inflate(data, dataStart, compressedSize, Math.max(uncompressedSize, 256));
            } else {
                throw new RuntimeException(
                    "Unsupported compression method " + method + " in shlang FileStorage (entry " + name + ")");
            }
            pages.put(name, uncompressed);
            pos = dataStart + compressedSize;
        }
        return pages;
    }

    private static byte[] inflate(byte[] src, int offset, int length, int hintCapacity) {
        var inflater = new java.util.zip.Inflater(true); // raw deflate (no zlib wrapper)
        try {
            inflater.setInput(src, offset, length);
            var out = new java.io.ByteArrayOutputStream(hintCapacity);
            var buffer = new byte[8 * 1024];
            while (!inflater.finished() && !inflater.needsInput()) {
                int n;
                try {
                    n = inflater.inflate(buffer);
                } catch (java.util.zip.DataFormatException e) {
                    throw new RuntimeException("Failed to inflate shlang entry", e);
                }
                if (n == 0) {
                    break;
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }

    private static int readU16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private static long readU32LE(byte[] data, int offset) {
        return (data[offset] & 0xFFL)
            | ((data[offset + 1] & 0xFFL) << 8)
            | ((data[offset + 2] & 0xFFL) << 16)
            | ((data[offset + 3] & 0xFFL) << 24);
    }

    private static String decode(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        // BOM check
        if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
            return new String(data, 3, data.length - 3, StandardCharsets.UTF_8);
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
