package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github._1c_syntax.bsl.context.api.Context;
import com.github._1c_syntax.bsl.context.api.ContextCollection;
import com.github._1c_syntax.bsl.context.api.ContextEnum;
import com.github._1c_syntax.bsl.context.platform.BilingualMerger;
import com.github.eightm.lib.DoubleLanguageString;
import com.github.eightm.lib.Page;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

  private final PageSource pageSource;

  /**
   * Индекс {@code нормализованный htmlPath → двуязычный заголовок страницы}.
   * Заполняется {@link HbkTreeParser} из дерева оглавления перед обходом и
   * используется для квалификации ссылок в секциях «Рекомендуется использовать»
   * и «См. также»: имя владельца члена извлекается из {@code href} ссылки
   * (текст {@code <a>} содержит только имя члена). Пустой индекс (например,
   * при прямом создании парсера в тестах) → ссылки остаются неквалифицированными.
   */
  private Map<String, DoubleLanguageString> pageIndex = Collections.emptyMap();

  /** Устанавливает индекс страниц для квалификации ссылок (см. {@link #pageIndex}). */
  void setPageIndex(Map<String, DoubleLanguageString> pageIndex) {
    this.pageIndex = pageIndex == null ? Collections.emptyMap() : pageIndex;
  }

  /** Маркеры member-страниц в пути htmlPath: член принадлежит типу-владельцу. */
  private static final String[] MEMBER_MARKERS = {"/methods/", "/properties/", "/events/", "/operators/"};
  /**
   * Распознаёт оба формата имени параметра в синтакс-помощнике:
   * <ul>
   *   <li>обычный {@code <Имя> (обязательный)} — group1 = «Имя», group2 = null, group3 = «обязательный»;</li>
   *   <li>вариадик {@code <Имя1>,...,<ИмяN> (необязательный)} — group1 = «Имя1», group2 = «ИмяN», group3 = «необязательный».</li>
   * </ul>
   */
  private final Pattern parameterNamePattern = Pattern.compile(
      "<([^>]+)>(?:,?\\.\\.\\.,?<([^>]+)>)?\\s*\\(([^)]+)\\)");
  private static final Pattern EVENT_PARAM_NAME_PATTERN = Pattern.compile("<([^>]+)>");
  private static final Pattern SINCE_VERSION_PATTERN = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?(?:\\.\\d+)?)");
  private static final Pattern DEFAULT_VALUE_PATTERN = Pattern.compile("Значение по умолчанию:\\n?\\s*([^.\\n]+?)\\.");

  /**
   * Маркеры обязательности параметра в {@code (...)} после имени.
   * ru: «Обязательный»/«Необязательный»; en: «required»/«optional».
   * Проверяется case-insensitive.
   */
  private static boolean isRequiredMarker(String marker) {
    if (marker == null) return false;
    var m = marker.trim();
    return m.equalsIgnoreCase("Обязательный") || m.equalsIgnoreCase("Required");
  }

  /** Содержит ли текст маркер «Тип:» (ru) или «Type:» (en) — case-sensitive по факту HBK. */
  private static boolean containsTypeMarker(String text) {
    return text != null && (text.contains("Тип:") || text.contains("Type:"));
  }

  /** Индекс первого вхождения маркера «Тип:»/«Type:»; -1 если нет. */
  private static int indexOfTypeMarker(String text) {
    if (text == null) return -1;
    int idx = text.indexOf("Тип:");
    if (idx >= 0) return idx;
    return text.indexOf("Type:");
  }

  /** Длина маркера «Тип:» или «Type:» на позиции idx в тексте (для substring(idx + len)). */
  private static int typeMarkerLength(String text, int idx) {
    return text.startsWith("Тип:", idx) ? "Тип:".length() : "Type:".length();
  }

  /** «Произвольный» (ru) / «Arbitrary» (en) — placeholder для composite-types. */
  private static boolean containsArbitraryType(String text) {
    return text != null && (text.contains("Произвольный") || text.contains("Arbitrary"));
  }

  private static final String ARBITRARY_TYPE_NAME = "Произвольный";

  /**
   * Маркер блока «Элементами коллекции являются объекты типа …» (ru) /
   * «Items of this collection are objects of … type» (en) в описании свойства.
   * Следующий после маркера {@code <a>} содержит имя типа-элемента коллекции,
   * на которую указывает свойство (per-property element-type, в отличие от
   * {@link ContextCollection#collectionElementTypes()}
   * на самом типе-коллекции).
   */
  private static boolean containsCollectionElementMarker(String text) {
    return text != null
      && (text.contains("Элементами коллекции являются объекты типа")
        || text.contains("Items of this collection are objects of"));
  }

  /**
   * Маркер главной страницы enum-«библиотеки» (БиблиотекаКартинок,
   * БиблиотекаСтилей, ЦветаСтиля, …): «Значения этого набора имеют тип X»
   * — задаёт общий тип всех значений набора (см.
   * {@link ContextEnum#valueType()}).
   * Следующий после маркера {@code <a>} содержит имя типа.
   * EN-вариант на синтакс-помощнике встречается формулировкой
   * «Values of this set have the type X».
   */
  private static boolean containsEnumValueTypeMarker(String text) {
    return text != null
      && (text.contains("Значения этого набора имеют тип")
        || text.contains("Values of this set have the type"));
  }

  /**
   * Создаёт парсер на источнике страниц (in-memory или файловая система).
   * Двуязычная поддержка реализуется внешним мерджером
   * ({@link BilingualMerger}),
   * парсящим ru и en HBK отдельно, поэтому парсер сам в курсе только
   * одного языка.
   */
  public HtmlParser(PageSource pageSource) {
    this.pageSource = pageSource;
  }

  /**
   * Конструктор для обратной совместимости — страницы лежат на файловой
   * системе по пути {@code pagesPath}. Используется тестами на
   * распакованных фикстурах.
   */
  public HtmlParser(Path pagesPath) {
    this(new PageSource.FileSystem(pagesPath));
  }

  /**
   * Возвращает текст узла до первого встретившегося {@code <br>} среди
   * прямых детей. Нужен для отсечения постфикса с XDTO-информацией
   * в секции «Доступность:», где после {@code <BR>} в той же {@code <p>}
   * идёт текст про XDTO, который иначе попадал бы в список доступностей.
   */
  private static String textBeforeFirstBr(Element element) {
    var sb = new StringBuilder();
    for (Node child : element.childNodes()) {
      if (child instanceof Element e && e.tag().getName().equalsIgnoreCase("br")) {
        break;
      }
      if (child instanceof TextNode t) {
        sb.append(t.text());
      } else if (child instanceof Element e) {
        sb.append(e.text());
      }
    }
    return sb.toString().trim();
  }

  /**
   * Разбирает «Сервер, толстый клиент, внешнее соединение.» в список
   * элементов без точки и пробелов по краям.
   */
  private static List<String> splitAvailabilities(String text) {
    return Arrays.stream(text.split(","))
        .map(String::trim)
        .map(s -> s.endsWith(".") ? s.substring(0, s.length() - 1) : s)
        .toList();
  }

  /**
   * Извлекает строку версии (например {@code 8.3.27}) из текста
   * вида «Доступен, начиная с версии 8.3.27.».
   */
  private static String parseSinceVersion(String text) {
    Matcher m = SINCE_VERSION_PATTERN.matcher(text);
    return m.find() ? m.group(1) : "";
  }

  /**
   * Снимает обрамляющие угловые скобки с имени параметра события вида
   * {@code <Отказ>}. Если скобок нет — возвращает исходный текст.
   */
  private static String stripAngleBrackets(String text) {
    Matcher m = EVENT_PARAM_NAME_PATTERN.matcher(text);
    return m.find() ? m.group(1).trim() : text.trim();
  }

  /**
   * Строит имя параметра по результату матча {@link #parameterNamePattern}.
   * Для вариадик-формы возвращает {@code Имя1,...,ИмяN}, для обычной — одно имя.
   */
  private static String buildParameterName(Matcher match) {
    var first = match.group(1).trim();
    var last = match.group(2);
    if (last == null || last.isBlank()) {
      return first;
    }
    return first + ",...," + last.trim();
  }

  /**
   * Имя-диапазон вида {@code Значение1-Значение10}: база + число, дефис,
   * (опц. база) + число. group1 = база1, group2 = число1, group3 = база2
   * (может быть пустой), group4 = число2.
   */
  private static final Pattern VARIADIC_RANGE_PATTERN =
      Pattern.compile("^(.+?)(\\d+)\\s*-\\s*(.*?)(\\d+)$");

  /**
   * Помечает вариадик-параметры флагом {@code variadic} и приводит их имя к
   * единственной базе (без номера/маркера), которую потребитель (LS) нумерует
   * по фактическим аргументам ({@code Значение} → {@code Значение1},
   * {@code Значение2}, …).
   * <p>
   * Синтакс-помощник кодирует вариадик тремя способами:
   * <ul>
   *   <li>явно {@code <Имя1>,...,<ИмяN>} ({@link #buildParameterName} даёт
   *       имя {@code Имя1,...,ИмяN}) — например {@code Макс}/{@code Мин};</li>
   *   <li>диапазоном в имени {@code <Значение1-Значение10>} — например
   *       {@code СтрШаблон};</li>
   *   <li>множественным именем последнего опционального параметра
   *       {@code <Значения>}/{@code <Values>} — конструкторы Структуры,
   *       ФиксированнойСтруктуры.</li>
   * </ul>
   */
  private static void applyVariadicMarkers(List<MethodSignatureParameterDescription> params) {
    if (params.isEmpty()) {
      return;
    }
    for (var p : params) {
      if (p.name == null) {
        continue;
      }
      var raw = p.name.trim();
      if (raw.contains(",...,") || VARIADIC_RANGE_PATTERN.matcher(raw).matches()) {
        p.variadic = true;
        p.name = variadicBase(raw);
      }
    }
    var last = params.get(params.size() - 1);
    if (!last.variadic && !last.isRequired && last.name != null
        && isVariadicValuesName(last.name)) {
      last.variadic = true;
      last.name = singularizeValuesName(last.name.trim());
    }
  }

  /**
   * База вариадик-имени — префикс до первой цифры, очищенный от хвостовых
   * не-буквенных символов. {@code Значение1,...,ЗначениеN} → {@code Значение},
   * {@code Значение1-Значение10} → {@code Значение},
   * {@code КоличествоЭлементов1,...,N} → {@code КоличествоЭлементов}.
   */
  private static String variadicBase(String name) {
    int i = 0;
    while (i < name.length() && !Character.isDigit(name.charAt(i))) {
      i++;
    }
    var base = (i > 0 ? name.substring(0, i) : name).trim();
    while (!base.isEmpty() && !Character.isLetter(base.charAt(base.length() - 1))) {
      base = base.substring(0, base.length() - 1);
    }
    return base.isEmpty() ? name : base;
  }

  /**
   * Множественное имя «значений» — платформенная конвенция для вариадик-хвоста
   * конструкторов Структуры/ФиксированнойСтруктуры.
   */
  private static boolean isVariadicValuesName(String name) {
    var n = name.trim();
    return n.equalsIgnoreCase("Значения") || n.equalsIgnoreCase("Values");
  }

  /** Сингуляризация известных множественных имён для нумерации вариадик-хвоста. */
  private static String singularizeValuesName(String name) {
    if (name.equalsIgnoreCase("Значения")) {
      return "Значение";
    }
    if (name.equalsIgnoreCase("Values")) {
      return "Value";
    }
    return name;
  }

  /**
   * Разбирает аккумулированную строку «Тип: …» из секции
   * «Возвращаемое значение:» на отдельные имена типов и добавляет их в
   * {@code returnValues}. Несколько типов разделяются запятой; внутри одного
   * имени запятых не бывает (точки в qualifiedName типа — это валидный
   * символ, а угловые скобки {@code <…>} — placeholder generic'а).
   * Хвостовые точки/пробелы у каждого имени убираются.
   * <p>
   * Очищает {@code typeLine} в конце.
   */
  private static void flushTypeLine(StringBuilder typeLine, List<String> returnValues) {
    if (typeLine.length() == 0) {
      return;
    }
    for (var part : typeLine.toString().split(",")) {
      var trimmed = part.trim();
      // Снимаем хвостовую точку (HBK обычно ставит её в конце последнего типа).
      // qualifiedName типа НИКОГДА не заканчивается на '.' — даже у generic'ов
      // имя выглядит как "СправочникСсылка.<Имя справочника>" (заканчивается
      // на '>'). Поэтому любая '.' на конце — это точка-разделитель из HBK
      // («Тип: X.»), а не часть имени.
      while (!trimmed.isEmpty() && trimmed.charAt(trimmed.length() - 1) == '.') {
        trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
      }
      if (!trimmed.isEmpty()) {
        returnValues.add(trimmed);
      }
    }
    typeLine.setLength(0);
  }

  /**
   * Ищет «Значение по умолчанию: X.» в описании параметра. Возвращает
   * пустую строку, если не найдено.
   */
  private static String parseDefaultValue(String description) {
    Matcher m = DEFAULT_VALUE_PATTERN.matcher(description);
    return m.find() ? m.group(1).trim() : "";
  }

  /**
   * Убирает из описания параметра вхождение «Значение по умолчанию: X.» —
   * этот блок выносится в отдельное поле {@code defaultValue}, чтобы
   * потребители не дублировали его в текстовом описании.
   * <p>
   * Регексп тот же, что у {@link #DEFAULT_VALUE_PATTERN}, но без захвата
   * группы значения — здесь нас интересует подстрока целиком (вместе с
   * префиксом и завершающей точкой) для замены на пустую строку.
   */
  private static String stripDefaultValueText(String description) {
    if (description == null || description.isBlank()) {
      return description == null ? "" : description;
    }
    return DEFAULT_VALUE_PATTERN.matcher(description).replaceAll("").trim();
  }

  /**
   * Определяет, относится ли текст версии к «Доступен, начиная с версии X»
   * (первая доступная версия члена). Прочее (например «Не рекомендуется
   * использовать, начиная с версии X», «Описание изменено в версии X»)
   * относится к другим полям.
   */
  private static boolean isSinceVersionText(String text) {
    return text.startsWith("Доступен");
  }

  /**
   * Определяет, что текст версии относится к «Не рекомендуется использовать,
   * начиная с версии X» — фиксирует версию депрекации.
   */
  private static boolean isDeprecatedSinceVersionText(String text) {
    return text.startsWith("Не рекомендуется");
  }

  /**
   * Извлекает имена «Рекомендуется использовать:» — обёрнутая в
   * {@code <div class="__DEPRECATED_SHOW_STYLE__"><ul>…<li><a>Имя</a></li>…}
   * подсказка, что использовать вместо устаревшего элемента. Возвращает
   * пустой список, если блока нет либо в нём нет {@code <a>}-ссылок.
   */
  private List<String> findRecommendedReplacements(Document document) {
    var blocks = document.select("div.__DEPRECATED_SHOW_STYLE__");
    if (blocks.isEmpty()) {
      return Collections.emptyList();
    }
    var result = new ArrayList<String>();
    for (var block : blocks) {
      for (var anchor : block.select("a")) {
        var text = anchor.text().trim();
        if (!text.isBlank()) {
          result.add(qualifyReference(anchor.attr("href"), text));
        }
      }
    }
    return result;
  }

  /**
   * Превращает {@code href} страницы синтакс-помощника в нормализованный путь
   * (как в {@code htmlPath} дерева), либо {@code null}, если ссылка не ведёт на
   * страницу СП (например, {@code "#"} или внешний URL).
   */
  private static String hrefToPath(String href) {
    if (href == null) {
      return null;
    }
    var marker = "SyntaxHelperContext/";
    var i = href.indexOf(marker);
    if (i < 0) {
      return null;
    }
    return PageSource.normalize(href.substring(i + marker.length()));
  }

  /** Индекс первого member-маркера ({@link #MEMBER_MARKERS}) в пути; {@code -1}, если нет. */
  private static int memberMarkerIndex(String path) {
    var min = -1;
    for (var marker : MEMBER_MARKERS) {
      var idx = path.indexOf(marker);
      if (idx >= 0 && (min < 0 || idx < min)) {
        min = idx;
      }
    }
    return min;
  }

  /**
   * Квалифицирует ссылку секций «Рекомендуется»/«См. также» в имя
   * {@code Владелец.Член}. Имя члена — текст ссылки {@code linkText} (он уже на
   * языке страницы), владелец берётся из {@link #pageIndex} по {@code href}.
   * Язык владельца выбирается по совпадению {@code linkText} с заголовком
   * member-страницы (по умолчанию ru). Если ссылка не ведёт на member-страницу
   * либо владелец не найден в индексе — возвращается исходный {@code linkText}
   * (без регрессии при пустом индексе).
   */
  private String qualifyReference(String href, String linkText) {
    var path = hrefToPath(href);
    if (path == null) {
      return linkText;
    }
    var markerIdx = memberMarkerIndex(path);
    if (markerIdx < 0) {
      return linkText;
    }
    var owner = pageIndex.get(path.substring(0, markerIdx) + ".html");
    if (owner == null) {
      return linkText;
    }
    if (isGlobalContext(owner)) {
      // У глобального контекста нет фактического владельца — метод/свойство
      // доступны по голому имени, префикс не нужен.
      return linkText;
    }
    var member = pageIndex.get(path);
    var useEn = member != null
        && linkText.equals(member.en())
        && !linkText.equals(member.ru());
    var ownerName = useEn ? owner.en() : owner.ru();
    if (ownerName == null || ownerName.isBlank()) {
      return linkText;
    }
    return ownerName + "." + linkText;
  }

  /** Владелец — глобальный контекст (en-заголовок {@code Global context}). */
  private static boolean isGlobalContext(DoubleLanguageString owner) {
    return "Global context".equals(owner.en()) || "Глобальный контекст".equals(owner.ru());
  }

  /**
   * Разбирает корневую страницу глобального контекста ({@code Global context.html}).
   * Её основное содержимое — навигационные списки чаптеров «Свойства:»,
   * «Методы:», «События приложения:» и т.д., которые мы вытаскиваем из
   * файлового дерева. С самой страницы нам нужна только версия первого
   * появления.
   */
  @SneakyThrows
  protected GlobalContextPageDescription parseGlobalContextPage(Page page) {
    var document = pageSource.parse(page.htmlPath());
    var result = new GlobalContextPageDescription();

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }
      if (node.attr("class").equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          result.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          result.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
      }
    }

    return result;
  }

  /**
   * Разбирает главную HTML-страницу типа / коллекции
   * (например, {@code .../catalog234/Map.html}) — «страничные» секции
   * («Описание:», «Примечание:», «Доступность:», «Пример:», «См. также:»,
   * version-info) плюс блок «Элементы коллекции:».
   * <p>
   * Навигационные чаптеры («Свойства:», «Методы:», «События:»,
   * «Конструкторы:», «Параметры формы:») — это оглавление членов, которое
   * мы берём из дерева HBK, поэтому в текст они не попадают.
   * <p>
   * Страница парсится ровно один раз: раньше на каждый тип приходилось
   * два независимых jsoup-parse (описание + элементы коллекции).
   */
  @SneakyThrows
  protected TypePageDescription parseTypePage(Page page) {
    var result = new TypePageDescription();
    if (page.htmlPath() == null || page.htmlPath().isEmpty()) {
      return result;
    }
    var document = pageSource.parse(page.htmlPath());
    parsePageSections(document, result);
    result.collectionInfo = parseCollectionInfo(document);
    return result;
  }

  /**
   * Разбирает «страничные» секции главной страницы контекста — общие для
   * типа, коллекции и перечисления. Разметка плоская: чаптер
   * {@code <p class="V8SH_chapter">} открывает секцию, следующий чаптер её
   * закрывает.
   * <p>
   * «Примечание:» трактуется так же, как на member-страницах: если
   * «Описание:» уже встречалось — это отдельная заметка ({@code notes}),
   * иначе — само описание.
   */
  private void parsePageSections(Document document, PageDescription sink) {
    var descriptionSection = false;
    var notesSection = false;
    var availabilitySection = false;
    var exampleSection = false;
    var seeAlsoSection = false;
    var descriptionSeen = false;
    var seeAlso = new SeeAlsoCollector();

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }

      if (isCodeTable(node)) {
        sink.description = stripTrailingExampleMarker(sink.description);
        addSnippet(sink.examples, node);
        continue;
      }
      extractNestedCodeTables(node, sink.examples);
      var isChapter = node.attr("class").equals("V8SH_chapter") && node instanceof Element;

      if (node.attr("class").equals("V8SH_pagetitle") && node instanceof Element n
          && sink.pageTitleRu.isEmpty()) {
        var parts = splitBilingualTitle(n.text());
        sink.pageTitleRu = parts[0];
        sink.pageTitleEn = parts[1];
      }

      if (descriptionSection && !isChapter) {
        sink.description = sink.description.concat(getDescription(node));
      }

      if (notesSection && !isChapter) {
        sink.notes = sink.notes.concat(getDescription(node));
      }

      if (availabilitySection && !isChapter) {
        var text = "";
        if (node instanceof TextNode n) {
          text = n.text();
        } else if (node instanceof Element n) {
          text = textBeforeFirstBr(n);
        }
        if (!text.isBlank()) {
          sink.availabilities = splitAvailabilities(text);
          availabilitySection = false;
        }
      }

      if (exampleSection && !isChapter && node instanceof Element n) {
        // Пример лежит в <TABLE> с <font>-разметкой; берём чистый текст.
        var snippet = n.wholeText().trim();
        if (!snippet.isBlank()) {
          sink.examples.add(snippet);
        }
      }

      if (seeAlsoSection && !isChapter
          && node instanceof Element n && "a".equalsIgnoreCase(n.tag().getName())) {
        seeAlso.accept(n);
      }

      if (node.attr("class").equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          sink.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          sink.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
      }

      if (isChapter && node instanceof Element n) {
        if (seeAlsoSection) {
          seeAlso.flush(sink.seeAlso);
        }
        var t = n.text();
        var tt = t.trim();
        var isDescriptionHeader = t.contains("Описание:") || t.contains("Description:");
        var isNote = t.contains("Примечание:");
        descriptionSeen = descriptionSeen || isDescriptionHeader;
        descriptionSection = isDescriptionHeader || (isNote && !descriptionSeen);
        notesSection = "Замечание:".equals(tt) || "Note:".equals(tt) || (isNote && descriptionSeen);
        availabilitySection = t.contains("Доступность:") || t.contains("Availability:");
        exampleSection = "Пример:".equals(tt) || "Example:".equals(tt);
        seeAlsoSection = "См. также:".equals(tt) || "See also:".equals(tt);
      }
    }
    seeAlso.flush(sink.seeAlso);
    sink.recommendedReplacements = findRecommendedReplacements(document);
  }

  /**
   * Разбирает заголовок страницы вида {@code «Имя (Name)»} на пару ru/en.
   * <p>
   * Скобки ищутся с конца с учётом вложенности: en-часть сама может содержать
   * скобки. Если хвост в скобках содержит кириллицу — это не перевод, а часть
   * самого имени ({@code «Расширение (устар.)»}), тогда весь заголовок
   * считается ru-именем. Generic-имена
   * ({@code «СправочникСсылка.<Имя справочника> (CatalogRef.<Catalog name>)»})
   * разбираются штатно.
   *
   * @return массив из двух элементов: ru-имя и en-имя (второе может быть пустым)
   */
  static String[] splitBilingualTitle(String title) {
    var t = title == null ? "" : title.trim();
    if (t.isEmpty() || !t.endsWith(")")) {
      return new String[]{t, ""};
    }
    var depth = 0;
    var open = -1;
    for (int i = t.length() - 1; i >= 0; i--) {
      var c = t.charAt(i);
      if (c == ')') {
        depth++;
      } else if (c == '(') {
        depth--;
        if (depth == 0) {
          open = i;
          break;
        }
      }
    }
    if (open <= 0) {
      return new String[]{t, ""};
    }
    var ru = t.substring(0, open).trim();
    var en = t.substring(open + 1, t.length() - 1).trim();
    if (ru.isEmpty() || en.isEmpty() || hasCyrillic(en)) {
      return new String[]{t, ""};
    }
    return new String[]{ru, en};
  }

  /** Хвостовой маркер «Пример:» перед блоком кода — в описании он лишний. */
  private static final Pattern TRAILING_EXAMPLE_MARKER =
    Pattern.compile("(Пример|Примеры|Example|Examples)\\s*:?\\s*$");

  /**
   * Горизонтальная линия перед футером страницы. Футер есть на всех 25503
   * страницах shcntx: {@code <HR>} и ссылка «Методическая информация» на
   * сайт 1С. Без обрыва она приклеивается к последней открытой секции —
   * например к синтаксису конструктора {@code Новый ГрафическаяСхема}.
   */
  private static boolean isFooterSeparator(Node node) {
    return node instanceof Element element && "hr".equalsIgnoreCase(element.tagName());
  }

  /**
   * Блок кода на странице СП — таблица с фоном {@code #f7f7f7}.
   * <p>
   * Обычно ей предшествует чаптер «Пример:», но на 32 страницах платформа
   * вписала слово «Пример:» прямо в текст описания через {@code <BR>}
   * (например, {@code ВнешнийОтчет.ОбработкаПроверкиЗаполнения}) — там
   * чаптера нет, и без этой проверки код целиком утекал в описание.
   */
  private static boolean isCodeTable(Node node) {
    return node instanceof Element element
      && "table".equalsIgnoreCase(element.tagName())
      && element.attr("bgcolor").toLowerCase(Locale.ROOT).contains("f7f7f7");
  }

  /**
   * Кладёт текст блока кода в примеры, если он непустой. Отступы в коде
   * платформа набирает неразрывными пробелами — их заменяем на обычные,
   * иначе {@code strip()} не срежет ведущий отступ, а сам код будет
   * непригоден для вставки в редактор.
   */
  private static void addSnippet(List<String> examples, Node node) {
    var snippet = ((Element) node).wholeText().replace('\u00A0', ' ').strip();
    if (!snippet.isBlank()) {
      examples.add(snippet);
    }
  }

  /**
   * Вынимает блоки кода, вложенные внутрь узла описания, и удаляет их из
   * разметки — вместе с текстовым маркером «Пример:», который стоит перед
   * ними. Без этого код целиком попадал бы в описание: на странице
   * {@code ВнешнийОтчет.ОбработкаПроверкиЗаполнения} платформа не закрыла
   * {@code <p>} перед таблицей, и та оказалась внутри абзаца.
   */
  private static void extractNestedCodeTables(Node node, List<String> examples) {
    if (!(node instanceof Element element)) {
      return;
    }
    for (var table : element.select("table")) {
      if (!isCodeTable(table)) {
        continue;
      }
      addSnippet(examples, table);
      stripExampleMarkerBefore(table);
      table.remove();
    }
  }

  /**
   * Подрезает маркер «Пример:», стоящий перед блоком кода: он относится
   * к коду, а не к описанию. Между маркером и таблицей могут быть
   * {@code <br>} и пробельные текстовые узлы.
   */
  private static void stripExampleMarkerBefore(Element table) {
    var previous = table.previousSibling();
    while (previous != null) {
      if (previous instanceof TextNode text) {
        var value = text.getWholeText();
        if (!value.isBlank()) {
          text.text(stripTrailingExampleMarker(value));
          return;
        }
      } else if (!(previous instanceof Element element) || !"br".equalsIgnoreCase(element.tagName())) {
        return;
      }
      previous = previous.previousSibling();
    }
  }

  /** Убирает из описания повисший маркер «Пример:». */
  private static String stripTrailingExampleMarker(String description) {
    return TRAILING_EXAMPLE_MARKER.matcher(description.stripTrailing()).replaceAll("").stripTrailing();
  }

  /** Есть ли в строке кириллические буквы. */
  static boolean hasCyrillic(String s) {
    if (s == null) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      var c = s.charAt(i);
      if (c >= 'А' && c <= 'я' || c == 'ё' || c == 'Ё') {
        return true;
      }
    }
    return false;
  }

  /**
   * Копит ссылки секции «См. также:». Ссылка на член кодируется парой
   * {@code <a>}: сначала тип-владелец, затем сам член; член уже несёт
   * владельца через {@code href}, поэтому предшествующую type-ссылку того же
   * владельца не дублируем. Standalone type-ссылку (без следующего члена)
   * отдаём как имя типа.
   */
  private final class SeeAlsoCollector {
    private final List<String> collected = new ArrayList<>();
    private String pendingText;
    private String pendingPath;

    private void accept(Element anchor) {
      var href = anchor.attr("href");
      var text = anchor.text().trim();
      if (text.isBlank()) {
        return;
      }
      var path = hrefToPath(href);
      var markerIdx = path == null ? -1 : memberMarkerIndex(path);
      if (markerIdx < 0) {
        // Ссылка на тип: держим до следующей — вдруг она на его член.
        flushPending();
        pendingText = text;
        pendingPath = path;
        return;
      }
      var ownerPath = path.substring(0, markerIdx) + ".html";
      if (ownerPath.equals(pendingPath)) {
        // pending — владелец этого члена, не дублируем.
        pendingText = null;
        pendingPath = null;
      } else {
        flushPending();
      }
      collected.add(qualifyReference(href, text));
    }

    /** Переносит накопленное в {@code sink}; коллектор снова пуст. */
    private void flush(List<String> sink) {
      flushPending();
      sink.addAll(collected);
      collected.clear();
    }

    private void flushPending() {
      if (pendingText != null) {
        collected.add(pendingText);
        pendingText = null;
        pendingPath = null;
      }
    }
  }

  /**
   * Информация о коллекции, извлечённая из блока «Элементы коллекции:»
   * на главной HTML-странице типа. Если блока нет — {@link #isEmpty()}
   * = {@code true} и список типов пустой.
   *
   * @param rawElementTypes        сырые ru-имена типов элементов (резолвятся
   *                               в {@link Context}
   *                               через {@code PlatformContextProvider})
   * @param supportsForEach        упомянут ли в блоке оператор «Для каждого»
   * @param forEachDescription     описание поведения обхода — «выбираются
   *                               значения / элементы / строки …»
   * @param supportsIndexAccess    упомянут ли оператор {@code [...]}
   * @param indexAccessDescription описание аргумента индексатора — «индекс
   *                               (нумерация с 0) / значение ключа …»
   */
  public record TypePageCollectionInfo(
    List<String> rawElementTypes,
    boolean supportsForEach,
    String forEachDescription,
    boolean supportsIndexAccess,
    String indexAccessDescription
  ) {
    public static final TypePageCollectionInfo EMPTY = new TypePageCollectionInfo(
      List.of(), false, "", false, "");

    public boolean isEmpty() {
      return rawElementTypes.isEmpty();
    }
  }

  /**
   * Разбирает блок «Элементы коллекции:» на главной HTML-странице типа.
   * Поддерживаются две разметки имени типа элемента:
   * <pre>{@code
   * <p class="V8SH_chapter">Элементы коллекции:</p>
   * <a href="...">КлючИЗначение</a><br>...
   * }</pre>
   * (тип — ссылкой, как у {@code Соответствие}) и
   * <pre>{@code
   * <p class="V8SH_chapter">Элементы коллекции:</p>
   * Произвольный<br>...
   * }</pre>
   * (тип — текстом до первого {@code <br>}, как у {@code Массив} —
   * «Произвольный» резолвится в синтетический {@code ArbitraryType}).
   * <p>
   * Дополнительно вытаскивает поддержку «Для каждого» и индексатора
   * {@code [...]} с их описаниями — это отдельные text-фрагменты между
   * {@code <br>} в том же блоке.
   * <p>
   * Если блока на странице нет — {@link TypePageCollectionInfo#EMPTY}.
   */
  private static TypePageCollectionInfo parseCollectionInfo(Document document) {
    for (var chapter : document.select("p.V8SH_chapter, P.V8SH_chapter")) {
      var t = chapter.text().trim();
      if (!"Элементы коллекции:".equals(t) && !"Collection elements:".equals(t)) {
        continue;
      }
      return parseCollectionBlock(chapter);
    }
    return TypePageCollectionInfo.EMPTY;
  }

  /**
   * Walk's через всех siblings до следующего chapter'а, нарезает по
   * {@code <br>} на text-фрагменты и распределяет их по семантике
   * (имя типа / Для каждого / индексатор).
   */
  private static TypePageCollectionInfo parseCollectionBlock(Element chapter) {
    var fromLinks = new ArrayList<String>();
    var fragments = new ArrayList<String>();
    var current = new StringBuilder();
    // Имя типа — ссылка / текст ДО первого <br>. Всё после первого <br> —
    // это уже описание, и встроенные в него <a>-теги (например, ссылка на
    // «Стиль» в фразе «При обходе выбираются <a>Стиль</a>») должны
    // попадать в текст описания, а не в список типов элементов.
    var beforeFirstBr = true;

    for (Node node = chapter.nextSibling(); node != null; node = node.nextSibling()) {
      if (node instanceof Element el) {
        var tag = el.tagName().toLowerCase(Locale.ROOT);
        if ("p".equals(tag) && el.hasClass("V8SH_chapter")) {
          break;
        }
        if ("br".equals(tag)) {
          fragments.add(current.toString().trim());
          current.setLength(0);
          beforeFirstBr = false;
        } else if ("a".equals(tag) && beforeFirstBr) {
          var name = el.text().trim();
          if (!name.isEmpty()) {
            fromLinks.add(name);
          }
        } else {
          // i, span, font, <a> в описании после <br> — собираем как текст
          current.append(el.text());
        }
      } else if (node instanceof TextNode t) {
        current.append(t.text());
      }
    }
    if (current.length() > 0) {
      fragments.add(current.toString().trim());
    }

    var elementTypes = !fromLinks.isEmpty()
      ? fromLinks
      : (fragments.isEmpty() ? List.<String>of() : splitElementNames(fragments.get(0)));

    var supportsForEach = false;
    var forEachDesc = "";
    var supportsIndex = false;
    var indexDesc = "";
    // Если имена пришли из links — первый text-фрагмент несёт «Для каждого»;
    // если имена из text — первый фрагмент это и есть имя, начинаем со второго.
    int startIdx = fromLinks.isEmpty() ? 1 : 0;
    for (int i = startIdx; i < fragments.size(); i++) {
      var frag = fragments.get(i);
      if (frag.isEmpty()) continue;
      if (frag.contains("Для каждого") || frag.contains("For each")) {
        supportsForEach = true;
        forEachDesc = stripBoilerplate(frag);
      } else if (frag.contains("[...]")
          || frag.contains("оператора [")
          || frag.contains("operator [")) {
        supportsIndex = true;
        indexDesc = stripBoilerplate(frag);
      }
    }
    return new TypePageCollectionInfo(elementTypes, supportsForEach, forEachDesc,
      supportsIndex, indexDesc);
  }

  /**
   * Срезает с описания «Элементы коллекции» стандартный prefix
   * («Для объекта доступен обход коллекции посредством оператора Для каждого
   * … Из … Цикл.», «Возможно обращение к … посредством оператора [...].» и
   * подобные) — оставляет только смысловую часть («При обходе выбираются …»,
   * «В качестве аргумента передается …»).
   * <p>
   * Резать «по первой точке» нельзя: у индексатора во фразе есть {@code [...]},
   * первая точка попадает внутрь этого {@code [...]}. Используем явные
   * маркеры окончания префикса.
   */
  private static String stripBoilerplate(String s) {
    var t = s.trim();
    if (t.startsWith("Для объекта доступен")) {
      // «… оператора Для каждого … Из … Цикл. <хвост>»
      var marker = "Цикл.";
      var idx = t.indexOf(marker);
      if (idx >= 0) {
        return t.substring(idx + marker.length()).trim();
      }
    }
    if (t.startsWith("Возможно обращение")) {
      // «… посредством оператора [...]. <хвост>»
      var marker = "[...].";
      var idx = t.indexOf(marker);
      if (idx >= 0) {
        return t.substring(idx + marker.length()).trim();
      }
    }
    return t;
  }

  /**
   * «Произвольный» / «Строка, Число» / «Строка / Число» — разные варианты
   * перечисления нескольких типов элемента в одной строке. Разбиваем по
   * запятым и слэшам, фильтруем пустые.
   */
  private static List<String> splitElementNames(String raw) {
    var result = new ArrayList<String>();
    for (var part : raw.split("[,/]")) {
      var trimmed = part.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /**
   * Разбирает главную страницу системного перечисления: «страничные» секции
   * (описание, доступность, версии, «См. также:», «Пример:») плюс общий тип
   * значений enum-«библиотеки» из фразы
   * {@code Значения этого набора имеют тип X.} — см.
   * {@link ContextEnum#valueType()}.
   * <p>
   * Навигационный чаптер «Значения» с перечнем самих значений в текст не
   * попадает — значения приходят из дерева HBK.
   * <p>
   * Для «обычных» системных перечислений ({@code ВидДвиженияНакопления}
   * и т.п.) фразы про общий тип на странице нет — поле {@code valueType}
   * остаётся пустой строкой. Поиск ведётся по полному тексту страницы
   * регулярным выражением, а не по DOM-структуре с {@code V8SH_chapter}-секциями:
   * маркер встречается внутри абзаца описания, где разметка непоследовательная
   * между разными HBK-страницами.
   */
  @SneakyThrows
  protected EnumDescription parseEnumPage(Page page) {
    final var document = pageSource.parse(page.htmlPath());
    var result = new EnumDescription();
    parsePageSections(document, result);
    var bodyText = document.body().text();
    var m = ENUM_VALUE_TYPE_PATTERN.matcher(bodyText);
    if (m.find()) {
      result.valueType = m.group(1).trim();
    }
    return result;
  }

  /**
   * «Значения этого набора имеют тип X.» (ru) /
   * «The values of this set have the type X.» (en).
   * Группа 1 — имя типа. Точная EN-формулировка взята с страницы
   * {@code PictureLib} реального {@code shcntx_root.hbk}.
   */
  private static final Pattern ENUM_VALUE_TYPE_PATTERN = Pattern.compile(
    "(?:Значения этого набора имеют тип|The values of this set have the type)\\s+([^.\\n]+?)\\.");

  /**
   * Разбирает страницу значения перечисления (например,
   * {@code РежимВиджета/properties/Active1.html}). Структура простая:
   * заголовок + чаптер {@code Описание:} + version-info.
   */
  @SneakyThrows
  protected EnumValueDescription parseEnumValuePage(Page page) {
    final var document = pageSource.parse(page.htmlPath());
    var result = new EnumValueDescription();
    result.recommendedReplacements = findRecommendedReplacements(document);

    var descriptionSection = false;

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }

      if (descriptionSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          descriptionSection = false;
        } else {
          result.description = result.description.concat(getDescription(node));
        }
      }

      if (node.attr("class").equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          result.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          result.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
      }

      if (node.attr("class").equals("V8SH_chapter") && node instanceof Element n) {
        var t = n.text();
        descriptionSection = t.contains("Описание:") || t.contains("Примечание:")
            || t.contains("Description:");
      }
    }

    return result;
  }

  @SneakyThrows
  protected PropertyDescription parsePropertyPage(Page page) {

    final var document = pageSource.parse(page.htmlPath());

    var result = new PropertyDescription();
    result.recommendedReplacements = findRecommendedReplacements(document);

    var accessModeSection = false;
    var descriptionSection = false;
    var typeSection = false;
    var collectionElementSection = false;
    var availabilitySection = false;
    var notesSection = false;
    var seeAlsoSection = false;
    var descriptionSeen = false;
    var seeAlso = new SeeAlsoCollector();

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }

      if (isCodeTable(node)) {
        result.description = stripTrailingExampleMarker(result.description);
        addSnippet(result.examples, node);
        continue;
      }
      extractNestedCodeTables(node, result.examples);

      if (accessModeSection) {

        if (node instanceof TextNode n) {

          var accessMode = n.text();
          if (accessMode.endsWith(".")) {
            accessMode = accessMode.substring(0, accessMode.length() - 1);
          }

          result.accessMode = accessMode;
        }

        accessModeSection = false;
      }

      if (descriptionSection) {

        if (node instanceof TextNode n && containsTypeMarker(n.text())) {
          typeSection = true;

          if (containsArbitraryType(n.text())) {
            result.types.add("Произвольный");
          }

        } else if (typeSection) {

          if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
            typeSection = false;
          } else if (node instanceof Element n) {
            result.types.add(n.text());
          } else if (node instanceof TextNode n && containsArbitraryType(n.text())) {
            result.types.add("Произвольный");
          }

        } else if (node instanceof TextNode n && containsCollectionElementMarker(n.text())) {
          collectionElementSection = true;
        } else if (collectionElementSection) {
          if (node instanceof Element n && !n.tag().equals(Tag.valueOf("br"))) {
            result.rawCollectionElementTypes.add(n.text());
            collectionElementSection = false;
          } else if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
            collectionElementSection = false;
          }
        } else if (node.attr("class").equals("V8SH_chapter")) {
          descriptionSection = false;
        } else {

          var text = "";

          if (node instanceof TextNode n) {
            text = n.text();
          } else if (node instanceof Element n) {
            text = n.wholeText();
          }

          text = text.replace(";", ";\n")
                  .replace(":", ":\n");

          result.description = result.description
                  .concat(text);

        }

      }

      if (notesSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          notesSection = false;
        } else {
          result.notes = result.notes.concat(getDescription(node));
        }
      }

      if (seeAlsoSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          seeAlso.flush(result.seeAlso);
          seeAlsoSection = false;
        } else if (node instanceof Element n && "a".equalsIgnoreCase(n.tag().getName())) {
          seeAlso.accept(n);
        }
      }

      if (availabilitySection) {

        var text = "";

        if (node instanceof TextNode n) {
          text = n.text();
        } else if (node instanceof Element n) {
          text = textBeforeFirstBr(n);
        }

        result.availabilities = splitAvailabilities(text);

        availabilitySection = false;

      }

      // Версия. На странице может быть несколько <p class="V8SH_versionInfo">:
      // «Доступен, начиная с версии X», «Не рекомендуется использовать,
      // начиная с версии X», «Описание изменено в версии X». Различаем по префиксу.
      if (node.attr("class").equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          result.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          result.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
      }

      if (node.attr("class").equals("V8SH_chapter")
              && node instanceof Element n) {

        var t = n.text();
        var tt = t.trim();
        var isDescriptionHeader = t.contains("Описание:") || t.contains("Description:");
        // «Примечание:» — отдельная заметка, если «Описание:» на странице уже
        // было; иначе примечание и есть описание (как в parseMethodPage).
        var isNote = t.contains("Примечание:");
        descriptionSeen = descriptionSeen || isDescriptionHeader;
        accessModeSection = t.contains("Использование:") || t.contains("Usage:");
        descriptionSection = isDescriptionHeader || (isNote && !descriptionSeen);
        notesSection = "Замечание:".equals(tt) || "Note:".equals(tt) || (isNote && descriptionSeen);
        availabilitySection = t.contains("Доступность:") || t.contains("Availability:");
        seeAlsoSection = "См. также:".equals(tt) || "See also:".equals(tt);

      }
    }

    seeAlso.flush(result.seeAlso);

    return result;

  }

  /**
   * Разбирает страницу параметра формы
   * ({@code .../ClientApplicationForm/formparams/ReadOnly57.html}).
   * <p>
   * Разметка полностью совпадает со страницей свойства — чаптер «Описание:»
   * с ведущим «Тип: …», опциональные «Примечание:» / «См. также:» и
   * version-info, — поэтому обход делает {@link #parsePropertyPage(Page)}.
   * Отличается только смысл чаптера «Использование:»: у свойства там режим
   * доступа («Только чтение»), а у параметра формы — признак ключевого
   * («Ключевой» / «Key Parameter»), см. {@link #isKeyParameterMarker(String)}.
   */
  protected FormParameterDescription parseFormParameterPage(Page page) {
    return new FormParameterDescription(parsePropertyPage(page));
  }

  /**
   * Маркер ключевого параметра формы в чаптере «Использование:».
   * ru: «Ключевой»; en: «Key Parameter» (обе формулировки сверены по
   * реальным {@code shcntx_ru.hbk} / {@code shcntx_root.hbk}).
   */
  private static boolean isKeyParameterMarker(String usage) {
    if (usage == null) {
      return false;
    }
    var u = usage.trim();
    return u.equalsIgnoreCase("Ключевой") || u.equalsIgnoreCase("Key Parameter");
  }

  @SneakyThrows
  protected MethodDescription parseMethodPage(Page page) {

    final var document = pageSource.parse(page.htmlPath());
    var result = new MethodDescription();
    result.recommendedReplacements = findRecommendedReplacements(document);

    var hasOverloads = document.text().contains("Вариант синтаксиса:");

    var descriptionSection = false;
    var typeSection = false;
    var availabilitySection = false;
    var parametersSection = false;
    var methodSignatureDescriptionSection = false;
    var returnValuesSection = false;
    var syntaxSection = false;
    var exampleSection = false;
    var seeAlsoSection = false;
    var notesSection = false;
    var descriptionSeen = false;

    MethodSignatureDescription currentMethodSignatureDescription = null;
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    // Ссылка «См. также» кодируется парой <a>: владелец (type-ссылка) + член
    // (ссылка с member-маркером). Член уже несёт владельца через href, поэтому
    // предшествующую type-ссылку того же владельца не дублируем. Standalone
    // type-ссылку (без следующего члена) отдаём как имя типа.
    String pendingSeeAlsoText = null;
    String pendingSeeAlsoPath = null;

    // Аккумулятор для текущей строки типа в "Тип: …". HBK иногда разбивает
    // имя generic-типа на несколько HTML-узлов: <a>СправочникОбъект.</a>
    // <span>&lt;</span><a>Имя справочника</a><span>&gt;</span>. Чтобы не
    // получать на выходе ["СправочникОбъект.", "<", "Имя справочника", ">"],
    // склеиваем соседние фрагменты в одну строку и разрезаем её только на
    // запятых (как явных разделителях типов).
    var currentTypeLine = new StringBuilder();

    if (!hasOverloads) {
      // Имя варианта сигнатуры остаётся пустым: значимого имени в HBK у
      // одиночной сигнатуры нет (в HTML не присутствует чаптер
      // «Вариант синтаксиса:…»). Раньше тут было хардкод "Основной",
      // который ломал двуязычный мерджер — он подменял alias на ту же
      // русскую строку из en-HBK.
      currentMethodSignatureDescription = new MethodSignatureDescription();
    }

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }

      if (isCodeTable(node)) {
        result.description = stripTrailingExampleMarker(result.description);
        addSnippet(result.examples, node);
        continue;
      }
      extractNestedCodeTables(node, result.examples);

      if (syntaxSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          syntaxSection = false;
        } else if (currentMethodSignatureDescription != null) {
          String text = "";
          if (node instanceof TextNode n) {
            text = n.text();
          } else if (node instanceof Element n) {
            text = n.text();
          }
          currentMethodSignatureDescription.syntaxText =
              currentMethodSignatureDescription.syntaxText.concat(text);
        }
      }

      if (methodSignatureDescriptionSection) {

        if (node.attr("class").equals("V8SH_chapter")) {
          methodSignatureDescriptionSection = false;
        } else {

          var text = "";

          if (node instanceof TextNode n) {
            text = n.text();
          } else if (node instanceof Element n) {
            text = n.wholeText();
          }

          text = text.replace(";", ";\n")
                  .replace(":", ":\n");

          currentMethodSignatureDescription.description = currentMethodSignatureDescription.description
                  .concat(text);

        }
      }

      if (returnValuesSection) {

        if (node.attr("class").equals("V8SH_chapter")) {
          flushTypeLine(currentTypeLine, result.returnValues);
          typeSection = false;
          returnValuesSection = false;
        } else {

          if (node instanceof TextNode n && containsTypeMarker(n.text())) {
            // Перед новым "Тип:" — flush'им предыдущий аккумулятор
            // (на случай, если в одной секции "Возвращаемое значение:"
            // указано несколько "Тип:" подряд).
            flushTypeLine(currentTypeLine, result.returnValues);
            typeSection = true;

            // Текст после «Тип:»/«Type:» иногда содержит inline-имя — забираем его.
            var text = n.text();
            int idx = indexOfTypeMarker(text);
            if (idx >= 0) {
              var tail = text.substring(idx + typeMarkerLength(text, idx));
              if (!tail.isBlank()) {
                currentTypeLine.append(tail);
              }
            }

          } else if (typeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              // Конец строки типа: flush аккумулятора, выходим из typeSection
              // (после <br> идёт текстовое описание возврата).
              flushTypeLine(currentTypeLine, result.returnValues);
              typeSection = false;
            } else if (node instanceof Element n) {
              // <a>префикс</a>, <span>&lt;</span>, <a>имя</a>, <span>&gt;</span>
              // — все эти фрагменты СОБИРАЮТСЯ в одну строку имени типа.
              currentTypeLine.append(n.text());
            } else if (node instanceof TextNode n) {
              // Текст внутри секции — обычно разделитель ", " или " " между
              // фрагментами. Аккумулируем как есть; flush на запятой
              // выполняется в самом flushTypeLine через split.
              currentTypeLine.append(n.text());
            }

          } else {
            // После списка типов идёт описание возврата (текст / <a>-ссылки).
            String text = "";
            if (node instanceof TextNode n) {
              text = n.text();
            } else if (node instanceof Element n) {
              text = n.wholeText();
            }
            if (!text.isBlank()) {
              result.returnValueDescription = result.returnValueDescription.concat(text);
            }
          }

        }
      }

      if (exampleSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          exampleSection = false;
        } else if (node instanceof Element n) {
          // Пример лежит в <TABLE> с <font>-разметкой; берём чистый текст.
          var snippet = n.wholeText().trim();
          if (!snippet.isBlank()) {
            result.examples.add(snippet);
          }
        }
      }

      if (seeAlsoSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          if (pendingSeeAlsoText != null) {
            result.seeAlso.add(pendingSeeAlsoText);
            pendingSeeAlsoText = null;
            pendingSeeAlsoPath = null;
          }
          seeAlsoSection = false;
        } else if (node instanceof Element n && "a".equalsIgnoreCase(n.tag().getName())) {
          var href = n.attr("href");
          var text = n.text().trim();
          if (!text.isBlank()) {
            var path = hrefToPath(href);
            var markerIdx = path == null ? -1 : memberMarkerIndex(path);
            if (markerIdx >= 0) {
              var ownerPath = path.substring(0, markerIdx) + ".html";
              if (ownerPath.equals(pendingSeeAlsoPath)) {
                // pending type-ссылка — владелец этого члена, не дублируем.
                pendingSeeAlsoText = null;
                pendingSeeAlsoPath = null;
              } else if (pendingSeeAlsoText != null) {
                result.seeAlso.add(pendingSeeAlsoText);
                pendingSeeAlsoText = null;
                pendingSeeAlsoPath = null;
              }
              result.seeAlso.add(qualifyReference(href, text));
            } else {
              if (pendingSeeAlsoText != null) {
                result.seeAlso.add(pendingSeeAlsoText);
              }
              pendingSeeAlsoText = text;
              pendingSeeAlsoPath = path;
            }
          }
        }
      }

      if (parametersSection) {

        if (node.attr("class").equals("V8SH_rubric")
            && node instanceof Element n) {

          if (currentMethodSignatureParameterDescription != null) {
            currentMethodSignatureDescription.parameters.add(currentMethodSignatureParameterDescription);
          }

          currentMethodSignatureParameterDescription = new MethodSignatureParameterDescription();

          var match = parameterNamePattern.matcher(n.text());

          if (match.find()) { // Параметр метода
            currentMethodSignatureParameterDescription.name = buildParameterName(match);
            currentMethodSignatureParameterDescription.isRequired = isRequiredMarker(match.group(3));
          } else { // Параметр события — без суффикса (обязательный)
            currentMethodSignatureParameterDescription.name = stripAngleBrackets(n.text());
            currentMethodSignatureParameterDescription.isRequired = true;
          }

        } else {

          if (node instanceof TextNode n && containsTypeMarker(n.text())) {
            typeSection = true;

            if (containsArbitraryType(n.text())) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (typeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              typeSection = false;
            } else if (node instanceof Element n) {
              currentMethodSignatureParameterDescription.types.add(n.text());
            } else if (node instanceof TextNode n && containsArbitraryType(n.text())) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (node.attr("class").equals("V8SH_chapter")) {
            // Выход из секции параметров на следующий V8SH_chapter — нужно
            // закоммитить накопленный параметр сразу, иначе он может «утечь»
            // в следующую секцию V8SH_rubric (например, в Возвращаемое значение).
            if (currentMethodSignatureParameterDescription != null) {
              currentMethodSignatureDescription.parameters.add(currentMethodSignatureParameterDescription);
              currentMethodSignatureParameterDescription = null;
            }
            parametersSection = false;
          } else {
            var text = "";

            if (node instanceof TextNode n) {
              text = n.text();
            } else if (node instanceof Element n) {
              text = n.wholeText();
            }

            text = text.replace(";", ";\n")
                    .replace(":", ":\n");

            currentMethodSignatureParameterDescription.description = currentMethodSignatureParameterDescription.description
                    .concat(text);
          }
        }
      }

      if (descriptionSection) {

        if (node.attr("class").equals("V8SH_chapter")) {
          descriptionSection = false;
        } else {
          result.description = result.description
            .concat(getDescription(node));
        }

      }

      if (notesSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          notesSection = false;
        } else {
          result.notes = result.notes.concat(getDescription(node));
        }
      }

      if (availabilitySection) {

        var text = "";

        if (node instanceof TextNode n) {
          text = n.text();
        } else if (node instanceof Element n) {
          text = textBeforeFirstBr(n);
        }

        result.availabilities = splitAvailabilities(text);

        availabilitySection = false;

      }

      if (node.attr("class").equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          result.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          result.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
      }

      if (node.attr("class").equals("V8SH_chapter")
              && node instanceof Element n) {

        // Заголовки чаптеров на ru/en. en-варианты сверены по реальному
        // shcntx_root.hbk (V8SH_chapter-метки) — см. en-смок-тест.
        var t = n.text();
        var tt = t.trim();
        var isDescriptionHeader = t.contains("Описание:") || t.contains("Description:");
        var isNote = t.contains("Примечание:");
        descriptionSeen = descriptionSeen || isDescriptionHeader;
        // «Примечание:» — это заметка (пара к en «Note:»), если на странице уже
        // встретилось «Описание:»; иначе «Примечание:» выступает самим описанием
        // (поля регистров и отдельные методы без отдельного «Описание:»).
        descriptionSection = isDescriptionHeader || (isNote && !descriptionSeen);
        availabilitySection = t.contains("Доступность:") || t.contains("Availability:");
        parametersSection = t.contains("Параметры:") || t.contains("Parameters:");
        methodSignatureDescriptionSection = t.contains("Описание варианта метода:")
            || t.contains("Description of method variant:");
        returnValuesSection = t.contains("Возвращаемое значение:")
            || t.contains("Returned value:");
        syntaxSection = "Синтаксис:".equals(tt) || "Syntax:".equals(tt);
        exampleSection = "Пример:".equals(tt) || "Example:".equals(tt);
        seeAlsoSection = "См. также:".equals(tt) || "See also:".equals(tt);
        notesSection = "Замечание:".equals(tt) || "Note:".equals(tt) || (isNote && descriptionSeen);

        if (t.contains("Вариант синтаксиса:") || t.contains("Syntax variant:")) {

          if (currentMethodSignatureParameterDescription != null) {
            currentMethodSignatureDescription.parameters.add(currentMethodSignatureParameterDescription);
            currentMethodSignatureParameterDescription = null;
          }

          if (currentMethodSignatureDescription != null) {
            applyVariadicMarkers(currentMethodSignatureDescription.parameters);
            result.signatures.add(currentMethodSignatureDescription);
          }

          currentMethodSignatureDescription = new MethodSignatureDescription();
          currentMethodSignatureDescription.name = n.text()
                  .replace("Вариант синтаксиса:", "")
                  .trim();
        }

        if (n.text().contains("Примечание:")) {
          result.description = result.description.concat("\n");
        }

      }
    }

    if (currentMethodSignatureParameterDescription != null) {
      currentMethodSignatureDescription.parameters.add(currentMethodSignatureParameterDescription);
    }

    if (currentMethodSignatureDescription != null) {
      applyVariadicMarkers(currentMethodSignatureDescription.parameters);
      result.signatures.add(currentMethodSignatureDescription);
    }


    return result;

  }

  @SneakyThrows
  protected ConstructorDescription parseConstructorPage(Page page) {
    final var document = pageSource.parse(page.htmlPath());

    var result = new ConstructorDescription();
    result.recommendedReplacements = findRecommendedReplacements(document);
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    var isDescription = false;
    var isParameters = false;
    var isTypeSection = false;
    var isSyntax = false;
    var isExample = false;
    var isSeeAlso = false;
    var seeAlso = new SeeAlsoCollector();

    for (Node node : document.body().childNodes()) {
      if (isFooterSeparator(node)) {
        // Ниже — только «Методическая информация» (ссылка на its).
        break;
      }

      if (isCodeTable(node)) {
        result.description = stripTrailingExampleMarker(result.description);
        addSnippet(result.examples, node);
        continue;
      }
      extractNestedCodeTables(node, result.examples);
      final var className = node.hasAttr("class") ? node.attr("class") : "";
      if ((className.equals("V8SH_pagetitle") || className.equals("V8SH_heading")) && node instanceof Element n) {
        result.name = n.text();
        continue;
      }

      if (className.equals("V8SH_versionInfo") && node instanceof Element n) {
        var versionText = n.text();
        if (isSinceVersionText(versionText)) {
          result.sinceVersion = parseSinceVersion(versionText);
        } else if (isDeprecatedSinceVersionText(versionText)) {
          result.deprecatedSinceVersion = parseSinceVersion(versionText);
        }
        continue;
      }

      if (className.equals("V8SH_chapter") && node instanceof Element n) {
        if (isSeeAlso) {
          seeAlso.flush(result.seeAlso);
        }
        isDescription = false;
        isParameters = false;
        isTypeSection = false;
        isSyntax = false;
        isExample = false;
        isSeeAlso = false;

        switch (n.text().trim()) {
          case "Параметры:", "Parameters:" -> isParameters = true;
          case "Описание:", "Description:" -> isDescription = true;
          case "Синтаксис:", "Syntax:" -> isSyntax = true;
          case "Пример:", "Example:" -> isExample = true;
          case "См. также:", "See also:" -> isSeeAlso = true;
        }
        continue;
      }

      if (isExample) {
        if (node instanceof Element n) {
          var snippet = n.wholeText().trim();
          if (!snippet.isBlank()) {
            result.examples.add(snippet);
          }
        }
        continue;
      }

      if (isSeeAlso) {
        if (node instanceof Element n && "a".equalsIgnoreCase(n.tag().getName())) {
          seeAlso.accept(n);
        }
        continue;
      }

      if (isSyntax) {
        String text = "";
        if (node instanceof TextNode n) {
          text = n.text();
        } else if (node instanceof Element n) {
          text = n.text();
        }
        result.syntaxText = result.syntaxText.concat(text);
        continue;
      }

      if (isDescription) {
        result.description = result.description.concat(getDescription(node));
      } else if (isParameters) {
        if (className.equals("V8SH_rubric")
          && node instanceof Element n) {

          if (currentMethodSignatureParameterDescription != null) {
            result.parameters.add(currentMethodSignatureParameterDescription);
          }

          currentMethodSignatureParameterDescription = new MethodSignatureParameterDescription();

          var match = parameterNamePattern.matcher(n.text());

          if (match.find()) { // Параметр конструктора
            currentMethodSignatureParameterDescription.name = buildParameterName(match);
            currentMethodSignatureParameterDescription.isRequired = isRequiredMarker(match.group(3));
          } else { // конструктор без явного суффикса обязательности
            currentMethodSignatureParameterDescription.name = stripAngleBrackets(n.text());
            currentMethodSignatureParameterDescription.isRequired = true;
          }

        } else {

          if (node instanceof TextNode n && containsTypeMarker(n.text())) {
            isTypeSection = true;

            if (containsArbitraryType(n.text())) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (isTypeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              isTypeSection = false;
            } else if (node instanceof Element n) {
              currentMethodSignatureParameterDescription.types.add(n.text());
            } else if (node instanceof TextNode n && containsArbitraryType(n.text())) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (node.attr("class").equals("V8SH_chapter")) {
            isParameters = false;
          } else {
            currentMethodSignatureParameterDescription.description = currentMethodSignatureParameterDescription.description
              .concat(getDescription(node));
          }
        }
      }
    }

    if (currentMethodSignatureParameterDescription != null) {
      result.parameters.add(currentMethodSignatureParameterDescription);
    }

    applyVariadicMarkers(result.parameters);
    seeAlso.flush(result.seeAlso);

    return result;
  }

  private String getDescription(Node node) {
    var text = "";

    if (node instanceof TextNode n) {
      text = n.text();
    } else if (node instanceof Element n) {
      text = n.wholeText();
    }

    text = text.replace(";", ";\n")
      .replace(":", ":\n");

    return text;
  }

  /**
   * Общие «страничные» поля, которые несёт любая страница синтакс-помощника:
   * описание, заметка, доступность, версии, «См. также:», «Пример:» и
   * рекомендации по замене. Заполняются {@link #parsePageSections(Document, PageDescription)}.
   */
  @Getter
  protected abstract static class PageDescription {
    /**
     * Имя с заголовка страницы ({@code V8SH_pagetitle}), ru-сторона.
     * В оглавлении HBK имя узла контекстно относительно родителя
     * («Поле ввода» → «Расширение»), а на самой странице стоит полное
     * («Расширение поля ввода системного перечисления»), поэтому имя контекста
     * берётся отсюда — см. {@code HbkTreeParser#contextName}.
     */
    protected String pageTitleRu = "";
    /** Имя с заголовка страницы, en-сторона (часть в скобках). */
    protected String pageTitleEn = "";
    protected String description = "";
    protected String notes = "";
    protected List<String> availabilities = Collections.emptyList();
    protected String sinceVersion = "";
    protected String deprecatedSinceVersion = "";
    protected final List<String> seeAlso = new ArrayList<>();
    protected final List<String> examples = new ArrayList<>();
    protected List<String> recommendedReplacements = Collections.emptyList();
  }

  /**
   * Главная страница типа / коллекции: страничные поля + блок
   * «Элементы коллекции:».
   */
  @Getter
  protected static class TypePageDescription extends PageDescription {
    private TypePageCollectionInfo collectionInfo = TypePageCollectionInfo.EMPTY;

    protected TypePageDescription() {
    }
  }

  @Getter
  protected static class PropertyDescription {
    private String accessMode = "";
    private final List<String> types = new ArrayList<>();
    private final List<String> rawCollectionElementTypes = new ArrayList<>();
    private String description = "";
    private String notes = "";
    private List<String> availabilities = Collections.emptyList();
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";
    private List<String> recommendedReplacements = Collections.emptyList();
    private final List<String> seeAlso = new ArrayList<>();
    private final List<String> examples = new ArrayList<>();

    protected PropertyDescription() {
    }

  }

  /**
   * Параметр формы, снятый со страницы {@code formparams/*.html}.
   * Строится из {@link PropertyDescription} (разметка страниц идентична),
   * поля {@code accessMode} / {@code availabilities} свойства при этом
   * схлопываются в один признак {@link #key}.
   */
  @Getter
  protected static class FormParameterDescription {

    private final List<String> types;
    private final String description;
    private final boolean key;
    private final String sinceVersion;
    private final String deprecatedSinceVersion;
    private final List<String> recommendedReplacements;
    private final List<String> seeAlso;

    private FormParameterDescription(PropertyDescription raw) {
      this.types = raw.getTypes();
      this.description = raw.getDescription();
      this.seeAlso = raw.getSeeAlso();
      this.key = isKeyParameterMarker(raw.getAccessMode());
      this.sinceVersion = raw.getSinceVersion();
      this.deprecatedSinceVersion = raw.getDeprecatedSinceVersion();
      this.recommendedReplacements = raw.getRecommendedReplacements();
    }

  }

  @Getter
  protected static class MethodDescription {

    private final List<String> returnValues = new ArrayList<>();
    private String description = "";
    private String returnValueDescription = "";
    private String notes = "";
    private List<String> availabilities = Collections.emptyList();
    private final List<MethodSignatureDescription> signatures = new ArrayList<>();
    private final List<String> examples = new ArrayList<>();
    private final List<String> seeAlso = new ArrayList<>();
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";
    private List<String> recommendedReplacements = Collections.emptyList();

    private MethodDescription() {
    }

  }

  @Getter
  protected static class MethodSignatureDescription {

    private final List<MethodSignatureParameterDescription> parameters = new ArrayList<>();
    private String name = "";
    private String description = "";
    private String syntaxText = "";

    private MethodSignatureDescription() {
    }

  }

  @Getter
  protected static class MethodSignatureParameterDescription {

    private String name = "";
    private String description = "";
    private boolean isRequired = false;
    private boolean variadic = false;
    private final List<String> types = new ArrayList<>();

    private MethodSignatureParameterDescription() {
    }

    /**
     * Значение по умолчанию (например, {@code Истина}), извлечённое из текста
     * описания по шаблону «Значение по умолчанию: X.». Пусто, если шаблон не
     * найден.
     */
    public String getDefaultValue() {
      return parseDefaultValue(description);
    }

    /**
     * Описание параметра без хвоста «Значение по умолчанию: X.» — этот блок
     * выносится в отдельное поле {@link #getDefaultValue()} и не должен
     * дублироваться в текстовом описании, чтобы потребители (hover/completion
     * LS) не показывали значение дефолта дважды (формально через {@code =}
     * и снова в текстовом описании).
     */
    public String getDescription() {
      return stripDefaultValueText(description);
    }

  }

  @Getter
  protected static class ConstructorDescription {
    private final List<MethodSignatureParameterDescription> parameters = new ArrayList<>();
    private String name = "";
    private String description = "";
    private String syntaxText = "";
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";
    private List<String> recommendedReplacements = Collections.emptyList();
    // Блока «Доступность:» на страницах конструкторов в HBK нет (проверено по
    // всем 444 страницам ctors/ платформы 8.3.27) — поля под него не держим.
    private final List<String> examples = new ArrayList<>();
    private final List<String> seeAlso = new ArrayList<>();

    private ConstructorDescription() {
    }
  }

  @Getter
  protected static class EnumDescription extends PageDescription {
    private String valueType = "";

    private EnumDescription() {
    }
  }

  @Getter
  protected static class EnumValueDescription {
    private String description = "";
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";
    private List<String> recommendedReplacements = Collections.emptyList();

    private EnumValueDescription() {
    }
  }

  @Getter
  protected static class GlobalContextPageDescription {
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";

    private GlobalContextPageDescription() {
    }
  }
}
