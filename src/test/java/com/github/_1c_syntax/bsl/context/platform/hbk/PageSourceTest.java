package com.github._1c_syntax.bsl.context.platform.hbk;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageSourceTest {

    @Test
    void normalizeConvertsBackslashesAndStripsLeadingSlash() {
        assertThat(PageSource.normalize("/objects/catalog/Type.html"))
            .isEqualTo("objects/catalog/Type.html");
        assertThat(PageSource.normalize("\\objects\\catalog\\Type.html"))
            .isEqualTo("objects/catalog/Type.html");
        assertThat(PageSource.normalize("objects/catalog/Type.html"))
            .isEqualTo("objects/catalog/Type.html");
    }

    @Test
    void normalizeCollapsesDoubleSlashesFromMixedSeparators() {
        // Реальный кейс из лога: parent заканчивается на '\', child начинается с '/'.
        assertThat(PageSource.normalize(
            "/objects\\catalog213\\catalog2171\\QuerySchemaQueryBatch\\/methods/Add4584.html"))
            .isEqualTo("objects/catalog213/catalog2171/QuerySchemaQueryBatch/methods/Add4584.html");
    }

    @Test
    void normalizeHandlesEmptyAndNull() {
        assertThat(PageSource.normalize(null)).isNull();
        assertThat(PageSource.normalize("")).isEmpty();
    }

    @Test
    void inMemoryResolvesPageRegardlessOfSeparators() throws IOException {
        var key = "objects/catalog/methods/Add.html";
        var bytes = "<html>ok</html>".getBytes(StandardCharsets.UTF_8);
        var source = new PageSource.InMemory(Map.of(key, bytes));

        // Lookup'ы в любом из встречающихся в HBK видов должны срабатывать.
        assertThat(read(source, "/objects/catalog/methods/Add.html")).isEqualTo("<html>ok</html>");
        assertThat(read(source, "\\objects\\catalog\\methods\\Add.html")).isEqualTo("<html>ok</html>");
        assertThat(read(source, "/objects\\catalog\\/methods/Add.html")).isEqualTo("<html>ok</html>");
        assertThat(read(source, "objects/catalog/methods/Add.html")).isEqualTo("<html>ok</html>");
    }

    @Test
    void inMemoryResolvesEntryStoredWithBackslashes() throws IOException {
        // HBK, где ZIP-entries хранятся в windows-style. После нормализации
        // ключей при заливке lookup по '/' должен сработать.
        var key = PageSource.normalize("objects\\catalog\\methods\\Add.html");
        var bytes = "x".getBytes(StandardCharsets.UTF_8);
        var source = new PageSource.InMemory(Map.of(key, bytes));

        assertThat(read(source, "/objects/catalog/methods/Add.html")).isEqualTo("x");
    }

    @Test
    void inMemoryThrowsWhenPageMissing() {
        var source = new PageSource.InMemory(Map.of());
        assertThatThrownBy(() -> source.open("/missing.html"))
            .isInstanceOf(IOException.class)
            .hasMessageContaining("page not found in memory");
    }

    private static String read(PageSource source, String path) throws IOException {
        try (var in = source.open(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
