package com.github._1c_syntax.bsl.context.platform.hbk;

import com.github.eightm.lib.DoubleLanguageString;
import com.github.eightm.lib.Page;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;


class HtmlParserTest {

    @Test
    void parseConstructorPage_Array() throws URISyntaxException {
        var constructor = parseConstructorPage("ctor13");
        assertThat(constructor)
                .isNotNull()
                .hasFieldOrPropertyWithValue("name", "По количеству элементов")
                .hasFieldOrPropertyWithValue("description", "Создает массив из указанного количества элементов. Если задано несколько параметров, то будет создан массив, элементами которого являются массивы (и т.д. в зависимости от количества параметров). Фактически конструктор позволяет создать массивы массивов, которые могут являться аналогом многомерного массива.");
        assertThat(constructor.getParameters())
                .hasSize(1);
        assertThat(constructor.getParameters().get(0))
                .hasFieldOrPropertyWithValue("name", "КоличествоЭлементов1>,...,<КоличествоЭлементовN")
                .hasFieldOrPropertyWithValue("description", "Каждый параметр определяет количество элементов массива в соответствующем измерении. Может задаваться неограниченное количество параметров. Если ни один параметр не указан, то создается одномерный массив с нулевым количеством элементов.")
                .hasFieldOrPropertyWithValue("isRequired", false)
                .hasFieldOrPropertyWithValue("types", List.of("Число"));
    }

    @Test
    void parseConstructorPage_ClientApplicationInterfaceContentSettingsItem() throws URISyntaxException {
        var constructor = parseConstructorPage("ctor225");
        assertThat(constructor)
                .isNotNull()
                .hasFieldOrPropertyWithValue("name", "На основании имени панели")
                .hasFieldOrPropertyWithValue("description", "Создает элемент настройки на основании имени панели.");
        assertThat(constructor.getParameters())
                .hasSize(1);
        assertThat(constructor.getParameters().get(0))
                .hasFieldOrPropertyWithValue("name", "Имя")
                .hasFieldOrPropertyWithValue("description", "Имя панели.")
                .hasFieldOrPropertyWithValue("isRequired", true)
                .hasFieldOrPropertyWithValue("types", List.of("Строка"));
    }

    HtmlParser.ConstructorDescription parseConstructorPage(String fileName) throws URISyntaxException {
        var parser = new HtmlParser(Path.of(Objects.requireNonNull(this.getClass().getClassLoader().getResource("fixtures")).toURI()));
        var page = new Page(new DoubleLanguageString("", ""), "/constructors/%s.html".formatted(fileName), Collections.emptyList());

        return parser.parseConstructorPage(page);

    }
}