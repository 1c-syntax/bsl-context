package com.github._1c_syntax.bsl.context.platform.hbk;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

/**
 * Источник HTML-страниц синтакс-помощника. Введён для того, чтобы парсер
 * мог работать как с распакованным на диск каталогом, так и с
 * in-memory-картой {@code путь → байты} — последнее принципиально быстрее
 * на Windows (97% времени уходило в FS-операции).
 */
public interface PageSource {

    /**
     * Открывает поток на чтение страницы по относительному пути из
     * {@link com.github.eightm.lib.Page#htmlPath()} (со слэшем в начале).
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * Удобная обёртка: разбирает HTML-страницу через jsoup.
     */
    default Document parse(String relativePath) throws IOException {
        try (var in = open(relativePath)) {
            return Jsoup.parse(in, StandardCharsets.UTF_8.name(), "");
        }
    }

    /**
     * Нормализует путь страницы СП к единому виду:
     * <ul>
     *   <li>{@code \} (Windows-style, как часто хранится в TOC PackBlock
     *       и в некоторых HBK ZIP-entries) → {@code /};</li>
     *   <li>ведущий слэш срезается (ключи ZIP — без него,
     *       {@code Page.htmlPath()} — со слэшем);</li>
     *   <li>повторные слэши (например, {@code \/methods/...} после смеси
     *       parent-с-{@code \} + child-с-{@code /}) схлопываются.</li>
     * </ul>
     * Используется и при заливке в map (см. {@code readFileStorageIntoMemory}),
     * и при lookup'е в {@link InMemory#open(String)} — чтобы lookup был
     * устойчив к различиям в разметке путей внутри HBK.
     */
    static String normalize(String path) {
        if (path == null || path.isEmpty()) return path;
        var slashed = path.replace('\\', '/');
        // Схлопываем "//" в "/".
        while (slashed.contains("//")) {
            slashed = slashed.replace("//", "/");
        }
        return slashed.charAt(0) == '/' ? slashed.substring(1) : slashed;
    }

    /**
     * In-memory источник: путь → байты. Используется по умолчанию, чтобы
     * избежать распаковки 20+k файлов на диск.
     */
    final class InMemory implements PageSource {
        private final Map<String, byte[]> data;

        public InMemory(Map<String, byte[]> data) {
            this.data = Objects.requireNonNull(data, "data");
        }

        @Override
        public InputStream open(String relativePath) throws IOException {
            var bytes = data.get(normalize(relativePath));
            if (bytes == null) {
                throw new IOException("page not found in memory: " + relativePath);
            }
            return new java.io.ByteArrayInputStream(bytes);
        }
    }

    /**
     * Источник, читающий страницы из каталога файловой системы.
     * Полезен для тестов на распакованных фикстурах.
     */
    final class FileSystem implements PageSource {
        private final Path root;

        public FileSystem(Path root) {
            this.root = Objects.requireNonNull(root, "root");
        }

        @Override
        public InputStream open(String relativePath) throws IOException {
            return Files.newInputStream(root.resolve(Path.of("." + relativePath)));
        }
    }
}
