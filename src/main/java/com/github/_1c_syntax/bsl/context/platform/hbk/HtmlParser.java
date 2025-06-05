package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github.eightm.lib.Page;
import lombok.Getter;
import lombok.SneakyThrows;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.parser.Tag;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class HtmlParser {

  private final Path pagesPath;
  private final Pattern parameterNamePattern = Pattern.compile("<(.*)>.*\\((.*)\\)");

  public HtmlParser(Path pagesPath) {
    this.pagesPath = pagesPath;
  }

  @SneakyThrows
  protected PropertyDescription parsePropertyPage(Page page) {

    final var document = Jsoup.parse(
            pagesPath.resolve(Path.of("." + page.htmlPath())).toFile()
    );

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
          text = n.text();
        }

        result.availabilities = Arrays.stream(text.split(","))
                .map(String::trim)
                .map(s -> {
                  if (s.endsWith(".")) {
                    s = s.substring(0, s.length() - 1);
                  }
                  return s;
                })
                .toList();

        availabilitySection = false;

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

    final var document = Jsoup.parse(
            pagesPath.resolve(Path.of("." + page.htmlPath())).toFile()
    );
    var result = new MethodDescription();

    var hasOverloads = document.text().contains("Вариант синтаксиса:");

    var descriptionSection = false;
    var typeSection = false;
    var availabilitySection = false;
    var parametersSection = false;
    var methodSignatureDescriptionSection = false;
    var returnValuesSection = false;

    MethodSignatureDescription currentMethodSignatureDescription = null;
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    if (!hasOverloads) {
      currentMethodSignatureDescription = new MethodSignatureDescription();
      currentMethodSignatureDescription.name = "Основной";
    }

    for (Node node : document.body().childNodes()) {

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
            currentMethodSignatureParameterDescription.name = match.group(1);
            currentMethodSignatureParameterDescription.isRequired = match.group(2)
                    .equalsIgnoreCase("Обязательный");
          } else { // Параметр события

            currentMethodSignatureParameterDescription.name = n.text();
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

      if (availabilitySection) {

        var text = "";

        if (node instanceof TextNode n) {
          text = n.text();
        } else if (node instanceof Element n) {
          text = n.text();
        }

        result.availabilities = Arrays.stream(text.split(","))
                .map(String::trim)
                .map(s -> {
                  if (s.endsWith(".")) {
                    s = s.substring(0, s.length() - 1);
                  }
                  return s;
                })
                .toList();

        availabilitySection = false;

      }

      if (node.attr("class").equals("V8SH_chapter")
              && node instanceof Element n) {

        descriptionSection = n.text().contains("Описание:") || n.text().contains("Примечание:");
        availabilitySection = n.text().contains("Доступность:");
        parametersSection = n.text().contains("Параметры:");
        methodSignatureDescriptionSection = n.text().contains("Описание варианта метода:");
        returnValuesSection = n.text().contains("Возвращаемое значение:");

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
    final var document = Jsoup.parse(
      pagesPath.resolve(Path.of("." + page.htmlPath())).toFile()
    );

    var result = new ConstructorDescription();
    MethodSignatureParameterDescription currentMethodSignatureParameterDescription = null;

    var isDescription = false;
    var isParameters = false;
    var isTypeSection = false;

    for (Node node : document.body().childNodes()) {
      final var className = node.hasAttr("class") ? node.attr("class") : "";
      if ((className.equals("V8SH_pagetitle") || className.equals("V8SH_heading")) && node instanceof Element n) {
        result.name = n.text();
        continue;
      }

      if (className.equals("V8SH_chapter") && node instanceof Element n) {
        isDescription = false;
        isParameters = false;
        isTypeSection = false;

        switch (n.text()) {
          case "Параметры:" -> isParameters = true;
          case "Описание:" -> isDescription = true;
        }
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

          if (match.find()) { // Параметр метода
            currentMethodSignatureParameterDescription.name = match.group(1);
            currentMethodSignatureParameterDescription.isRequired = match.group(2)
              .equalsIgnoreCase("Обязательный");
          } else { // Параметр события

            currentMethodSignatureParameterDescription.name = n.text();
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

    protected PropertyDescription() {
    }

  }

  @Getter
  protected static class MethodDescription {

    private final List<String> returnValues = new ArrayList<>();
    private String description = "";
    private List<String> availabilities = Collections.emptyList();
    private final List<MethodSignatureDescription> signatures = new ArrayList<>();

    private MethodDescription() {
    }

  }

  @Getter
  protected static class MethodSignatureDescription {

    private final List<MethodSignatureParameterDescription> parameters = new ArrayList<>();
    private String name = "";
    private String description = "";

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

  }

  @Getter
  protected static class ConstructorDescription {
    private final List<MethodSignatureParameterDescription> parameters = new ArrayList<>();
    private String name = "";
    private String description = "";

    private ConstructorDescription() {
    }
  }
}
