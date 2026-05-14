package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github.eightm.lib.Page;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HtmlParser {

  private final PageSource pageSource;
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
   * Создаёт парсер на источнике страниц (in-memory или файловая система).
   * Двуязычная поддержка реализуется внешним мерджером
   * ({@link com.github._1c_syntax.bsl.context.platform.BilingualMerger}),
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
   * Ищет «Значение по умолчанию: X.» в описании параметра. Возвращает
   * пустую строку, если не найдено.
   */
  private static String parseDefaultValue(String description) {
    Matcher m = DEFAULT_VALUE_PATTERN.matcher(description);
    return m.find() ? m.group(1).trim() : "";
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
   * Разбирает страницу значения перечисления (например,
   * {@code РежимВиджета/properties/Active1.html}). Структура простая:
   * заголовок + чаптер {@code Описание:} + version-info.
   */
  @SneakyThrows
  protected EnumValueDescription parseEnumValuePage(Page page) {
    final var document = pageSource.parse(page.htmlPath());
    var result = new EnumValueDescription();

    var descriptionSection = false;

    for (Node node : document.body().childNodes()) {

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
        descriptionSection = n.text().contains("Описание:") || n.text().contains("Примечание:");
      }
    }

    return result;
  }

  @SneakyThrows
  protected PropertyDescription parsePropertyPage(Page page) {

    final var document = pageSource.parse(page.htmlPath());

    var result = new PropertyDescription();

    var accessModeSection = false;
    var descriptionSection = false;
    var typeSection = false;
    var availabilitySection = false;

    for (Node node : document.body().childNodes()) {

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

        if (node instanceof TextNode n && n.text().contains("Тип:")) {
          typeSection = true;

          if (n.text().contains("Произвольный")) {
            result.types.add("Произвольный");
          }

        } else if (typeSection) {

          if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
            typeSection = false;
          } else if (node instanceof Element n) {
            result.types.add(n.text());
          } else if (node instanceof TextNode n && n.text().contains("Произвольный")) {
            result.types.add("Произвольный");
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

        accessModeSection = n.text().contains("Использование:");
        descriptionSection = n.text().contains("Описание:") || n.text().contains("Примечание:");
        availabilitySection = n.text().contains("Доступность:");

        if (n.text().contains("Примечание:")) {
          result.description = result.description.concat("\n");
        }

      }
    }

    return result;

  }

  @SneakyThrows
  protected MethodDescription parseMethodPage(Page page) {

    final var document = pageSource.parse(page.htmlPath());
    var result = new MethodDescription();

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

    MethodSignatureDescription currentMethodSignatureDescription = null;
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    if (!hasOverloads) {
      // Имя варианта сигнатуры остаётся пустым: значимого имени в HBK у
      // одиночной сигнатуры нет (в HTML не присутствует чаптер
      // «Вариант синтаксиса:…»). Раньше тут было хардкод "Основной",
      // который ломал двуязычный мерджер — он подменял alias на ту же
      // русскую строку из en-HBK.
      currentMethodSignatureDescription = new MethodSignatureDescription();
    }

    for (Node node : document.body().childNodes()) {

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
          returnValuesSection = false;
        } else {

          if (node instanceof TextNode n && n.text().contains("Тип:")) {
            typeSection = true;

            if (n.text().contains("Произвольный")) {
              result.returnValues.add("Произвольный");
            }

          } else if (typeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              typeSection = false;
            } else if (node instanceof Element n) {
              result.returnValues.add(n.text());
            } else if (node instanceof TextNode n && n.text().contains("Произвольный")) {
              result.returnValues.add("Произвольный");
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
          seeAlsoSection = false;
        } else if (node instanceof Element n && "a".equalsIgnoreCase(n.tag().getName())) {
          var text = n.text().trim();
          if (!text.isBlank()) {
            result.seeAlso.add(text);
          }
        }
      }

      if (notesSection) {
        if (node.attr("class").equals("V8SH_chapter")) {
          notesSection = false;
        } else {
          result.notes = result.notes.concat(getDescription(node));
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
            currentMethodSignatureParameterDescription.isRequired = match.group(3)
                    .equalsIgnoreCase("Обязательный");
          } else { // Параметр события — без суффикса (обязательный)
            currentMethodSignatureParameterDescription.name = stripAngleBrackets(n.text());
            currentMethodSignatureParameterDescription.isRequired = true;
          }

        } else {

          if (node instanceof TextNode n && n.text().contains("Тип:")) {
            typeSection = true;

            if (n.text().contains("Произвольный")) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (typeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              typeSection = false;
            } else if (node instanceof Element n) {
              currentMethodSignatureParameterDescription.types.add(n.text());
            } else if (node instanceof TextNode n && n.text().contains("Произвольный")) {
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

        descriptionSection = n.text().contains("Описание:") || n.text().contains("Примечание:");
        availabilitySection = n.text().contains("Доступность:");
        parametersSection = n.text().contains("Параметры:");
        methodSignatureDescriptionSection = n.text().contains("Описание варианта метода:");
        returnValuesSection = n.text().contains("Возвращаемое значение:");
        syntaxSection = "Синтаксис:".equals(n.text().trim());
        exampleSection = "Пример:".equals(n.text().trim());
        seeAlsoSection = "См. также:".equals(n.text().trim());
        notesSection = "Замечание:".equals(n.text().trim());

        if (n.text().contains("Вариант синтаксиса:")) {

          if (currentMethodSignatureParameterDescription != null) {
            currentMethodSignatureDescription.parameters.add(currentMethodSignatureParameterDescription);
            currentMethodSignatureParameterDescription = null;
          }

          if (currentMethodSignatureDescription != null) {
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
      result.signatures.add(currentMethodSignatureDescription);
    }


    return result;

  }

  @SneakyThrows
  protected ConstructorDescription parseConstructorPage(Page page) {
    final var document = pageSource.parse(page.htmlPath());

    var result = new ConstructorDescription();
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    var isDescription = false;
    var isParameters = false;
    var isTypeSection = false;
    var isSyntax = false;

    for (Node node : document.body().childNodes()) {
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
        isDescription = false;
        isParameters = false;
        isTypeSection = false;
        isSyntax = false;

        switch (n.text().trim()) {
          case "Параметры:" -> isParameters = true;
          case "Описание:" -> isDescription = true;
          case "Синтаксис:" -> isSyntax = true;
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
            currentMethodSignatureParameterDescription.isRequired = match.group(3)
              .equalsIgnoreCase("Обязательный");
          } else { // конструктор без явного суффикса обязательности
            currentMethodSignatureParameterDescription.name = stripAngleBrackets(n.text());
            currentMethodSignatureParameterDescription.isRequired = true;
          }

        } else {

          if (node instanceof TextNode n && n.text().contains("Тип:")) {
            isTypeSection = true;

            if (n.text().contains("Произвольный")) {
              currentMethodSignatureParameterDescription.types.add("Произвольный");
            }

          } else if (isTypeSection) {

            if (node instanceof Element n && n.tag().equals(Tag.valueOf("br"))) {
              isTypeSection = false;
            } else if (node instanceof Element n) {
              currentMethodSignatureParameterDescription.types.add(n.text());
            } else if (node instanceof TextNode n && n.text().contains("Произвольный")) {
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

  @Getter
  protected static class PropertyDescription {
    private String accessMode = "";
    private final List<String> types = new ArrayList<>();
    private String description = "";
    private List<String> availabilities = Collections.emptyList();
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";

    protected PropertyDescription() {
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

  }

  @Getter
  protected static class ConstructorDescription {
    private final List<MethodSignatureParameterDescription> parameters = new ArrayList<>();
    private String name = "";
    private String description = "";
    private String syntaxText = "";
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";

    private ConstructorDescription() {
    }
  }

  @Getter
  protected static class EnumValueDescription {
    private String description = "";
    private String sinceVersion = "";
    private String deprecatedSinceVersion = "";

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
