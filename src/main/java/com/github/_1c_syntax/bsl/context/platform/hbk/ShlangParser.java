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
                    // Парный en-HTML той же страницы — извлекаем en-description
                    // и кладём в PlatformLanguageKeyword.descriptionEn.
                    var enHtmlForPage = decode(enPages.get(name));
                    if (enHtmlForPage != null) {
                        var enDoc = Jsoup.parse(enHtmlForPage);
                        var enDesc = extractDescription(enDoc);
                        if (!enDesc.isEmpty()) {
                            pageKw.setDescriptionEn(enDesc);
                        }
                    }
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
            if (pages.containsKey(basename) || pages.containsKey(basename + ".html")) {
                continue; // обработали через HTML-страницу (с расширением или без)
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
            // EN-страница snippet-only keyword'ов чаще всего есть в en-storage
            // под именем «{basename}.html» (например, operator_await.html). RU
            // его обычно не содержит — поэтому ru description у нас пустой,
            // EN тащим из соседней en-страницы, если найдём.
            var enHtmlBytes = enPages.get(basename + ".html");
            if (enHtmlBytes == null) enHtmlBytes = enPages.get(basename);
            var enDesc = "";
            if (enHtmlBytes != null) {
                var enHtml = decode(enHtmlBytes);
                if (enHtml != null) {
                    enDesc = extractDescription(Jsoup.parse(enHtml));
                }
            }
            result.add(PlatformLanguageKeyword.builder()
                .name(new ContextName(ru, en))
                .category(category)
                .description("")
                .descriptionEn(enDesc)
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
        // Часть страниц в FileStorage имеет суффикс .html (operator_await.html),
        // у других страниц расширения нет (def_Func, def_Proc). Для lookup'а
        // парного .st-сниппета и для categoryFor() используем basename без .html.
        var baseKey = basename.endsWith(".html") ? basename.substring(0, basename.length() - 5) : basename;

        if (PRIMITIVE_BASENAMES.contains(baseKey)) {
            // Примитивные типы из shlang — публикуем как PrimitivePlaceholderType,
            // kind=PRIMITIVE_TYPE, методов/свойств/событий нет, но description
            // тащим из СП (раньше хардкод-классы возвращали только имя).
            return new PrimitivePlaceholderType(name, description);
        }

        var category = categoryFor(baseKey);
        return PlatformLanguageKeyword.builder()
            .name(name)
            .category(category)
            .description(description)
            .snippet(readSnippetBilingual(pages.get(baseKey + ".st")))
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
            var ru = stripCategoryPrefix(matcher.group(1).trim());
            var en = stripCategoryPrefix(matcher.group(2).trim());
            return new ContextName(ru, en);
        }
        // Если скобки есть, но содержимое — не латинский алиас (например,
        // «[...] (обращение к свойствам объекта)»), отбрасываем хвост:
        // имя заканчивается перед открывающей скобкой.
        var paren = raw.indexOf('(');
        if (paren > 0) {
            return new ContextName(stripCategoryPrefix(raw.substring(0, paren).trim()), "");
        }
        return new ContextName(stripCategoryPrefix(raw), "");
    }

    /**
     * На страницах вида {@code operator_*.html} заголовок выглядит как
     * «Оператор Ждать (Await)»/«Statement If…» — слова «Оператор», «Operator»,
     * «Statement» — это категория секции СП, а не часть имени keyword'а.
     * Имя самого ключевого слова — {@code Ждать}/{@code Await}. Также
     * совпадает с тем, что выдаётся из snippet-only flow (где парсится
     * {@code .st}-файл), поэтому записи дедуплицируются.
     */
    private static final java.util.List<String> CATEGORY_PREFIXES = java.util.List.of(
        "Оператор ", "Operator ",
        "Statement ", "Конструкция "
    );

    private static String stripCategoryPrefix(String s) {
        for (var p : CATEGORY_PREFIXES) {
            if (s.startsWith(p)) return s.substring(p.length()).trim();
        }
        return s;
    }

    /**
     * Извлекает первый абзац после маркера «Описание:». Если его нет —
     * берёт первый непустой абзац после заголовка.
     */
    static String extractDescription(org.jsoup.nodes.Document doc) {
        for (var p : doc.select("p, P")) {
            var text = p.text();
            // На разных страницах маркер с двоеточием («Описание:», «Description:»)
            // либо без («Description» — у en-варианта def_Func из шапки <b>Description<br></b>).
            // Матчим строго ПО НАЧАЛУ параграфа, чтобы не цеплять «Description» как
            // обычное слово в середине описательного текста.
            int markerLen = -1;
            var lc = text.toLowerCase(Locale.ROOT);
            if (lc.startsWith("описание:")) markerLen = "Описание:".length();
            else if (lc.startsWith("description:")) markerLen = "Description:".length();
            else if (lc.startsWith("описание ") || lc.equals("описание")) markerLen = "Описание".length();
            else if (lc.startsWith("description ") || lc.equals("description")) markerLen = "Description".length();
            if (markerLen >= 0) {
                var tail = text.substring(markerLen).trim();
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
        // Fallback: первый непустой абзац после <H1>. Пропускаем содержимое,
        // совпадающее с заголовком (на некоторых en-страницах, например
        // def_Func, первый <p> после H1 содержит то же слово «Function» —
        // это title, а не описание).
        var h1 = doc.selectFirst("h1.V8SH_pagetitle, H1.V8SH_pagetitle");
        if (h1 != null) {
            var h1Text = h1.text().trim();
            // Берём ru/en часть отдельно — заголовок вида «Функция (Function)».
            var h1Tokens = new java.util.HashSet<String>();
            h1Tokens.add(h1Text.toLowerCase(Locale.ROOT));
            var matcher = TITLE_BILINGUAL.matcher(h1Text);
            if (matcher.matches()) {
                h1Tokens.add(matcher.group(1).trim().toLowerCase(Locale.ROOT));
                h1Tokens.add(matcher.group(2).trim().toLowerCase(Locale.ROOT));
            }
            for (Element sibling = h1.nextElementSibling(); sibling != null; sibling = sibling.nextElementSibling()) {
                var t = sibling.text().trim();
                if (t.isEmpty()) continue;
                if (h1Tokens.contains(t.toLowerCase(Locale.ROOT))) continue;
                // Шапки СП-секций — не описание. Если на странице только они
                // (как у en-варианта def_Func), пусть descriptionEn останется
                // пустым — LS отрисует ru-вариант через BilingualString-фоллбэк.
                if (isSectionHeader(t)) continue;
                // Однословные/короткие куски — это не описание, а атрибут
                // синтаксиса (например, у en-страницы def_Func между шапками
                // встречается просто «Async»). Описание — всегда фраза.
                if (t.length() < 20 || !t.contains(" ")) continue;
                return t;
            }
        }
        return "";
    }

    /**
     * Шапки секций СП-страницы (двуязычные). Если параграф ровно равен одной
     * из них — это не описание, а заголовок следующего блока, который
     * нужно пропустить при поиске первого осмысленного абзаца после H1.
     * Сравнение регистронезависимое, по trimmed-тексту.
     */
    private static final java.util.Set<String> SECTION_HEADERS = java.util.Set.of(
        "синтаксис:", "syntax:",
        "параметры:", "parameters:",
        "возвращаемое значение:", "returned value:", "return value:",
        "пример:", "example:",
        "примечание:", "note:", "notes:",
        "см. также:", "see also:",
        "доступность:", "availability:",
        "описание варианта метода:", "method variant description:",
        "вариант синтаксиса:", "syntax variant:"
    );

    private static boolean isSectionHeader(String text) {
        var lc = text.toLowerCase(Locale.ROOT).trim();
        if (SECTION_HEADERS.contains(lc)) return true;
        for (var h : SECTION_HEADERS) {
            if (lc.startsWith(h)) return true;
        }
        return false;
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
        var ruPairs = collectBodyDescriptions(html, parent.name().getName());
        var enPairs = enHtml == null ? List.<TokenDescription>of()
            : collectBodyDescriptions(enHtml, parent.name().getAlias());
        for (int i = 0; i < ruPairs.size(); i++) {
            var rp = ruPairs.get(i);
            var ru = rp.token();
            var ep = i < enPairs.size() ? enPairs.get(i) : null;
            var en = ep != null ? ep.token() : "";
            var parentRu = parent.name().getName();
            var parentEn = parent.name().getAlias();
            // Если keyword уже опубликован на другой родительской странице —
            // обогащаем существующую запись описанием под ключ {@code parentRu}.
            // Иначе создаём новый и кладём первое описание под этот же ключ.
            PlatformLanguageKeyword target = null;
            if (!publishedNames.add(ru.toLowerCase(Locale.ROOT))) {
                for (var c : sink) {
                    if (c instanceof PlatformLanguageKeyword pk
                        && pk.category() == parent.category()
                        && pk.name().getName().equalsIgnoreCase(ru)) {
                        target = pk;
                        break;
                    }
                }
            }
            if (target == null) {
                // Generic-описание остаётся для случая, когда контекст вызова
                // неизвестен: LS пытается выбрать конкретное по parent'у,
                // если не находит — показывает это.
                var genericRu = parentRu == null || parentRu.isBlank()
                    ? "" : "Часть конструкции «" + parentRu + "»";
                var genericEn = parentEn == null || parentEn.isBlank()
                    ? "" : "Part of \"" + parentEn + "\" construct";
                target = PlatformLanguageKeyword.builder()
                    .name(new ContextName(ru, en))
                    .category(parent.category())
                    .description(genericRu)
                    .descriptionEn(genericEn)
                    .snippet(LanguageKeywordSnippet.EMPTY)
                    .build();
                sink.add(target);
            }
            // Per-parent описания.
            if (!rp.description().isEmpty()) {
                target.putDescriptionForParent(parentRu, rp.description());
            }
            if (ep != null && !ep.description().isEmpty()) {
                target.putDescriptionForParentEn(parentRu, ep.description());
            }
        }
    }

    /**
     * Извлекает body-keyword'ы родительской страницы вместе с их описаниями.
     * <p>
     * На странице вида {@code def_Func} body-keyword'ы перечислены как
     * {@code <u>X</u>} с описанием либо в том же {@code <p>} после
     * {@code </u>} через {@code <br>}, либо в следующем {@code <p>}.
     * Плейсхолдеры синтаксиса ({@code <Имя_функции>}, {@code <Параметр1>…})
     * и комментарии-описания секций ({@code // Объявления…}) отбрасываются —
     * это не самостоятельные keyword'ы.
     */
    private static List<TokenDescription> collectBodyDescriptions(String html, String parentName) {
        var doc = Jsoup.parse(html);
        var single = Pattern.compile("^\\p{L}[\\p{L}\\p{N}]*$", Pattern.UNICODE_CHARACTER_CLASS);
        var seen = new java.util.LinkedHashMap<String, TokenDescription>();
        // На def_Func/def_Proc body-keyword'ы выделены тегом <u>X</u> в секции
        // «Параметры:» — каждый в своём <p>, под ним описание. <strong class="ControlElement">
        // присутствует и в шапке "Syntax:" в одном <p> с несколькими тегами —
        // их брать не нужно (там нет описаний). Поэтому ограничиваемся <u>.
        // Для struct_*-страниц (Если/Попытка/Цикл) body-keywords тоже выделены
        // <u>, так что одного селектора достаточно.
        for (var tag : doc.select("u, U")) {
            var rawText = tag.text().trim().replace(' ', ' ');
            if (rawText.isEmpty()) {
                continue;
            }
            // Если в теге несколько токенов (например, «Возврат <Возвращаемое значение>»
            // или «Return <Return value>»), keyword — это первое слово; остальное —
            // placeholder параметра. Берём первый Cyrillic/Latin токен.
            boolean wasSingle = single.matcher(rawText).matches();
            String raw = wasSingle ? rawText : extractLeadingWord(rawText);
            if (raw == null || raw.isEmpty() || !single.matcher(raw).matches()) {
                continue;
            }
            // Фильтр «не дублировать родителя» применяем только если в теге
            // ОДНО слово — равное имени родителя. Случай вроде
            // {@code <u><Procedure name></u>}, где после extractLeadingWord
            // получается "Procedure", совпадающий с parentName en="Procedure", —
            // это синтаксический placeholder, не сам родитель; такой токен
            // создаёт keyword «Имя/Procedure», участвующий в общем матчинге
            // ru/en по позиции (иначе indices ru/en расходятся и Возврат
            // получает en-alias соседнего токена).
            if (wasSingle && parentName != null && raw.equalsIgnoreCase(parentName)) {
                continue;
            }
            // Найти ближайший <p>-предок и в нём текст после tag-а
            String desc = "";
            org.jsoup.nodes.Element p = tag.parent();
            while (p != null && !"p".equalsIgnoreCase(p.tagName())) {
                p = p.parent();
            }
            if (p != null) {
                var all = p.text();
                var pos = all.indexOf(raw);
                if (pos >= 0) {
                    desc = all.substring(pos + raw.length()).trim();
                }
                if (desc.isEmpty()) {
                    var next = p.nextElementSibling();
                    if (next != null && "p".equalsIgnoreCase(next.tagName())) {
                        desc = next.text().trim();
                    }
                }
                // Очищаем leading placeholder вида «<Возвращаемое значение>» или
                // «=<DefaultValue>» — это синтаксический «хвост» token-а, а не
                // описание. После trim'а описание начинается с реального текста.
                desc = stripLeadingPlaceholders(desc);
            }
            // Если уже встречали — оставляем НЕ-ПУСТОЕ описание (первый
            // встреченный тег может быть в шапке без описания, реальное
            // описание идёт ниже в секции «Параметры:»).
            var existing = seen.get(raw);
            if (existing == null || existing.description().isEmpty()) {
                seen.put(raw, new TokenDescription(raw, desc));
            }
        }
        return new java.util.ArrayList<>(seen.values());
    }

    private record TokenDescription(String token, String description) {}

    /**
     * Возвращает первое слово (последовательность Unicode-букв/цифр) до пробела,
     * угловой скобки или другого разделителя. Используется для multi-word
     * содержимого {@code <u>}-тегов вида {@code «Возврат <Возвращаемое значение>»} —
     * сам keyword здесь {@code «Возврат»}, остальное — синтаксический placeholder.
     */
    /**
     * Очищает в начале строки повторяющиеся placeholder-«хвосты» вида
     * {@code <Имя>} / {@code =<Имя>}, оставшиеся после извлечения keyword'а
     * из multi-word тега (например, после {@code <u>Возврат <Возвращаемое
     * значение></u>} описание начиналось с {@code «<Возвращаемое значение> …»}).
     */
    private static String stripLeadingPlaceholders(String s) {
        var result = s.trim();
        while (result.startsWith("=<") || result.startsWith("<")) {
            int gt = result.indexOf('>');
            if (gt < 0) break;
            result = result.substring(gt + 1).trim();
        }
        return result;
    }

    private static String extractLeadingWord(String text) {
        int i = 0;
        while (i < text.length() && !Character.isLetter(text.charAt(i))) i++;
        int start = i;
        while (i < text.length()) {
            var c = text.charAt(i);
            if (!Character.isLetterOrDigit(c)) break;
            i++;
        }
        return i > start ? text.substring(start, i) : null;
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
                .descriptionEn("Logical operation")
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
     * последовательный {@link java.util.zip.ZipInputStream} (и обычный
     * pos+=csize обход) после такой записи попадает в середину следующего
     * header'а и теряет хвост FileStorage. Например, в shlang_ru.hbk
     * {@code operator_await.html} (страница «Ждать») лежит на смещении 58516
     * — линейный парсер не доходит до неё и описание оператора теряется.
     * <p>
     * Решение — сканировать data на сигнатуры {@code 0x04034b50}
     * (PK\x03\x04) и пытаться прочесть каждый встреченный local header
     * независимо. Записи с некорректным header'ом отбрасываются, корректные
     * сохраняются. Этот brute-force подход устойчив к битым промежуточным
     * записям.
     */
    private static Map<String, byte[]> readZip(byte[] data) {
        var pages = new HashMap<String, byte[]>();
        var charset = Charset.forName("windows-1251");
        for (int pos = 0; pos + 30 <= data.length; pos++) {
            if (readU32LE(data, pos) != 0x04034b50L) {
                continue;
            }
            try {
                var nameLen = readU16LE(data, pos + 26);
                var extraLen = readU16LE(data, pos + 28);
                if (pos + 30 + nameLen + extraLen > data.length) continue;
                var name = new String(data, pos + 30, nameLen, charset);
                var compressedSize = (int) readU32LE(data, pos + 18);
                var uncompressedSize = (int) readU32LE(data, pos + 22);
                var method = readU16LE(data, pos + 8);
                var gpFlag = readU16LE(data, pos + 6);
                int dataStart = pos + 30 + nameLen + extraLen;
                if (dataStart + compressedSize > data.length) continue;
                if ((gpFlag & 0x08) != 0 && compressedSize == 0) continue; // streaming descriptor — пропускаем
                byte[] uncompressed;
                if (method == 0) {
                    uncompressed = java.util.Arrays.copyOfRange(data, dataStart, dataStart + compressedSize);
                } else if (method == 8) {
                    uncompressed = inflate(data, dataStart, compressedSize, Math.max(uncompressedSize, 256));
                } else {
                    continue;
                }
                // Не перезаписываем существующую запись (на случай ложного срабатывания
                // сигнатуры внутри сжатых данных — первая встреченная обычно корректная).
                pages.putIfAbsent(name, uncompressed);
            } catch (Exception ex) {
                // битый header или inflate — пропускаем, ищем следующую PK-сигнатуру
            }
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
